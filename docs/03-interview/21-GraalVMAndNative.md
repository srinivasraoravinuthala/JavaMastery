# GraalVM & Native Image — Interview Questions

**Back to:** [Interview Index](00-INDEX.md)

---

### 1. What is GraalVM?

**Short:** A JDK distribution that adds AOT compilation to native binaries, polyglot execution, and an improved JIT compiler.

**Detailed:** GraalVM CE (Community Edition) is open source under the GraalVM project. It provides:
- **Native Image** — AOT compile Java to standalone native executable
- **Polyglot API** — run JS, Python, Ruby, Wasm inside the JVM
- **Graal JIT** — a JIT compiler written in Java (replacing C1/C2 in some configs)

---

### 2. What is AOT (Ahead-Of-Time) compilation?

**Short:** Compile Java bytecode to native machine code at build time, eliminating JIT warmup.

**Detailed:**
- JIT compiles methods at runtime as they are called (warm-up period of seconds to minutes)
- AOT compiles everything statically at build time — the resulting binary has no JVM, no JIT
- Trade-off: AOT loses adaptive optimization (JIT can optimize based on actual runtime behavior); gains instant startup and predictable memory

```
Traditional: .java → javac → .class (bytecode) → JVM JIT → native code (at runtime)
GraalVM AOT: .java → javac → .class → native-image → binary (runs directly on OS)
```

---

### 3. How does closed-world assumption work?

**Short:** Native Image analyzes all reachable code at build time — code not reachable is excluded.

**Detailed:** The `native-image` tool performs static analysis (points-to analysis) starting from `main()`. Only classes, methods, and fields provably reachable are compiled. Anything loaded dynamically (reflection, `Class.forName`, serialization) breaks this unless configured.

```bash
native-image --no-fallback Hello
# --no-fallback: fail the build if native image can't be complete
# Without it, native-image creates a "fallback" that still needs a JVM
```

---

### 4. How do you configure reflection for Native Image?

**Short:** Provide `reflect-config.json`, use `@RegisterForReflection`, or run with the tracing agent.

**Option 1 — Tracing agent (best for existing code):**
```bash
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
     -jar myapp.jar
# Run the application through all code paths; agent records reflection calls
```

**Option 2 — Manual config:**
```json
[{
  "name": "com.example.MyDto",
  "allDeclaredFields": true,
  "allDeclaredConstructors": true,
  "allPublicMethods": true
}]
```

**Option 3 — Annotation (Micronaut/Quarkus):**
```java
@RegisterForReflection
public class MyDto { ... }
```

**Spring Boot 3** generates reflection hints automatically via AOT processing — no manual config needed for Spring beans.

---

### 5. Trade-offs: Native Image vs JVM?

| Aspect | JVM (JIT) | Native Image (AOT) |
|--------|-----------|--------------------|
| Startup time | 100ms – 5s | 10ms – 100ms |
| Memory at start | 200–500 MB | 20–100 MB |
| Peak throughput | Higher (JIT adapts) | Lower (no runtime optimization) |
| Debugging | Full tools (JFR, JMX) | Limited (DWARF, no JFR) |
| Dynamic features | Full support | Requires config |
| Build time | Seconds | Minutes |
| Docker image size | 200–500 MB (with JRE) | 30–100 MB |
| Best use case | Long-running, high-throughput | CLI, Lambda, K8s sidecar |

---

### 6. How does Spring Boot support Native Image?

**Short:** Spring Boot 3+ includes Spring AOT — generates proxies and reflection hints at build time.

```bash
# Build Spring Boot native binary
./mvnw -Pnative native:compile

# Or build OCI image
./mvnw spring-boot:build-image -Pnative
docker run myapp:latest  # startup < 100ms
```

Spring AOT does:
1. Runs `BeanFactoryInitializationAotProcessor` at build time
2. Generates Java source for Spring bean definitions (no runtime CGLIB proxies)
3. Generates `reflect-config.json`, `resource-config.json`, `proxy-config.json`

---

### 7. What is GraalVM Polyglot?

**Short:** Run JavaScript, Python, R, Ruby, WebAssembly inside the JVM and share objects between languages.

```java
try (Context context = Context.newBuilder()
        .allowAllAccess(true)
        .build()) {

    // Evaluate JavaScript
    Value jsResult = context.eval("js", "[1,2,3].map(x => x*2)");
    System.out.println(jsResult.getArrayElement(0).asInt());  // 2

    // Expose Java object to JavaScript
    context.getBindings("js").putMember("javaList", List.of("a","b","c"));
    context.eval("js", "console.log(javaList.get(0))");  // "a"
}
```

Use cases: scripting engines, rule engines, migrating legacy JS/Python logic into Java services.

---

### 8. What is Graal JIT vs C2 JIT?

**Short:** Graal JIT is the Java-written JIT that replaced C2 in some GraalVM distributions.

**Detailed:**
- C2 is the server JIT in standard JDK — written in C++, hard to modify
- Graal JIT is written in Java — easier to extend, used for speculative optimizations, escape analysis improvements
- In GraalVM CE 21+, standard JVM mode still uses C2 by default; Graal JIT is opt-in
- Used internally for `native-image` compilation pipeline

---

### 9. What can't be supported in Native Image?

- `Class.forName(dynamicName)` without config
- Dynamic proxies (unless registered: `proxy-config.json`)
- Finalization (avoid in modern Java anyway)
- `java.lang.management` JMX introspection (limited)
- `SecurityManager` (removed in Java 17+)
- Bytecode generation at runtime (ASM, Javassist, Byte Buddy) unless static substitutions provided
- Attach API / JVMTI agents
- JShell, JSR 292 `invokedynamic` dynamic redefine

---

### 10. How do you choose: native or JVM for a microservice?

| Service type | Recommendation |
|--------------|----------------|
| REST CRUD API (steady load) | JVM + virtual threads |
| AWS Lambda / serverless | Native Image (cold start) |
| Kubernetes sidecar | Native Image (memory) |
| Batch processor | JVM (throughput) |
| CLI tool | Native Image (no JRE install needed) |
| High-throughput trading system | JVM with ZGC + JIT tuning |
| Event-driven consumer | JVM (or native if many replicas) |

---

## Rapid-Fire

1. `native-image --no-fallback`? → Fail build if not fully native (no JVM fallback).
2. Reflection at runtime in native? → Needs `reflect-config.json` or tracing agent.
3. JFR in native? → Not available; use async-profiler or OS-level profiling.
4. Native image GC options? → Serial (default), G1 (Enterprise), Epsilon (no-op for testing).
5. `@ReflectionHint` in Spring? → Programmatic way to register reflection for AOT.
6. Static initializer in native? → Runs at build time by default; `--initialize-at-run-time` to defer.
7. Can native images be debugged? → Yes with DWARF format; `native-image -g` for symbols.
8. Quarkus vs Spring for native? → Quarkus designed for native from start (faster build, smaller binary); Spring Boot 3 added native later.
