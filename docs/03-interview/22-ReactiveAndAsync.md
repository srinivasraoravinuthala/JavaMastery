# Reactive & Async Java — Interview Questions

**Back to:** [Interview Index](00-INDEX.md)

---

### 1. What is reactive programming?

**Short:** A programming model where you compose asynchronous data streams with operators, and subscribers pull/receive items with backpressure control.

**Detailed:** Reactive is built on the **Reactive Streams** specification (4 interfaces: `Publisher`, `Subscriber`, `Subscription`, `Processor`). The key properties are:
- **Asynchronous** — data flows without blocking
- **Lazy** — nothing executes until subscribed
- **Backpressure** — subscriber signals demand; prevents memory overflow
- **Composable** — operators chain transformations without callbacks

Java ecosystem: JDK `Flow` API, Project Reactor (`Mono`/`Flux`), RxJava 3, Mutiny (Quarkus).

---

### 2. Reactive vs Virtual Threads — when to choose?

| | Reactive (Reactor/WebFlux) | Virtual Threads (Java 21+) |
|-|----------------------------|----------------------------|
| Code style | Functional pipeline chains | Imperative, blocking code |
| Backpressure | Built-in protocol | Manual (queues needed) |
| Debugging stack traces | Non-intuitive (async frames) | Natural call stack |
| Error handling | Operator-based (`onErrorResume`) | try/catch |
| JPA/JDBC | R2DBC only | Standard JPA works |
| Streaming | Native | Workarounds needed |
| Learning curve | Steep | Minimal |
| Memory per task | Very low (no thread stack) | Low (1–2 KB virtual stack) |

**Recommendation:** Virtual threads for new services (simpler, JPA-compatible). Reactive for streaming, SSE, WebSocket-heavy, or when R2DBC is needed.

---

### 3. What is backpressure and why does it matter?

**Short:** Backpressure is the mechanism for a subscriber to tell the publisher "send me N items" — preventing unbounded buffering and OOM.

**Detailed:**
```java
// Without backpressure — consumer overwhelmed
producer.emit(1_000_000_000_items);    // fast producer
consumer.process(item);               // slow consumer → OOM

// With backpressure — consumer controls flow
publisher.subscribe(new Subscriber<>() {
    public void onSubscribe(Subscription s) { s.request(10); }  // request 10
    public void onNext(T item) {
        process(item);
        subscription.request(10);    // request 10 more when ready
    }
});
```

**Backpressure strategies in Reactor:**
```java
Flux.range(1, 1_000_000)
    .onBackpressureBuffer(1000)        // buffer up to 1000; drop beyond
    .onBackpressureDrop(dropped -> log("dropped: " + dropped))
    .onBackpressureLatest()            // keep only latest item
    .onBackpressureError();            // throw overflow exception
```

---

### 4. `flatMap` vs `concatMap` vs `switchMap`?

```java
// flatMap — subscribe to inner publishers eagerly, merge results (unordered)
Flux.just("a", "b", "c")
    .flatMap(s -> fetchAsync(s))   // all 3 fetches start simultaneously
    // results arrive in any order

// concatMap — subscribe sequentially, preserves order
Flux.just("a", "b", "c")
    .concatMap(s -> fetchAsync(s))  // waits for "a" to complete, then "b", then "c"
    // results in order: a, b, c

// switchMap — cancels previous inner publisher on new item (latest-wins)
searchInput.switchMap(query -> search(query))  // cancel previous search on new keystroke
```

| Operator | Concurrency | Order | Use case |
|----------|-------------|-------|----------|
| `flatMap` | Eager parallel | No | Independent parallel calls |
| `concatMap` | Sequential | Yes | Order matters (pagination) |
| `switchMap` | Latest only | N/A | Autocomplete, search |

---

### 5. What is `subscribeOn` vs `publishOn`?

```java
Flux.just(1, 2, 3)
    .subscribeOn(Schedulers.boundedElastic())  // affects upstream subscription thread
    .map(i -> i * 2)                           // runs on boundedElastic
    .publishOn(Schedulers.parallel())          // switches thread downstream from here
    .map(i -> i + 1)                           // runs on parallel
    .subscribe(System.out::println);
```

- **`subscribeOn`** — controls the thread that subscribes to the source (affects upstream)
- **`publishOn`** — controls the thread for downstream operators (affects downstream)
- Multiple `publishOn` calls stack — each one switches thread for the segment below it

---

### 6. CompletableFuture pitfalls?

```java
// Pitfall 1: blocking inside async chain
CompletableFuture.supplyAsync(() -> {
    return jdbcRepo.findById(1);    // blocks ForkJoinPool common thread — BAD
});
// Fix: use dedicated executor for blocking ops
CompletableFuture.supplyAsync(() -> jdbcRepo.findById(1), blockingExecutor);

// Pitfall 2: not handling exceptions
CompletableFuture<String> cf = fetchData();
cf.thenApply(data -> process(data));   // if fetchData fails, exception silently ignored
// Fix: always chain exceptionally or handle
cf.thenApply(data -> process(data))
  .exceptionally(ex -> "error: " + ex.getMessage());

// Pitfall 3: allOf returns CompletableFuture<Void>
CompletableFuture<Void> all = CompletableFuture.allOf(cf1, cf2, cf3);
// Can't get results from allOf directly — must join each individual CF
all.thenRun(() -> {
    String r1 = cf1.join();
    String r2 = cf2.join();   // safe here — already completed
});

// Pitfall 4: cf.get() without timeout — hangs forever
cf.get();                          // BAD
cf.get(5, TimeUnit.SECONDS);      // GOOD
```

---

### 7. What is the difference between `Mono.block()` and `cf.get()`?

Both synchronously wait for a result — they're essentially the same thing at the JVM level. In production:
- `block()` in Reactor logs a warning if called from inside a reactive chain (deadlock risk on bounded scheduler)
- Both should only appear in `main()`, tests, or at integration points
- Prefer `StepVerifier` for testing Reactor pipelines

---

### 8. How does Project Reactor handle errors?

```java
// onErrorReturn — replace error with static value
Flux.just(1, 2, 0, 4)
    .map(n -> 10 / n)                    // throws on 0
    .onErrorReturn(-1)                   // sequence ends with -1

// onErrorResume — switch to fallback publisher
Flux.just("a", "bad", "c")
    .flatMap(s -> fetchUser(s))
    .onErrorResume(NotFoundException.class, ex -> Mono.just(User.GUEST))

// doOnError — side effect (logging) without recovering
flux.doOnError(ex -> log.error("Failed", ex))

// retry — resubscribe on error
flux.retry(3)

// retryWhen — sophisticated retry with backoff
flux.retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                    .maxBackoff(Duration.ofSeconds(5))
                    .jitter(0.5))

// timeout — fail if no item within duration
flux.timeout(Duration.ofSeconds(3))
    .onErrorResume(TimeoutException.class, ex -> Flux.just(DEFAULT))
```

---

### 9. What is the Reactive Streams specification?

Four interfaces in `java.util.concurrent.Flow` (JDK 9+):
```java
interface Publisher<T>  { void subscribe(Subscriber<? super T> s); }
interface Subscriber<T> {
    void onSubscribe(Subscription s);
    void onNext(T item);
    void onError(Throwable t);
    void onComplete();
}
interface Subscription  { void request(long n); void cancel(); }
interface Processor<T,R> extends Subscriber<T>, Publisher<R> {}
```

Rules: `onNext` never after `onError`/`onComplete`; `request(n)` must be positive; `cancel` stops future delivery.

---

### 10. How do you test reactive code?

```java
import reactor.test.StepVerifier;

@Test
void testFlux() {
    Flux<Integer> flux = Flux.just(1, 2, 3)
        .map(n -> n * 2);

    StepVerifier.create(flux)
        .expectNext(2)
        .expectNext(4)
        .expectNext(6)
        .expectComplete()
        .verify(Duration.ofSeconds(1));   // with timeout
}

@Test
void testError() {
    Mono<String> mono = Mono.error(new RuntimeException("boom"));

    StepVerifier.create(mono)
        .expectErrorMessage("boom")
        .verify();
}
```

---

## Rapid-Fire

1. `Mono` vs `Flux`? → Mono: 0 or 1 item. Flux: 0 to N items.
2. Hot vs cold publisher? → Cold: starts fresh per subscriber. Hot: shares emission (e.g., live events).
3. `Sinks.many()` vs `SubmissionPublisher`? → Both are programmatic publishers; Sinks is Reactor-native and thread-safe.
4. Schedulers types? → `immediate()`, `single()`, `parallel()`, `boundedElastic()` (for blocking I/O).
5. `zip` vs `combineLatest`? → zip pairs elements by index; combineLatest re-emits when any source emits with latest from others.
6. `buffer` vs `window`? → `buffer` collects into a `List<T>`; `window` emits a `Flux<T>` per window.
7. R2DBC vs JDBC? → R2DBC: reactive, non-blocking. JDBC: blocking (fine with virtual threads).
8. Can you use reactive with Spring MVC? → Yes, you can return `Mono`/`Flux` from MVC controllers (handled by reactive wrapper), but full reactive requires WebFlux.
9. Difference between `then` and `thenMany`? → `then` returns `Mono<Void>` (ignore upstream); `thenMany` subscribes to another `Flux`.
10. How to merge two Flux? → `Flux.merge(f1, f2)` (eager), `Flux.concat(f1, f2)` (sequential), `Flux.zip(f1, f2)` (paired).
