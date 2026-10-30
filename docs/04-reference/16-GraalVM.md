# GraalVM Reference

**Back to Index:** [04-reference/00-INDEX.md](00-INDEX.md)

---

## Installation

```bash
# SDKMAN (recommended)
sdk install java 25-graalce     # GraalVM CE 25
sdk use java 25-graalce

# Verify
java -version                   # GraalVM CE 25...
native-image --version          # GraalVM Native Image 25...

# Or download from https://github.com/graalvm/graalvm-ce-builds/releases
```

---

## Native Image Commands

```bash
# Basic compilation
native-image Hello                          # produces ./hello
native-image -jar myapp.jar                # from JAR
native-image -jar myapp.jar myapp          # custom output name

# Options
--no-fallback                              # fail if can't be fully native
-O0 / -O1 / -O2 / -O3                    # optimization level (O2 default)
-g                                         # debug symbols (DWARF)
--verbose                                  # detailed build output
--report-unsupported-elements-at-runtime  # don't fail — warn at runtime

# Memory during build (native-image is memory-hungry)
-J-Xmx8g                                  # give build 8 GB heap

# GC selection (Community Edition)
--gc=serial                               # default, smallest binary
--gc=epsilon                              # no-op GC (testing only)

# Initialize at build time vs run time
--initialize-at-build-time=com.example.Config   # init class at build time
--initialize-at-run-time=com.third.party.Lib    # defer to run time

# Profiling-guided optimization
--pgo-instrument                          # first run: instrument
--pgo                                     # second run: use profile data
```

---

## Configuration Files

Place in `src/main/resources/META-INF/native-image/` — picked up automatically.

### `reflect-config.json`
```json
[
  {
    "name": "com.example.MyEntity",
    "allDeclaredFields": true,
    "allDeclaredConstructors": true,
    "allPublicMethods": true,
    "allDeclaredMethods": false
  },
  {
    "name": "com.example.dto.UserDto",
    "fields": [{"name": "id"}, {"name": "name"}],
    "methods": [{"name": "<init>", "parameterTypes": []}]
  }
]
```

### `resource-config.json`
```json
{
  "resources": {
    "includes": [
      {"pattern": ".*\\.properties"},
      {"pattern": "templates/.*\\.html"},
      {"pattern": "application\\.yml"}
    ],
    "excludes": []
  },
  "bundles": [
    {"name": "messages"}
  ]
}
```

### `proxy-config.json`
```json
[
  ["com.example.MyInterface"],
  ["java.lang.Runnable", "com.example.Marker"]
]
```

### `serialization-config.json`
```json
{
  "types": [
    {"name": "com.example.MySerializable"}
  ]
}
```

### `native-image.properties`
```properties
Args = --initialize-at-build-time=org.slf4j \
       -H:+ReportExceptionStackTraces
```

---

## Tracing Agent

Run the application under the agent to auto-generate config:

```bash
# Run with agent attached
java -agentlib:native-image-agent=config-output-dir=META-INF/native-image \
     -jar myapp.jar

# Or merge with existing config
java -agentlib:native-image-agent=config-merge-dir=META-INF/native-image \
     -jar myapp.jar

# Conditional config (only include types matching filter)
java -agentlib:native-image-agent=config-output-dir=out, \
       caller-filter-file=filter.json \
     -jar myapp.jar
```

**Best practice:** Run the agent through ALL code paths (happy path, error paths, all REST endpoints) then merge config files.

---

## Maven & Gradle Integration

### Maven
```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <version>0.10.2</version>
    <configuration>
        <imageName>${project.artifactId}</imageName>
        <mainClass>com.example.Main</mainClass>
        <buildArgs>
            <buildArg>--no-fallback</buildArg>
            <buildArg>-O2</buildArg>
        </buildArgs>
    </configuration>
    <executions>
        <execution>
            <id>native-compile</id>
            <goals><goal>compile-no-fork</goal></goals>
            <phase>package</phase>
        </execution>
    </executions>
</plugin>
```

```bash
mvn -Pnative native:compile
./target/myapp
```

### Gradle
```kotlin
plugins {
    id("org.graalvm.buildtools.native") version "0.10.2"
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("myapp")
            mainClass.set("com.example.Main")
            buildArgs.add("--no-fallback")
        }
    }
}
```

```bash
./gradlew nativeCompile
./build/native/nativeCompile/myapp
```

---

## Spring Boot Native

```bash
# Spring Boot 3 + GraalVM
./mvnw -Pnative native:compile              # produces target/<artifact>
./mvnw spring-boot:build-image -Pnative     # produces OCI image

# Gradle
./gradlew nativeCompile
./gradlew bootBuildImage
```

Spring Boot 3 AOT processing runs during `package` phase:
1. `spring-boot:process-aot` — generates `target/spring-aot/`
2. Files: generated sources, `reflect-hints.json`, `resource-hints.json`, proxy registrations

**Custom hints:**
```java
@Configuration
@ImportRuntimeHints(MyRuntimeHints.class)
public class AppConfig { ... }

class MyRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader cl) {
        hints.reflection().registerType(MyEntity.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_FIELDS);
        hints.resources().registerPattern("templates/*.html");
    }
}
```

---

## GraalVM Polyglot Reference

```java
// Context creation
Context ctx = Context.newBuilder()
    .allowAllAccess(true)                         // all privileges
    .allowExperimentalOptions(true)
    .build();

// Evaluate
Value result = ctx.eval("js", "1 + 2");           // 3
Value list   = ctx.eval("js", "[1,2,3]");          // array

// Access result
result.asInt()                                      // 3
list.getArraySize()                                 // 3
list.getArrayElement(0).asInt()                     // 1

// Share data
ctx.getBindings("js").putMember("data", Map.of("a", 1));
ctx.eval("js", "data.a");                           // 1

// Languages: "js", "python", "ruby", "R", "wasm", "llvm"
```

---

## Supported GCs in Native Image

| GC | Edition | Notes |
|----|---------|-------|
| Serial | Community | Default; smallest binary; single-threaded collect |
| G1 | Enterprise | Lower latency; region-based |
| Epsilon | Community | No-op; zero overhead; OOM on first GC |

```bash
native-image --gc=serial myapp      # default
native-image --gc=epsilon myapp     # benchmarking only
```

---

## Limitations Quick Reference

| Feature | Support in Native Image |
|---------|------------------------|
| Reflection | Config required |
| Dynamic class loading | Config or not supported |
| Dynamic proxies | `proxy-config.json` required |
| JNI | `jni-config.json` required |
| JVMTI agents | Not supported |
| JFR / JMX | Very limited |
| Finalizers | Removed; use Cleaner |
| Runtime bytecode gen (ASM) | Not supported |
| InvokeDynamic (static) | Supported |
| Lambda / MethodRef | Supported |
| Serialization | `serialization-config.json` required |
| Resources (classpath) | `resource-config.json` required |

---

**Learn more:** [`44-GraalVMNative.md`](../02-learn/44-GraalVMNative.md) · [`21-GraalVMAndNative.md`](../03-interview/21-GraalVMAndNative.md)
