# 21 — Java Version Features (5 → 25)

**Previous:** [20 Streams & Optional](20-StreamsAndOptional.md) · **Next:** [22 Data Structures](22-DataStructures.md)

▶️ `pkg2versions/versions1Java5Features.java` · `pkg2versions/versions2Java7Features.java` · `pkg2versions/versions3Java8Features.java` · `pkg2versions/versions4Java9To11Features.java` · `pkg2versions/versions5Java17Features.java` · `pkg2versions/versions6Java21Features.java` · `pkg2versions/versions7Java22Unnamed.java` · `pkg2versions/versions8Java24Gatherers.java` · `pkg2versions/versions9Java25ScopedValues.java` · `pkg2versions/versions10Java25FlexibleConstructors.java` · `pkg2versions/versions11Java25ModuleImport.java` · `pkg2versions/versions12ForeignMemoryAPI.java`

---

## Timeline summary

| Version | Year | Headline features |
|---------|------|-------------------|
| **5** | 2004 | Generics, enums, autoboxing, for-each, annotations |
| **7** | 2011 | try-with-resources, diamond `<>`, NIO.2 |
| **8** | 2014 | Lambdas, Streams, Optional, `java.time` |
| **9** | 2017 | Modules, `List.of`, `takeWhile` |
| **10** | 2018 | `var` local inference |
| **11** | 2018 LTS | HttpClient, String methods, run `.java` directly |
| **14** | 2020 | Switch expressions |
| **15** | 2020 | Text blocks |
| **16** | 2021 | Records, `instanceof` patterns |
| **17** | 2021 LTS | Sealed classes |
| **21** | 2023 LTS | Virtual threads, record patterns, sequenced collections |
| **22** | 2024 | Unnamed variables `_` (final), Foreign Function & Memory API (final) |
| **23** | 2024 | Markdown Javadoc, primitive patterns in switch (preview) |
| **24** | 2025 | Stream Gatherers (final), Class-File API (final) |
| **25** | 2025 LTS | Scoped Values (final), Flexible Constructor Bodies, Module Import Declarations |

---

## Run each demo

```bash
java pkg2versions/versions1Java5Features.java
java pkg2versions/versions2Java7Features.java
java pkg2versions/versions3Java8Features.java
java pkg2versions/versions4Java9To11Features.java
java pkg2versions/versions5Java17Features.java
java pkg2versions/versions6Java21Features.java
java pkg2versions/versions7Java22Unnamed.java
java pkg2versions/versions8Java24Gatherers.java
java pkg2versions/versions9Java25ScopedValues.java
java pkg2versions/versions10Java25FlexibleConstructors.java
java pkg2versions/versions11Java25ModuleImport.java
```

---

## What to learn from each

### Java 5 — foundations still used daily
Generics, enhanced for, `enum`, `ExecutorService`.

### Java 8 — biggest mindset shift
Stop writing loops for every transformation — use Streams. Replace `Date` with `java.time`.

### Java 9–11 — cleaner code
`List.of()`, `var`, `Files.readString`, `HttpClient`.

### Java 14–17 — modern syntax
Switch expressions, text blocks, records, sealed classes.

### Java 21 — concurrency revolution
```java
Thread.startVirtualThread(() -> fetchData());
```

Use virtual threads for **blocking I/O at scale** (HTTP calls, DB queries).

### Java 22 — unnamed variables & native interop
```java
for (int _ : list) { count++; }          // unnamed variable (JEP 456)
try { risky(); } catch (Exception _) {}  // ignore exception variable
```
Foreign Function & Memory API replaces JNI for calling native code without `unsafe`.

### Java 23–24 — Gatherers & Class-File API
```java
// Stream Gatherers (JEP 485) — custom intermediate operations
Stream.of(1,2,3,4).gather(Gatherers.windowSliding(2)).toList();
// → [[1,2],[2,3],[3,4]]
```

### Java 25 LTS — Scoped Values & Flexible Constructors
```java
// Scoped Values (JEP 506) — immutable, thread-local-like per-call data
static final ScopedValue<String> USER = ScopedValue.newInstance();
ScopedValue.where(USER, "ada").run(() -> process(USER.get()));

// Flexible Constructor Bodies (JEP 492) — statements before super()
class Sub extends Base {
    Sub(int x) {
        var validated = validate(x);   // ← allowed before super()
        super(validated);
    }
}

// Module Import Declarations (JEP 494)
import module java.sql;   // imports all exported packages of a module
```

---

**Full reference →** [Java Versions](../04-reference/01-JavaVersions.md) · [Java 22–25 Reference](../04-reference/15-Java22to25.md) · [Interview: Java 9–25 Features](../03-interview/10-Java9To25Features.md)

**Next →** [22 Data Structures](22-DataStructures.md)
