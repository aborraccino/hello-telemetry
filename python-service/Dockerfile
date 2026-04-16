# Use the official Python image from the Docker Hub
FROM docker.io/library/python:3.13-slim

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy the requirements file into the container
COPY requirements.txt ./

# Install the required packages
RUN pip install --no-cache-dir -r requirements.txt

# Run opentelemetry-bootsrap
RUN opentelemetry-bootstrap -a install

# Copy the rest of the application code into the container
COPY . .

# Openetelemetry envs variables
ENV OTEL_PYTHON_LOG_CODE_ATTRIBUTES=true
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://ht-otel-collector:4317

# Expose the port that the application will run on
EXPOSE 5000

# Define the command to run the application
CMD [ "opentelemetry-instrument", "--service_name", "pyton-service", "python", "compute.py"]
#CMD [ "python", "compute.py"]