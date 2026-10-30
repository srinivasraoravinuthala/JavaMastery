# 43 — Foreign Function & Memory API (Project Panama)

**Previous:** [42 Pattern Matching Deep Dive](42-PatternMatchingDeep.md) · **Next:** [44 GraalVM & Native Image](44-GraalVMNative.md)

▶️ `pkg2versions/versions12ForeignMemoryAPI.java` · `pkg2versions/versions7Java22Unnamed.java`

---

## What is the Foreign Function & Memory API?

Project Panama's **Foreign Function & Memory API** (JEP 454, final in Java 22) provides:
- **Off-heap memory** management via `MemorySegment` and `Arena`
- **Native function calls** via `Linker` — no JNI C glue code required
- **Safe memory layouts** via `MemoryLayout` and `VarHandle`

It replaces `sun.misc.Unsafe` and JNI for most native interop use cases.

---

## Core Concepts

### Memory Segments
```java
import java.lang.foreign.*;

// Allocate 1 KB of off-heap memory, auto-freed when arena closes
try (Arena arena = Arena.ofConfined()) {
    MemorySegment seg = arena.allocate(1024);
    seg.set(ValueLayout.JAVA_INT, 0, 42);           // write int at offset 0
    int val = seg.get(ValueLayout.JAVA_INT, 0);     // read it back
    System.out.println(val);  // 42
}   // memory freed here
```

**Arena types:**
| Arena | Lifetime | Thread safety |
|-------|----------|---------------|
| `Arena.ofConfined()` | try-with-resources | single thread |
| `Arena.ofShared()` | explicit `close()` | multi-thread |
| `Arena.ofAuto()` | GC-managed | multi-thread |
| `Arena.global()` | never freed | always open |

---

### Memory Layouts

Describe C struct layouts to Java:

```java
// C struct: struct Point { int x; int y; }
StructLayout POINT = MemoryLayout.structLayout(
    ValueLayout.JAVA_INT.withName("x"),
    ValueLayout.JAVA_INT.withName("y")
);

VarHandle X = POINT.varHandle(MemoryLayout.PathElement.groupElement("x"));
VarHandle Y = POINT.varHandle(MemoryLayout.PathElement.groupElement("y"));

try (Arena arena = Arena.ofConfined()) {
    MemorySegment point = arena.allocate(POINT);
    X.set(point, 0L, 10);
    Y.set(point, 0L, 20);
    System.out.println("x=" + X.get(point, 0L) + " y=" + Y.get(point, 0L));
}
```

---

### Calling Native Functions

```java
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

// Call C's strlen() without any JNI C code
Linker linker = Linker.nativeLinker();
SymbolLookup stdlib = linker.defaultLookup();

MethodHandle strlen = linker.downcallHandle(
    stdlib.find("strlen").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
);

try (Arena arena = Arena.ofConfined()) {
    MemorySegment str = arena.allocateFrom("Hello, Panama!");
    long len = (long) strlen.invoke(str);
    System.out.println("strlen = " + len);  // 14
}
```

---

### Upcall Stubs (Callbacks from Native to Java)

```java
// Wrap a Java lambda as a C function pointer
MethodHandle comparator = MethodHandles.lookup().findStatic(
    MyClass.class, "compare",
    MethodType.methodType(int.class, int.class, int.class)
);

MemorySegment callback = linker.upcallStub(
    comparator,
    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                          ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
    Arena.ofAuto()
);
// Pass callback to native qsort, etc.
```

---

## FFM vs JNI Comparison

| | JNI | FFM API (Java 22+) |
|-|-----|---------------------|
| Native glue code | Required (.c + .h) | Not needed |
| Safety | Unsafe by default | Safe segments; bounds-checked |
| Off-heap memory | `ByteBuffer.allocateDirect` | `Arena` + `MemorySegment` |
| Function call overhead | High (crossing JNI) | Low (MethodHandle) |
| Memory lifecycle | Manual/GC-registered | Arena-scoped, auto-freed |
| Preview → final | — | JEP 424 (preview) → JEP 454 (Java 22, final) |

---

## Practical Use Cases

1. **BLAS/LAPACK** — high-performance linear algebra without JNI wrappers
2. **OpenSSL** — call SSL_CTX functions for custom TLS
3. **SQLite** — embed SQLite native lib; call `sqlite3_open` etc.
4. **Off-heap caches** — allocate GB of data outside GC heap
5. **SIMD / Vector operations** — pair with Vector API (incubating)

---

## Interview Questions

1. What does `Arena.ofConfined()` guarantee? → Memory auto-freed on close; only accessible from the creating thread.
2. Difference between `MemorySegment` and `ByteBuffer`? → Segments are sized, typed with layouts, arena-bound; ByteBuffer is GC-managed and limited to 2 GB.
3. What is a downcall handle? → A `MethodHandle` that calls a native function from Java.
4. What is an upcall stub? → A native function pointer that calls back into a Java method.
5. Why replace `sun.misc.Unsafe`? → `Unsafe` is JVM-internal, bypasses all safety; FFM provides the same power with bounds checking, lifecycle management, and a stable public API.

---

**Next →** [44 GraalVM & Native Image](44-GraalVMNative.md)
