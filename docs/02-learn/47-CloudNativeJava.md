# 47 — Cloud-Native Java

**Previous:** [46 Security in Java](46-SecurityJava.md) · **Next:** [48 Effective Java](48-EffectiveJava.md)

> **Note:** Cloud-Native topics require Docker, Kubernetes, or a cloud provider. Use the shell commands and YAML snippets below with any existing Spring Boot project.

---

## What is Cloud-Native Java?

Cloud-native Java means running Java applications efficiently in containerized, orchestrated environments — Docker, Kubernetes, serverless — with:
- Fast startup (container replaces fast)
- Low memory footprint (many pods, cost-sensitive)
- Health checks and observability baked in
- 12-factor app principles

---

## 1. Docker — Multi-Stage Builds for Java

### Naive (bad) — ships the full JDK
```dockerfile
FROM eclipse-temurin:21-jdk
COPY target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
# Image size: ~500 MB
```

### Multi-stage + JRE (good)
```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
# Image size: ~200 MB
```

### Multi-stage + jlink custom runtime (best for JVM)
```dockerfile
FROM eclipse-temurin:21-jdk AS jlink
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.net.http,java.sql \
    --strip-debug --no-man-pages --no-header-files \
    --compress=2 --output /custom-jre

FROM debian:bookworm-slim
COPY --from=jlink /custom-jre /opt/java
COPY --from=build /app/target/app.jar /app/app.jar
ENV PATH="/opt/java/bin:$PATH"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# Image size: ~100 MB
```

### GraalVM Native Image (smallest + fastest)
```dockerfile
FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -Pnative native:compile

FROM debian:bookworm-slim
COPY --from=build /app/target/app ./app
EXPOSE 8080
ENTRYPOINT ["./app"]
# Image size: ~50 MB, startup < 50ms
```

---

## 2. JVM Flags for Containers

```bash
# Container-aware heap (Java 8u191+, 11+)
java -XX:+UseContainerSupport \
     -XX:MaxRAMPercentage=75.0 \   # use 75% of container memory limit
     -jar app.jar

# For 512 MB container:
# -Xms = auto (~128MB), -Xmx = auto (~384MB)

# Explicit sizing (when you know the container limit)
java -Xms128m -Xmx384m -jar app.jar

# GC tuning for containers
java -XX:+UseZGC \                 # low-latency GC
     -XX:+ZGenerational \          # generational ZGC (Java 21+)
     -XX:MaxRAMPercentage=75 \
     -jar app.jar

# Startup optimization
java -XX:TieredStopAtLevel=1 \    # only tier 1 JIT — faster start, lower peak
     -Xshare:on \                  # use shared class data archive
     -jar app.jar
```

**Class Data Sharing (CDS) for faster startup:**
```bash
# Create archive once
java -Xshare:dump -jar app.jar

# Use archive on each run
java -Xshare:on -jar app.jar
# Startup up to 30% faster
```

---

## 3. Kubernetes Health Checks

```java
// Spring Boot Actuator (auto-configured with spring-boot-starter-actuator)
// application.yml:
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

```yaml
# kubernetes deployment.yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

**Custom health indicator:**
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    @Autowired DataSource ds;

    @Override
    public Health health() {
        try (Connection c = ds.getConnection()) {
            c.isValid(1);
            return Health.up().withDetail("db", "reachable").build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
```

---

## 4. Kubernetes Resource Configuration

```yaml
# deployment.yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "1000m"
```

**Rules of thumb:**
- Set `-XX:MaxRAMPercentage=75` so JVM heap ≤ 75% of limit
- Request ≥ expected idle consumption; limit = 2× request
- For CPU: JVM uses aggressive CPU at startup (JIT) — allow burst

---

## 5. Graceful Shutdown

```yaml
# application.yml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```yaml
# Kubernetes — give pod time to drain
spec:
  terminationGracePeriodSeconds: 60

lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 10"]  # wait for LB to deregister
```

---

## 6. Observability (Micrometer + OpenTelemetry)

```xml
<!-- Spring Boot 3 auto-configures Micrometer -->
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
management:
  tracing:
    sampling:
      probability: 1.0   # 100% in dev, 10% in prod
  otlp:
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics
    tracing:
      export:
        url: http://otel-collector:4318/v1/traces
```

---

## 7. 12-Factor Principles for Java

| Factor | Java implementation |
|--------|---------------------|
| Config from env | `@Value("${MY_VAR}")`, Spring Cloud Config |
| Stateless processes | No local file state; use Redis/DB |
| Logs as streams | SLF4J to stdout; no file appenders in production |
| Dependencies declared | Maven/Gradle; no classpath assumptions |
| Dev/prod parity | Docker compose = same image as production |
| Admin processes | Spring Boot shell commands / Kubernetes Jobs |

---

## Interview Questions

1. Why `MaxRAMPercentage` over `-Xmx` in containers? → Percentage scales automatically as you resize the container without changing JVM flags.
2. What is `UseContainerSupport`? → JVM detects cgroup-based memory/CPU limits (Docker/K8s) instead of host values. Default-on since Java 8u191.
3. Liveness vs Readiness probe? → Liveness: is the app alive? (restart if fails). Readiness: is it ready to receive traffic? (remove from LB if fails, don't restart).
4. Why `preStop: sleep` in Kubernetes? → Load balancer takes a few seconds to stop routing traffic after pod gets SIGTERM; sleeping prevents 502 errors during rolling deploy.
5. What is GraalVM Native advantage in Kubernetes? → Cold start < 50ms, memory 10× lower → cost saving in auto-scaled services, ideal for FaaS/serverless.

---

**Next →** [48 Effective Java](48-EffectiveJava.md)
