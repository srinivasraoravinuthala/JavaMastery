# Java 22–25 Cheatsheet

One-page reference for all finalized language and API changes from Java 22 through 25 LTS.

---

## Java 22 — March 2024

### Unnamed Variables `_` (JEP 456 — Final)
```java
for (var _ : list) count++;                       // ignore loop var
catch (IOException _) { fallback(); }             // ignore exception
if (o instanceof Foo(int x, _)) { use(x); }       // ignore component
list.removeIf(_ -> size-- > 0);                   // ignore lambda param
```

### Foreign Function & Memory API (JEP 454 — Final)
```java
try (Arena a = Arena.ofConfined()) {
    MemorySegment seg = a.allocate(ValueLayout.JAVA_INT, 42);
    int val = seg.get(ValueLayout.JAVA_INT, 0);   // 42
}
Linker linker = Linker.nativeLinker();
MethodHandle strlen = linker.downcallHandle(
    linker.defaultLookup().find("strlen").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
);
```

### Launch Multi-File Source Programs (JEP 458)
```bash
java Main.java          # compiles + runs Main.java and all files it references
```

---

## Java 23 — September 2024

### Markdown Documentation Comments (JEP 467 — Final)
```java
/// Converts a `String` to an `int`.
///
/// ## Throws
/// - `NumberFormatException` if not a valid integer
///
/// @param s the string
/// @return the integer value
public static int parseInt(String s) { ... }
```

### Primitive Types in Patterns (JEP 455 — Preview → Java 25 Final)
```java
long n = 100_000L;
if (n instanceof int i) use(i);       // narrowing check

switch (value) {
    case byte b  -> smallHandle(b);
    case int i   -> handle(i);
    case long l  -> bigHandle(l);
}
```

### Module Import Declarations (JEP 476 — Preview → Java 25 Final)
```java
import module java.sql;   // imports all exported packages of java.sql
```

---

## Java 24 — March 2025

### Stream Gatherers (JEP 485 — Final)
```java
// Built-in gatherers
stream.gather(Gatherers.windowFixed(3))      // [[1,2,3],[4,5,6]]
stream.gather(Gatherers.windowSliding(3))    // [[1,2,3],[2,3,4]...]
stream.gather(Gatherers.fold(() -> 0, Integer::sum))  // running fold
stream.gather(Gatherers.scan(() -> 0, Integer::sum))  // running prefix

// Custom gatherer
Gatherer<Integer, ?, String> toHex = Gatherer.ofSequential(
    (state, elem, downstream) -> downstream.push(Integer.toHexString(elem))
);
List.of(10,255,16).stream().gather(toHex).toList();  // ["a","ff","10"]
```

### Class-File API (JEP 484 — Final)
```java
ClassModel cm = ClassFile.of().parse(Path.of("Foo.class"));
cm.methods().forEach(m -> System.out.println(m.methodName()));
```

### AOT Class Loading (JEP 483)
```bash
java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconf -jar app.jar
java -XX:AOTMode=on    -XX:AOTConfiguration=app.aotconf -jar app.jar
```

### Quantum-Resistant Crypto (JEP 496/497)
```java
KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-KEM-768");
KeyPair kp = kpg.generateKeyPair();
```

---

## Java 25 LTS — September 2025

### Scoped Values (JEP 506 — Final)
```java
static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

// Bind for the duration of a lambda
ScopedValue.where(CURRENT_USER, user).run(() -> handleRequest());

// Anywhere inside the run() lambda
User u = CURRENT_USER.get();
```

| | ThreadLocal | ScopedValue |
|-|-------------|-------------|
| Mutability | Mutable | Immutable |
| Scope | Thread lifetime | `where(...).run()` |
| Memory | Manual `remove()` | Auto on scope exit |
| Virtual threads | Inherited (copied) | Read-only share |

---

### Flexible Constructor Bodies (JEP 492 — Final)
```java
class Sub extends Base {
    Sub(String raw) {
        var validated = Objects.requireNonNull(raw).strip();  // before super()!
        super(validated);
    }
}
```
Cannot access `this` fields before `super()`; only local vars allowed.

---

### Module Import Declarations (JEP 494 — Final)
```java
import module java.base;    // everything from java.base
import module java.sql;     // java.sql + transitive (javax.sql, etc.)
import module java.logging;
```

---

### Primitive Types in Patterns (JEP 488 — Final)
See Java 23 preview — same syntax, now stable.

---

### Structured Concurrency (JEP 505 — Final)
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Supplier<User>    user  = scope.fork(() -> fetchUser(id));
    Supplier<Integer> score = scope.fork(() -> fetchScore(id));
    scope.join().throwIfFailed();
    return new Profile(user.get(), score.get());
}
// Both subtasks cancelled automatically if either fails
```

---

## Version Quick-Pick Guide

| Need | Use |
|------|-----|
| Ignore variable | `_` (Java 22) |
| Call native C library | FFM API `Linker` (Java 22) |
| Custom stream operation | `gather(Gatherer)` (Java 24) |
| Off-heap memory | `Arena` + `MemorySegment` (Java 22) |
| Per-request immutable data | `ScopedValue` (Java 25) |
| Validate before `super()` | Flexible constructor (Java 25) |
| Import whole module | `import module X` (Java 25) |
| Run parallel tasks safely | `StructuredTaskScope` (Java 25) |
| Post-quantum crypto | ML-KEM, ML-DSA (Java 25) |

---

## LTS Target Summary

```
Java 8  (2014) → Most legacy systems
Java 11 (2018) → Old enterprise baseline
Java 17 (2021) → Current enterprise standard
Java 21 (2023) → Modern standard (virtual threads, records, patterns)
Java 25 (2025) → Latest LTS (scoped values, flexible constructors, gatherers)
```

**Full reference:** [04-reference/15-Java22to25.md](../04-reference/15-Java22to25.md)
