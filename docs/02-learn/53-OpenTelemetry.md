# 53 — OpenTelemetry in Java

**Previous:** [52 Kafka Client](52-KafkaClient.md) · **Next →** [Back to Index](00-INDEX.md)

---

## What is OpenTelemetry?

OpenTelemetry (OTel) is the vendor-neutral observability standard for:
- **Traces** — distributed request flows across services
- **Metrics** — counters, gauges, histograms
- **Logs** — structured log events with trace correlation

Single SDK, one agent, works with Jaeger, Zipkin, Prometheus, Datadog, Grafana, etc.

---

## Observability Pillars

```
Traces  → Where did time go? Which service is slow?
Metrics → How often? How many? How long on average?
Logs    → What happened? What was the state?
```

---

## 1. Auto-Instrumentation (Zero Code Change)

The OTel Java agent instruments your application via byte-code injection:

```bash
# Download the agent
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Run with agent — instruments Spring, JDBC, HTTP, Kafka, etc. automatically
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=user-service \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -Dotel.traces.exporter=otlp \
     -Dotel.metrics.exporter=otlp \
     -Dotel.logs.exporter=otlp \
     -jar app.jar
```

Auto-instrumented libraries: Spring Boot, Spring MVC, Spring WebFlux, JDBC, Hibernate, Kafka, RabbitMQ, Redis (Lettuce/Jedis), gRPC, HttpClient, OkHttp, and 100+ more.

---

## 2. Manual Instrumentation (SDK)

### Maven Dependencies

```xml
<!-- Core SDK -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.36.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>1.36.0</version>
</dependency>
<!-- OTLP exporter -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>1.36.0</version>
</dependency>
```

### SDK Setup

```java
import io.opentelemetry.api.*;
import io.opentelemetry.sdk.*;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.exporter.otlp.trace.*;

// Configure
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(
        OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://otel-collector:4317")
            .build()
    ).build())
    .setResource(Resource.create(Attributes.of(
        ResourceAttributes.SERVICE_NAME, "user-service",
        ResourceAttributes.SERVICE_VERSION, "1.0.0"
    )))
    .build();

OpenTelemetry otel = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .buildAndRegisterGlobal();
```

### Tracing

```java
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;

Tracer tracer = otel.getTracer("com.example.UserService");

public User findUser(Long id) {
    Span span = tracer.spanBuilder("findUser")
        .setAttribute("user.id", id)
        .startSpan();

    try (Scope scope = span.makeCurrent()) {   // propagates span context
        User user = repository.findById(id).orElseThrow();
        span.setAttribute("user.name", user.getName());
        return user;
    } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
        throw e;
    } finally {
        span.end();
    }
}

// Nested spans automatically form a trace
public Order createOrder(Order order) {
    Span span = tracer.spanBuilder("createOrder").startSpan();
    try (Scope s = span.makeCurrent()) {
        User user = findUser(order.getUserId());   // child span inside
        return orderRepository.save(order);
    } finally {
        span.end();
    }
}
```

---

## 3. Spring Boot Integration

```xml
<!-- Spring Boot 3 + Micrometer OTel bridge -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0    # 100% in dev, reduce in prod (e.g. 0.1)
  otlp:
    tracing:
      export:
        url: http://otel-collector:4318/v1/traces
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics

spring:
  application:
    name: user-service    # becomes otel.service.name
```

```java
// Traces propagated automatically for Spring MVC, WebClient, RestTemplate, JDBC
// Add custom spans with @Observed or Observation API

import io.micrometer.observation.annotation.Observed;

@Service
public class UserService {

    @Observed(name = "user.find", contextualName = "findUserById",
              lowCardinalityKeyValues = {"layer", "service"})
    public User findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
```

---

## 4. Metrics

```java
import io.opentelemetry.api.metrics.*;

Meter meter = otel.getMeter("com.example.UserService");

// Counter — monotonically increasing
LongCounter requestCounter = meter
    .counterBuilder("user.requests")
    .setDescription("Number of user lookup requests")
    .setUnit("requests")
    .build();

// Histogram — distribution (latency, size)
DoubleHistogram latencyHistogram = meter
    .histogramBuilder("user.request.duration")
    .setDescription("Duration of user lookup requests")
    .setUnit("ms")
    .build();

// Gauge — current value (queue size, connection count)
meter.gaugeBuilder("user.cache.size")
    .setDescription("Number of users in cache")
    .buildWithCallback(obs -> obs.record(cache.size()));

// Use in code
public User findById(Long id) {
    long start = System.currentTimeMillis();
    requestCounter.add(1, Attributes.of(AttributeKey.stringKey("endpoint"), "/users"));
    try {
        return repo.findById(id).orElseThrow();
    } finally {
        latencyHistogram.record(System.currentTimeMillis() - start);
    }
}
```

---

## 5. Structured Logging with Trace Correlation

```java
// With Logback + OTel trace correlation
// Add traceId/spanId automatically to all log lines
// In logback.xml:
<pattern>%d{ISO8601} [%thread] %-5level %logger{36} [traceId=%X{traceId} spanId=%X{spanId}] - %msg%n</pattern>
```

```yaml
# Spring Boot Micrometer tracing auto-injects traceId/spanId into MDC
# Output: 2025-09-15 [http-nio-8080-exec-1] INFO  UserService [traceId=abc123 spanId=def456] - Found user alice
```

---

## 6. OTel Collector

The OTel Collector is a proxy that receives telemetry, processes it, and exports to backends:

```yaml
# docker-compose.yml
otel-collector:
  image: otel/opentelemetry-collector-contrib:latest
  ports:
    - "4317:4317"   # OTLP gRPC
    - "4318:4318"   # OTLP HTTP
  volumes:
    - ./otel-config.yaml:/etc/otelcol/config.yaml
```

```yaml
# otel-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 1s
    send_batch_size: 1024

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true
  prometheus:
    endpoint: "0.0.0.0:8889"

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [jaeger]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
```

---

## 7. Context Propagation

Trace context propagates across services via HTTP headers:

```java
// With Spring WebClient — automatic propagation
@Bean
public WebClient webClient(WebClient.Builder builder) {
    return builder
        .filter(new TracingExchangeFilterFunction(tracer))  // auto with Micrometer
        .build();
}

// Manual propagation via W3C Trace Context headers
// HTTP request includes:
// traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
// tracestate: rojo=00f067aa0ba902b7
```

---

## Interview Questions

1. What are the three pillars of observability? → Traces (request flow), Metrics (aggregated numbers), Logs (events). OTel provides a unified API for all three.
2. What is the OTel Collector? → A vendor-agnostic proxy that receives telemetry from applications, applies processing (batching, filtering, enrichment), and exports to multiple backends.
3. Auto-instrumentation vs manual instrumentation? → Auto (Java agent) instruments libraries with zero code change. Manual (SDK) lets you create custom spans, record business metrics, and add semantic attributes.
4. What is W3C Trace Context? → HTTP header standard (`traceparent`, `tracestate`) for propagating trace IDs across services, enabling distributed traces to stitch together across service boundaries.
5. What is sampling? → Recording only a fraction of traces (e.g., 10%) to reduce storage/overhead. Head-based (decided at first span); tail-based (decided after full trace seen — better for capturing errors).

---

**Previous →** [52 Kafka Client](52-KafkaClient.md) · **Back to Index →** [00-INDEX](00-INDEX.md)
