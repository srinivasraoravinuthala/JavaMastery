# 45 — Reactive Java

**Previous:** [44 GraalVM & Native Image](44-GraalVMNative.md) · **Next:** [46 Security in Java](46-SecurityJava.md)

▶️ `pkg7concurrency/concurrency9CompletableFutureAdvanced.java` (CompletableFuture advanced patterns)

---

## Why Reactive?

Traditional blocking I/O holds a thread per request. At high concurrency:
- 10,000 simultaneous HTTP calls = 10,000 blocked threads = ~10 GB RAM
- Reactive: 10,000 calls handled by a few threads + callbacks/continuations

> **Java 21+ note:** Virtual threads largely solve the "thread per request" cost for blocking I/O. Reactive is still relevant for **backpressure**, **streaming pipelines**, and **existing reactive codebases** (Spring WebFlux, RxJava).

---

## 1. CompletableFuture (JDK Built-in)

The JDK's async composition API — no extra dependency required:

```java
import java.util.concurrent.*;

CompletableFuture<String> cf = CompletableFuture
    .supplyAsync(() -> fetchUser(1))            // run on ForkJoinPool
    .thenApply(user -> user.toUpperCase())      // transform (non-blocking)
    .thenCompose(name -> fetchOrders(name))     // chain another async op
    .exceptionally(ex -> "error: " + ex.getMessage())
    .whenComplete((result, ex) -> log(result));

String value = cf.get(5, TimeUnit.SECONDS);    // block with timeout
```

**Key operators:**
| Method | Purpose |
|--------|---------|
| `supplyAsync(Supplier)` | Start async computation |
| `thenApply(fn)` | Transform result |
| `thenCompose(fn)` | Flat-map to another CF |
| `thenCombine(cf2, fn)` | Combine two independent CFs |
| `allOf(cf1, cf2, ...)` | Wait for all |
| `anyOf(cf1, cf2, ...)` | Wait for first |
| `exceptionally(fn)` | Handle exception |
| `handle(fn)` | Handle both result and exception |

```java
// Run three calls in parallel, combine results
CompletableFuture<String> name    = fetchNameAsync(id);
CompletableFuture<Integer> score  = fetchScoreAsync(id);
CompletableFuture<String> avatar  = fetchAvatarAsync(id);

CompletableFuture.allOf(name, score, avatar)
    .thenRun(() -> System.out.println(
        name.join() + " score=" + score.join() + " avatar=" + avatar.join()
    ));
```

---

## 2. Java Flow API (JDK 9+)

The JDK's built-in reactive streams (Publisher/Subscriber) — low-level:

```java
import java.util.concurrent.Flow.*;
import java.util.concurrent.SubmissionPublisher;

// Publisher
try (var pub = new SubmissionPublisher<Integer>()) {
    // Subscriber
    pub.subscribe(new Subscriber<Integer>() {
        Subscription sub;
        public void onSubscribe(Subscription s) { sub = s; s.request(Long.MAX_VALUE); }
        public void onNext(Integer item)         { System.out.println("Got: " + item); }
        public void onError(Throwable t)         { t.printStackTrace(); }
        public void onComplete()                 { System.out.println("Done"); }
    });

    for (int i = 1; i <= 5; i++) pub.submit(i);
}
```

In practice, use **Project Reactor** or **RxJava** for production pipelines.

---

## 3. Project Reactor (Spring WebFlux)

Reactor is the reactive library behind Spring WebFlux:

```xml
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
    <version>3.6.0</version>
</dependency>
```

### Mono (0 or 1 item)
```java
import reactor.core.publisher.Mono;

Mono<String> greeting = Mono.just("Hello")
    .map(String::toUpperCase)
    .filter(s -> s.length() > 3)
    .defaultIfEmpty("Hi")
    .doOnNext(s -> log("emitting: " + s));

greeting.subscribe(System.out::println);           // lazy — subscribing triggers it

// Block (use only in tests/main)
String result = greeting.block();
```

### Flux (0..N items)
```java
import reactor.core.publisher.Flux;

Flux<Integer> numbers = Flux.range(1, 10)
    .filter(n -> n % 2 == 0)
    .map(n -> n * n)
    .take(3);

numbers.subscribe(System.out::println);  // 4, 16, 36
```

### Operators cheat sheet
```java
Flux.just(1,2,3,4,5)
    .map(n -> n * 2)                        // transform
    .flatMap(n -> fetchAsync(n))            // async transform + flatten
    .concatMap(n -> fetchAsync(n))          // ordered flatMap
    .filter(n -> n > 4)                     // predicate
    .take(3)                                // limit
    .skip(1)                                // skip first N
    .buffer(2)                              // List<T> of size N
    .window(Duration.ofSeconds(1))          // time window
    .distinct()                             // deduplicate
    .reduce(0, Integer::sum)                // fold to Mono
    .collectList()                          // Mono<List<T>>
    .zipWith(otherFlux)                     // pair elements
    .onErrorResume(ex -> Flux.just(-1))     // fallback on error
    .retry(3)                               // retry on error
    .timeout(Duration.ofSeconds(5))         // timeout per element
    .subscribeOn(Schedulers.boundedElastic()) // thread pool
    .publishOn(Schedulers.parallel());       // downstream thread pool
```

---

## 4. Backpressure

Backpressure = subscriber controls how fast producer emits.

```java
Flux.range(1, 1_000_000)
    .onBackpressureDrop()                 // drop items if subscriber is slow
    // alternatives: onBackpressureBuffer(), onBackpressureLatest(), onBackpressureError()
    .subscribeOn(Schedulers.parallel())
    .subscribe(new BaseSubscriber<Integer>() {
        protected void hookOnSubscribe(Subscription sub) {
            request(10);                  // request only 10 at a time
        }
        protected void hookOnNext(Integer value) {
            process(value);
            request(10);                  // request more after processing
        }
    });
```

---

## 5. Spring WebFlux Example

```java
@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired UserRepository repo;  // ReactiveCrudRepository

    @GetMapping("/users")
    public Flux<User> allUsers() {
        return repo.findAll();
    }

    @GetMapping("/users/{id}")
    public Mono<ResponseEntity<User>> getUser(@PathVariable Long id) {
        return repo.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> createUser(@RequestBody User user) {
        return repo.save(user);
    }
}
```

---

## 6. Reactive vs Virtual Threads

| | Reactive (WebFlux) | Virtual Threads (Java 21+) |
|-|--------------------|-----------------------------|
| Code style | Functional chains | Imperative, blocking |
| Backpressure | Built-in | Manual (queues) |
| Debugging | Hard (no stack trace) | Easy (real stack) |
| Learning curve | Steep | Flat |
| Best for | Streaming, high-fan-out pipelines | CRUD APIs, general services |
| JPA support | R2DBC (reactive) | Standard JPA/Hibernate |

**Recommendation:** New projects use virtual threads + Spring Boot 3. Reactive for streaming data, SSE, WebSocket-heavy workloads.

---

## Interview Questions

1. What is backpressure? → The mechanism where a subscriber signals to its publisher how many items it can handle, preventing buffer overflow.
2. `Mono` vs `Flux`? → Mono is 0 or 1 item; Flux is 0 to N items.
3. `flatMap` vs `concatMap`? → flatMap subscribes eagerly (parallel, unordered); concatMap preserves order (sequential).
4. `subscribeOn` vs `publishOn`? → subscribeOn affects the upstream thread; publishOn switches thread for downstream operators.
5. When to choose reactive over virtual threads? → When you need backpressure, streaming, or are using reactive databases (R2DBC).

---

**Next →** [46 Security in Java](46-SecurityJava.md)
