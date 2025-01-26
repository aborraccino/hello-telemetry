from flask import Flask, request, jsonify

# OpenTelemetry SDK
from opentelemetry.sdk.metrics import MeterProvider, Meter
from opentelemetry import metrics
from opentelemetry.sdk.resources import Resource
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.semconv.resource import ResourceAttributes

# Create a Resource with the service.name attribute
resource = Resource.create({ResourceAttributes.SERVICE_NAME: "python-service"})


# Initialize OpenTelemetry SDK

# Metrics
exporter = OTLPMetricExporter(endpoint="http://ht-otel-collector:4317", insecure=True)
reader = PeriodicExportingMetricReader(exporter,export_interval_millis=10000)
meterProvider = MeterProvider(resource=resource,metric_readers=[reader])
metrics.set_meter_provider(meterProvider)
meter = metrics.get_meter(__name__)

compute_request_count = meter.create_counter(name='app_compute_request_count', description="Counts the requests to compute-service",unit='1')

app = Flask(__name__)

@app.route('/compute_average_age', methods=['POST'])
def compute_average_age():  
    
    # Increment compute counter
    compute_request_count.add(1)

    # Process the request data
    data = request.json['data']
    if not data:
        return jsonify({'error': 'No data provided'}), 400
    
    # Extract ages from the data
    ages = [item['age'] for item in data if 'age' in item]
    if not ages:
        return jsonify({'error': 'No age data available'}), 400
    
    # Compute the average age
    average_age = round(sum(ages) / len(ages), 1)

    return jsonify({'average_age': average_age})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)