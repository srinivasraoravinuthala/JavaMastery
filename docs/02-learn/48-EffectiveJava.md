# 48 — Effective Java — Key Items with Code

**Previous:** [47 Cloud-Native Java](47-CloudNativeJava.md)

▶️ `pkg1core/core32EffectiveJavaDemo.java`

Based on *Effective Java, 3rd Edition* by Joshua Bloch — the canonical Java expert reference.

---

## Chapter 1: Creating and Destroying Objects

### Item 1 — Static factory methods over constructors
```java
// Constructor: client knows the type, no meaningful name
new ArrayList<String>()

// Static factory: named, can return subtype, can cache
List.of("a", "b")                          // immutable
Optional.of(value)                          // named context
Collections.unmodifiableList(list)          // wraps without subclass

// Own example: connection factory
public static Connection openReadOnly(String url) { ... }
public static Connection openReadWrite(String url) { ... }
// clearer than two constructors with a boolean flag
```

### Item 2 — Builder for many parameters
```java
// Telescoping constructors — bad
new Pizza(12, true, false, false, true)   // what do these booleans mean?

// Builder — readable, safe, handles optional params
Pizza pizza = new Pizza.Builder(12)
    .cheese(true)
    .pepperoni(true)
    .build();

public static class Builder {
    private final int size;
    private boolean cheese = false, pepperoni = false;
    public Builder(int size)          { this.size = size; }
    public Builder cheese(boolean v)  { cheese = v; return this; }
    public Builder pepperoni(boolean v){ pepperoni = v; return this; }
    public Pizza build()              { return new Pizza(this); }
}
```

### Item 3 — Singleton with enum
```java
// Most concise, handles serialization, reflection-safe
public enum AppConfig {
    INSTANCE;
    private final String apiKey = System.getenv("API_KEY");
    public String getApiKey() { return apiKey; }
}
// Usage
AppConfig.INSTANCE.getApiKey()
```

### Item 5 — Dependency injection over hard-wired resources
```java
// Bad — hard-wired, untestable
public class SpellChecker {
    private final Lexicon lexicon = new EnglishLexicon();  // inflexible
}

// Good — inject dependency
public class SpellChecker {
    private final Lexicon lexicon;
    public SpellChecker(Lexicon lexicon) { this.lexicon = Objects.requireNonNull(lexicon); }
}
```

### Item 6 — Avoid unnecessary objects
```java
// Bad — creates new Pattern every call
static boolean isRomanNumeral(String s) {
    return s.matches("[IVXLCDM]+");   // Pattern.compile inside every call
}

// Good — precompile
private static final Pattern ROMAN = Pattern.compile("[IVXLCDM]+");
static boolean isRomanNumeral(String s) { return ROMAN.matcher(s).matches(); }

// Prefer primitives over boxed
Long sum = 0L;          // Bad: creates Long object on every iteration
long sum = 0L;          // Good: primitive
for (long i = 0; i <= Integer.MAX_VALUE; i++) sum += i;
```

### Item 7 — Eliminate obsolete object references
```java
// Leaking stack example — null out popped elements
public Object pop() {
    if (size == 0) throw new EmptyStackException();
    Object result = elements[--size];
    elements[size] = null;   // remove stale reference — allows GC
    return result;
}
```

---

## Chapter 2: Methods Common to All Objects

### Item 10 — `equals` contract
```java
// Reflexive, Symmetric, Transitive, Consistent, Null-safe
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Point p)) return false;  // use pattern matching
    return x == p.x && y == p.y;
}
```
**Never** override `equals` in a subclass that adds a value component — you'll violate symmetry. Use composition instead.

### Item 11 — Always override `hashCode` when you override `equals`
```java
@Override
public int hashCode() {
    return Objects.hash(x, y);  // consistent with equals fields
}
// Rule: equal objects MUST have equal hash codes
```

### Item 12 — `toString` for diagnostics
```java
@Override
public String toString() {
    return "PhoneNumber{areaCode=" + areaCode + ", prefix=" + prefix + "}";
}
// Include all significant fields; format it in Javadoc
```

### Item 14 — `Comparable` for natural ordering
```java
public class Weight implements Comparable<Weight> {
    private final double kg;
    @Override
    public int compareTo(Weight other) {
        return Double.compare(this.kg, other.kg);  // use compare(), never subtract
    }
}
```

---

## Chapter 3: Classes and Interfaces

### Item 15 — Minimize accessibility
```java
// fields → private; expose via methods
// classes → package-private unless part of public API
// interfaces → prefer package-private for internal contracts

private int count;          // not package-private
public int getCount() { return count; }
```

### Item 17 — Minimize mutability (prefer immutable classes)
```java
// Java 16+ record is immutable by default
record ImmutablePoint(int x, int y) {}

// Manual immutable class rules:
// 1. No mutating methods
// 2. All fields final + private
// 3. Class final (or factory)
// 4. Defensive copy mutable fields
public final class ImmutablePeriod {
    private final Date start, end;
    public ImmutablePeriod(Date start, Date end) {
        this.start = new Date(start.getTime());   // defensive copy in
        this.end   = new Date(end.getTime());
    }
    public Date getStart() { return new Date(start.getTime()); }  // copy out
}
```

### Item 18 — Composition over inheritance
```java
// Wrapper / decorator — compose, don't extend
public class LoggingList<E> implements List<E> {
    private final List<E> delegate;
    public LoggingList(List<E> list) { this.delegate = list; }

    @Override
    public boolean add(E e) {
        log("Adding " + e);
        return delegate.add(e);
    }
    // delegate all other methods
}
```

### Item 22 — Interfaces for type definition only
```java
// Bad — constant interface anti-pattern
interface Constants { double PI = 3.14; }  // pollutes API

// Good — utility class or enum
public final class MathConstants {
    private MathConstants() {}
    public static final double PI = 3.14159265;
}
```

---

## Chapter 4: Generics

### Item 28 — Prefer List over array
```java
// Arrays are covariant → runtime failure
Object[] arr = new Long[1];
arr[0] = "hello";           // ArrayStoreException at runtime

// Generics are invariant → compile-time failure
List<Object> list = new ArrayList<Long>();  // won't compile
```

### Item 31 — Bounded wildcards for flexibility (PECS)
```java
// PECS: Producer Extends, Consumer Super
// Read from collection → <? extends T>
static double sum(Collection<? extends Number> nums) {
    return nums.stream().mapToDouble(Number::doubleValue).sum();
}

// Write to collection → <? super T>
static <T> void copy(List<? super T> dest, List<? extends T> src) {
    for (T e : src) dest.add(e);
}
```

---

## Chapter 5: Lambdas and Streams

### Item 42 — Lambdas over anonymous classes
```java
// Verbose anonymous class
list.sort(new Comparator<String>() {
    public int compare(String a, String b) { return a.length() - b.length(); }
});

// Lambda — clear intent
list.sort(Comparator.comparingInt(String::length));
```

### Item 44 — Standard functional interfaces
```java
// Use JDK interfaces — don't invent your own single-method interfaces
Supplier<List<String>> factory = ArrayList::new;
Consumer<String>       printer = System.out::println;
Function<String, Integer> len  = String::length;
Predicate<String>      nonEmpty = s -> !s.isEmpty();
BiFunction<String,Integer,String> repeat = String::repeat;
```

### Item 46 — Prefer side-effect-free stream operations
```java
// Bad — side effect in forEach
Map<String, Long> freq = new HashMap<>();
words.stream().forEach(w -> freq.merge(w, 1L, Long::sum));  // mutation inside stream

// Good — use collectors
Map<String, Long> freq = words.stream()
    .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
```

---

## Chapter 6: Exceptions

### Item 70 — Checked vs unchecked exceptions
```java
// Checked: recoverable conditions the caller must handle
public Config load(Path path) throws IOException { ... }

// Unchecked (RuntimeException): programming errors
Objects.requireNonNull(name, "name must not be null");
if (index < 0) throw new IllegalArgumentException("index: " + index);
```

### Item 75 — Include failure-capture information in exceptions
```java
// Bad
throw new IndexOutOfBoundsException("Index out of bounds");

// Good — include values for debugging
throw new IndexOutOfBoundsException(
    "Index: " + index + ", Size: " + size);
```

---

## Chapter 7: Concurrency

### Item 78 — Synchronize access to shared mutable data
```java
// volatile only prevents caching — not atomic operations
private volatile boolean stopRequested = false;   // OK for single-writer flag

// Use AtomicInteger for atomic increments
private final AtomicInteger count = new AtomicInteger();
count.incrementAndGet();   // atomic, no synchronization needed

// Use synchronized for compound actions
private synchronized void transferTo(Account dest, int amount) {
    balance -= amount;
    dest.balance += amount;
}
```

### Item 81 — Prefer higher-level concurrency utilities
```java
// Don't use wait/notify — use modern concurrency utilities
CountDownLatch ready   = new CountDownLatch(workerCount);
CountDownLatch start   = new CountDownLatch(1);
CountDownLatch done    = new CountDownLatch(workerCount);

// Prefer Executor, Callable, Future over raw threads
ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
Future<String> result = exec.submit(() -> fetchData());
String value = result.get(5, TimeUnit.SECONDS);
```

---

## Top 10 Effective Java Rules for Senior Devs

1. **Prefer immutability** — classes should be immutable unless there's a compelling reason.
2. **Composition over inheritance** — `extends` breaks encapsulation; wrap instead.
3. **Code to interfaces** — `List<String>` not `ArrayList<String>` in fields/params.
4. **Minimize scope** — prefer local variables over fields; smaller visibility modifiers.
5. **Avoid `null` returns** — return `Optional`, empty collections, or throw.
6. **Avoid float/double for exact values** — use `BigDecimal` for money.
7. **Override `hashCode` when you override `equals`**.
8. **Use enums instead of int constants**.
9. **Throw unchecked exceptions for programmer errors; checked for recoverable conditions**.
10. **Document thread-safety** — every class should say whether it is: immutable / thread-safe / not thread-safe.

---

## Interview Questions

1. What is the PECS rule? → Producer Extends (read), Consumer Super (write) — for bounded wildcard generics.
2. Why prefer List over array? → Generics are invariant (safer), arrays are covariant (runtime failure risk).
3. What is the Builder pattern and when to use it? → When constructors have ≥ 4 parameters or many optional ones — improves readability and safety.
4. Why can't you add a value component to an equals contract in a subclass? → You violate symmetry — `Point.equals(ColorPoint)` may differ from `ColorPoint.equals(Point)`.
5. What is defensive copying? → Copy mutable input in constructor and copy mutable output in accessor to prevent clients from mutating internal state.

---

**Previous →** [47 Cloud-Native Java](47-CloudNativeJava.md) · **Back to Index →** [00-INDEX](00-INDEX.md)
