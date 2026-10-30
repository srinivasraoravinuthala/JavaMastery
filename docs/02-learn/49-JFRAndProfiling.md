# 49 — JFR & Java Profiling

**Previous:** [48 Effective Java](48-EffectiveJava.md) · **Next:** [50 Jakarta EE & Microservices](50-JakartaEEMicroservices.md)

▶️ `pkg6jvm/jvm4FlightRecorderDemo.java`

---

## Why Profiling Matters

Optimization without measurement is guesswork. Profiling answers:
- Where is CPU time actually spent? (not where you think)
- What is allocated, when, and how much survives GC?
- Why is this request slow? (lock contention, GC pause, I/O?)

---

## 1. Java Flight Recorder (JFR)

JFR is a low-overhead profiling and event recording framework built into the JDK (final in Java 14, production-safe since Java 11).

### Enable JFR

```bash
# During startup
java -XX:StartFlightRecording=duration=60s,filename=app.jfr -jar app.jar

# On-demand (requires JVM running)
jcmd <PID> JFR.start duration=60s filename=app.jfr
jcmd <PID> JFR.stop

# Dump current recording
jcmd <PID> JFR.dump filename=dump.jfr
```

### JFR in Code

```java
import jdk.jfr.*;

// Define a custom event
@Label("Database Query")
@Category("Database")
@StackTrace(true)
public class QueryEvent extends Event {
    @Label("SQL") public String sql;
    @Label("Rows") public int rowCount;
    @Label("Duration ms") public long durationMs;
}

// Record it
public List<User> findUsers(String query) {
    var event = new QueryEvent();
    event.sql = query;
    event.begin();
    try {
        var result = jdbcTemplate.query(query, rowMapper);
        event.rowCount = result.size();
        return result;
    } finally {
        event.end();
        event.durationMs = event.getDuration().toMillis();
        event.commit();   // writes to JFR recording if enabled
    }
}
```

### Analyze JFR Recordings

**Java Mission Control (JMC):**
```bash
# Open JMC GUI (download separately from https://jdk.java.net/jmc/)
jmc
# File → Open → select .jfr file
```

**Programmatic analysis:**
```java
import jdk.jfr.consumer.*;

try (var rf = RecordingFile.open(Path.of("app.jfr"))) {
    rf.readAllEvents().stream()
        .filter(e -> e.getEventType().getName().contains("GarbageCollection"))
        .forEach(e -> System.out.printf("GC: %s duration=%sms%n",
            e.getEventType().getName(),
            e.getDuration().toMillis()));
}
```

---

## 2. async-profiler

async-profiler is the best open-source CPU + allocation + lock profiler for JVM.
It uses OS signals to sample the CPU — no safepoint bias.

### Install & Profile

```bash
# Download from https://github.com/async-profiler/async-profiler/releases
./asprof -d 30 -f flamegraph.html <PID>   # 30-second CPU profile → HTML flamegraph

# Allocation profiling
./asprof -d 30 -e alloc -f alloc.html <PID>

# Lock contention profiling
./asprof -d 30 -e lock -f locks.html <PID>

# Wall-clock (includes blocking time — shows what threads are actually doing)
./asprof -d 30 -e wall -f wall.html <PID>
```

### Reading a Flamegraph

```
- X axis: alphabetical order (NOT time)
- Width of a box: % of samples that include this method
- Look for: wide boxes at the top (hotspots)
- Look for: unexpectedly wide JVM internals (GC, classloading, lock contention)
```

---

## 3. JVM Diagnostic Commands (`jcmd`)

```bash
# List all running JVMs
jcmd

# Heap summary
jcmd <PID> GC.heap_info

# Heap histogram (top 20 classes by instance count)
jcmd <PID> GC.class_histogram | head -25

# Thread dump
jcmd <PID> Thread.print

# JVM flags
jcmd <PID> VM.flags

# Force GC
jcmd <PID> GC.run

# Create heap dump
jcmd <PID> GC.heap_dump /tmp/heap.hprof
```

---

## 4. Heap Dump Analysis

```bash
# Generate heap dump
jmap -dump:format=b,file=heap.hprof <PID>

# Or at OOM (auto-generate)
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/oom.hprof \
     -jar app.jar
```

**Analyze with Eclipse Memory Analyzer (MAT):**
1. Download MAT from eclipse.org
2. Open heap.hprof
3. Run "Leak Suspects" report
4. Look for: large retained heaps, duplicate strings, large collections

---

## 5. GC Log Analysis

```bash
java -Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=20m \
     -jar app.jar
```

Key GC log patterns:
```
[info][gc] GC(42) Pause Young (Normal) 150M->80M(512M) 12.345ms
#                                       before→after(heap) pause_time

[info][gc] GC(43) Pause Full (Ergonomics) 500M->200M(512M) 1234.567ms
# Full GC with long pause — investigate memory pressure
```

**GCEasy.io** — paste GC log for visual analysis (free online tool).

---

## 6. Common Profiling Findings & Fixes

| Finding | Likely Cause | Fix |
|---------|-------------|-----|
| Wide `String.format` | Logging in hot path | Use SLF4J placeholders `log.debug("{}", x)` |
| Wide `ObjectInputStream` | Java serialization | Switch to JSON/Protobuf |
| Wide `synchronized` blocks | Lock contention | Use `ConcurrentHashMap`, `AtomicXxx`, or virtual threads |
| High allocation rate | `new StringBuilder()` in loop | Pre-allocate or use streams |
| `ClassLoader.loadClass` hot | Dynamic class loading | Pre-warm or use caching |
| High GC pause time | Memory pressure | Tune heap, switch GC, reduce allocation |

---

## Interview Questions

1. What is safepoint bias in profiling? → Traditional profilers only sample at safepoints — missing code that never reaches a safepoint. async-profiler uses OS signals to sample at any point.
2. JFR overhead? → < 1% for standard configuration; safe in production. Use `-XX:StartFlightRecording=settings=profile` for more events (slightly higher overhead).
3. What is a flamegraph? → A visualization where each box represents a method/function in the call stack; width proportional to CPU time. Read from bottom (thread entry) to top (hotspot).
4. Heap dump vs thread dump? → Heap dump: memory snapshot showing all objects and their sizes. Thread dump: snapshot of all thread stacks showing what each thread is doing.

---

**Next →** [50 Jakarta EE & Microservices](50-JakartaEEMicroservices.md)
