package com.bbsod.demo;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

// OpenTelemetry SDK
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.api.GlobalOpenTelemetry;
// OpenTelemetry API
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

public class MyServlet extends HttpServlet {

    // Define Class Fields
    private static final String INSTRUMENTATION_NAME = MyServlet.class.getName();
    private final Meter meter;
    private final LongCounter requestCounter;
    private final Tracer tracer;

    // Constructor
    public MyServlet() {
        OpenTelemetry openTelemetry = initOpenTelemetry();
        this.meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);
        this.requestCounter = meter.counterBuilder("app.db.db_requests")
                .setDescription("Count DB requests")
                .build();
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    static OpenTelemetry initOpenTelemetry() {

        // Set up the resource with service.name
        Resource resource = Resource.create(Attributes.of(AttributeKey.stringKey("service.name"),
                "tomcat-service"));

        // Metrics
        OtlpGrpcMetricExporter otlpGrpcMetricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint("http://otel-collector:4317")
                .build();

        PeriodicMetricReader periodicMetricReader = PeriodicMetricReader.builder(otlpGrpcMetricExporter)
                .setInterval(java.time.Duration.ofSeconds(20))
                .build();

        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(periodicMetricReader)
                .build();

        // Traces
        OtlpGrpcSpanExporter otlpGrpcSpanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://otel-collector:4317")
                .build();

        SimpleSpanProcessor simpleSpanProcessor = SimpleSpanProcessor.builder(otlpGrpcSpanExporter).build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(simpleSpanProcessor)
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .setTracerProvider(tracerProvider)
                .build();

        // Cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

        return sdk;

    }

    Context parentContext;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        List<JSONObject> dataList = new ArrayList<>();
        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        // Create a new ParentSpan
        Span parentSpan = tracer.spanBuilder("GET").setNoParent().startSpan();
        parentSpan.makeCurrent();

        // Sleep for 2 seconds
        // Span to capture sleep
        parentContext = Context.current().with(parentSpan);
        Span sleepSpan = tracer.spanBuilder("SleepForTwoSeconds")
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentContext)
                .startSpan();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            sleepSpan.end();
        }

        // Establish database connection and get data
        requestCounter.add(1);

        // Start Database Span
        // Context parentContext = Context.current().with(sleepSpan);
        Span dbSpan = tracer.spanBuilder("DatabaseConnection")
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentContext)
                .startSpan();

        // JDBC connection parameters
        String jdbcUrl = "jdbc:mysql://mysql_container:3306/mydatabase";
        String jdbcUser = "myuser";
        String jdbcPassword = "mypassword";

        try {
            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser,
                    jdbcPassword);

            // Create a statement
            Statement statement = connection.createStatement();

            // Execute a query
            String query = "SELECT * FROM mytable";
            ResultSet resultSet = statement.executeQuery(query);

            // Build web page
            out.println("<html><body>");
            out.println("<h1>Database Results</h1>");
            out.println("<table border='1'>");
            out.println("<tr><th>ID</th><th>Name</th><th>Age</th></tr>");

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                int age = resultSet.getInt("age");
                out.println("<tr><td>" + id + "</td><td>" + name + "</td><td>" + age
                        + "</td></tr>");

                JSONObject dataObject = new JSONObject();
                dataObject.put("id", id);
                dataObject.put("name", name);
                dataObject.put("age", age);
                dataList.add(dataObject);

            }
            out.println("</table>");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC Driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Connection failed.");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            out.println("<h2>Error: " + e.getMessage() + "</h2>");
        } finally {
            dbSpan.end();
        }
        parentSpan.end();

        // Make a request to the Python microservice
        String averageAge = getAverageAge(dataList);
        out.println("<h2>Average Age: " + averageAge + "</h2>");
        out.println("</body></html>");

    }

    private String getAverageAge(List<JSONObject> dataList) throws IOException {

        Span computeSpan = tracer.spanBuilder("Compute Request").setSpanKind(SpanKind.INTERNAL).setParent(parentContext)
                .startSpan();
        Context context = Context.current().with(computeSpan);

        try (Scope scope = computeSpan.makeCurrent(); CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("http://python-service:5000/compute_average_age");
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject requestData = new JSONObject();
            requestData.put("data", new JSONArray(dataList));

            StringEntity entity = new StringEntity(requestData.toString());
            httpPost.setEntity(entity);

            // Inject the context into the HTTP request headers
            // TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
            // propagator.inject(context, httpPost, HttpPost::setHeader);

            // Inject the context into the HTTP request headers using W3CTraceContextPropagator 
            W3CTraceContextPropagator propagator = W3CTraceContextPropagator.getInstance(); 
            propagator.inject(context, httpPost, HttpPost::setHeader);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseString = EntityUtils.toString(response.getEntity());
                JSONObject responseJson = new JSONObject(responseString);
                return responseJson.get("average_age").toString();
            }
        } finally {
            computeSpan.end();
        }
    }
}