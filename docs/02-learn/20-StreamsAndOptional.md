# 20 — Streams & Optional

**Previous:** [19 Lambdas & Functional](19-LambdasAndFunctional.md) · **Next:** [21 Java Versions](21-JavaVersions.md)

▶️ `core22StreamsDemo` · `core23OptionalDemo`

---

## Stream pipeline

```java
List<String> result = names.stream()
    .filter(s -> !s.isBlank())      // intermediate — lazy
    .map(String::toUpperCase)       // intermediate
    .sorted()                       // intermediate
    .toList();                      // terminal — triggers execution
```

| Type | Examples | Lazy? |
|------|----------|-------|
| Intermediate | `filter`, `map`, `flatMap`, `sorted`, `distinct` | Yes |
| Terminal | `collect`, `forEach`, `reduce`, `count`, `toList` | Triggers pipeline |

💡 Streams are **single-use** — create a new stream for a second pipeline.

---

## Common operations

```java
long count = list.stream().filter(x -> x > 0).count();
int sum = list.stream().mapToInt(Integer::intValue).sum();
OptionalInt max = list.stream().mapToInt(Integer::intValue).max();

Map<String, List<User>> byDept = users.stream()
    .collect(Collectors.groupingBy(User::dept));
```

---

## Optional — avoid null returns

```java
Optional<User> findUser(int id) {
    User u = db.get(id);
    return Optional.ofNullable(u);
}

findUser(42)
    .map(User::name)
    .filter(n -> !n.isBlank())
    .ifPresentOrElse(System.out::println, () -> System.out.println("missing"));
```

| Do | Don't |
|----|-------|
| Return `Optional` from lookup methods | Use as field or method parameter |
| Use `orElse`, `orElseGet`, `ifPresent` | Call `.get()` without checking |

**Milestone:** You completed **Core APIs** (chapters 16–20). 🎉

**Interview drill →** [Streams.md](../03-interview/Streams.md)

**Next →** [21 Java Versions](21-JavaVersions.md)
