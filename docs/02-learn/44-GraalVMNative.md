# 44 — GraalVM & Native Image

**Previous:** [43 Foreign Function & Memory API](43-ForeignMemoryAPI.md) · **Next:** [45 Reactive Java](45-ReactiveJava.md)

> **Note:** GraalVM Native Image requires GraalVM JDK installed — no standalone demo file. Follow the shell commands below with any Java project.

---

## What is GraalVM?

GraalVM is a high-performance JDK distribution that adds:
1. **Native Image** — AOT compilation to a standalone native binary
2. **Polyglot** — run JavaScript, Python, Ruby, R, WebAssembly inside the JVM
3. **GraalVM JIT** — an improved JIT compiler replacing C2 (in Enterprise edition)

---

## Native Image — Why It Matters

| | Traditional JVM | GraalVM Native Image |
|-|-----------------|----------------------|
| Startup time | 100ms – 5s | 10ms – 50ms |
| Memory at start | 200–500 MB | 20–80 MB |
| Peak throughput | Excellent (JIT) | Good (no JIT warmup) |
| Binary | JAR + JRE | Single self-contained executable |
| Use case | Long-running services | CLI tools, serverless, containers |

---

## Getting Started

### 1. Install GraalVM
```bash
# Using SDKMAN
sdk install java 25-graalce
sdk use java 25-graalce

# Verify
java -version     # GraalVM CE 25
native-image --version
```

### 2. Build Native Binary
```bash
# Compile Java source
javac Hello.java

# Create native executable
native-image Hello

# Run — no JVM needed
./hello            # starts in milliseconds
```

### 3. With Maven
```xml
<!-- pom.xml — GraalVM Native Maven plugin -->
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <version>0.10.2</version>
    <executions>
        <execution>
            <goals><goal>native-compile</goal></goals>
        </execution>
    </executions>
</plugin>
```
```bash
mvn -Pnative native:compile
./target/my-app
```

---

## Reflection & Dynamic Features

Native Image performs **closed-world analysis** — it traces what code is reachable at build time. Dynamic features need explicit configuration:

### Reflection Config (`reflect-config.json`)
```json
[
  {
    "name": "com.example.MyService",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  }
]
```

### Resource Config (`resource-config.json`)
```json
{
  "resources": {
    "includes": [{"pattern": ".*\\.properties"}]
  }
}
```

### Automatic Config Generation (Tracing Agent)
```bash
# Run app with tracing agent to auto-generate config files
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
     -jar myapp.jar

# Then build native image — config is picked up automatically
native-image -jar myapp.jar
```

---

## Spring Boot + Native Image

Spring Boot 3+ has first-class Native Image support:
```bash
# Spring Boot 3 with GraalVM
./mvnw -Pnative native:compile    # Maven
./gradlew nativeCompile           # Gradle

# Or build Docker image directly
./mvnw spring-boot:build-image -Pnative
```

Spring AOT (Ahead-Of-Time processing) generates:
- Proxy classes at build time
- Reflection config for Spring beans
- Resource hints for templates/configs

---

## Limitations of Native Image

| Limitation | Workaround |
|------------|------------|
| Reflection requires config | Tracing agent; `@RegisterForReflection` |
| Dynamic class loading limited | Static factory patterns |
| No runtime bytecode generation (ASM/Javassist) | Provide compiled alternatives |
| Build time is slow (minutes) | Incremental builds; CI caching |
| Debugging is harder | Use JVM mode for dev, native for prod |
| Serialization needs config | `serialization-config.json` |

---

## GraalVM Polyglot

```java
import org.graalvm.polyglot.*;

try (Context ctx = Context.create()) {
    // Execute JavaScript
    Value result = ctx.eval("js", "1 + 1");
    System.out.println(result.asInt());   // 2

    // Call Python
    ctx.eval("python", "print('Hello from Python')");
}
```

---

## Performance Profile: When to Use What

```
CLI tool / lambda function   → Native Image (fast start, small memory)
Microservice (high RPS)      → JVM + JIT (best sustained throughput)
Batch processing             → JVM + JIT (warmup amortized)
Serverless (cold starts)     → Native Image (< 50ms cold start)
Long-running server          → JVM (best GC, JIT optimization)
```

---

## Useful Flags

```bash
# Build
native-image --no-fallback                   # fail if fallback JVM is needed
native-image -O2                             # optimize level
native-image --gc=G1                         # choose GC (Enterprise)
native-image --initialize-at-run-time=pkg    # defer class init

# Run
./myapp -Xmx128m                             # still supports JVM flags
./myapp -XX:+PrintGC                         # GC logging
```

---

## Interview Questions

1. What is AOT compilation? → Compile Java bytecode to native machine code at build time, not at runtime like JIT.
2. Why does Native Image need reflection config? → Closed-world analysis can't see dynamically loaded classes; config tells it what to include.
3. Trade-off: Native Image vs JVM JIT? → Native Image: fast start, low memory, no peak JIT optimization; JVM: slow start, high memory, best sustained throughput.
4. What does Spring AOT do? → Generates proxies, reflection hints, and resource configs at build time so Spring context can bootstrap natively.
5. Can you use GraalVM with existing JARs? → Yes, but jars using dynamic class loading, reflection, or JNI without config may fail to build natively.

---

**Next →** [45 Reactive Java](45-ReactiveJava.md)
