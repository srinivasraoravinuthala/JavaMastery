# System Design — Java-Specific Interview Questions

**Back to:** [Interview Index](00-INDEX.md)

---

## Core Concepts

### 1. How do you size a thread pool for a Java service?

**Short:** Match pool size to the bottleneck — CPU or I/O.

**Formula:**
```
CPU-bound:  pool size = N_cpu (+ 1 buffer)
I/O-bound:  pool size = N_cpu × (1 + wait_time / compute_time)
```

**Modern approach (Java 21+):** Use virtual threads for I/O-bound work — no manual sizing needed:
```java
// Virtual thread executor — one virtual thread per task, automatically managed
ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

// Classic bounded pool for CPU tasks
ExecutorService cpuPool = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

**Interview answer:** "For CPU-bound: N_cpu threads. For I/O-bound: N_cpu × (1 + blocking_ratio). With Java 21 virtual threads, I/O-bound services don't need manual pool sizing."

---

### 2. How do you implement caching in Java?

**Local in-process cache:**
```java
// Caffeine — best local cache library
import com.github.benmanes.caffeine.cache.*;

Cache<String, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .recordStats()
    .build();

// Read-through pattern
User user = cache.get(userId, id -> userRepository.findById(id).orElseThrow());

// Stats
CacheStats stats = cache.stats();
System.out.printf("hit rate=%.2f%n", stats.hitRate());
```

**Distributed cache (Redis with Spring):**
```java
// Spring Cache abstraction — swap implementation without code change
@Cacheable(value = "users", key = "#id", unless = "#result == null")
public User findUser(Long id) { return repo.findById(id).orElse(null); }

@CacheEvict(value = "users", key = "#user.id")
public User updateUser(User user) { return repo.save(user); }

@CachePut(value = "users", key = "#result.id")
public User createUser(User user) { return repo.save(user); }
```

**Cache levels:**
```
Request scope      → ThreadLocal / request-scoped bean
In-process         → Caffeine, Guava Cache
Distributed        → Redis, Hazelcast, Memcached
CDN                → Static assets, API responses with Cache-Control
```

---

### 3. How do you design a rate limiter in Java?

**Token bucket implementation:**
```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class TokenBucketRateLimiter {
    private final long capacity;
    private final long refillRate;     // tokens per second
    private final AtomicLong tokens;
    private volatile long lastRefillTime;

    public TokenBucketRateLimiter(long capacity, long refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTime = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTime;
        long newTokens = elapsed * refillRate / 1_000_000_000L;
        if (newTokens > 0) {
            tokens.set(Math.min(capacity, tokens.get() + newTokens));
            lastRefillTime = now;
        }
    }
}
```

**With Resilience4j:**
```java
RateLimiter limiter = RateLimiter.of("api",
    RateLimiterConfig.custom()
        .limitForPeriod(100)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ofMillis(500))
        .build());

limiter.executeSupplier(() -> callApi());
```

---

### 4. How do you handle distributed transactions in Java?

**Options:**
```
SAGA pattern (preferred)  → Compensating transactions, eventual consistency
Two-Phase Commit (2PC)    → ACID, but slow and blocks resources
Outbox pattern            → Guarantee exactly-once event publishing
```

**Outbox pattern (recommended):**
```java
@Transactional
public void createOrder(Order order) {
    orderRepo.save(order);                          // save business data
    outboxRepo.save(new OutboxEvent("ORDER_CREATED", order.getId(), toJson(order)));
    // ↑ Both in same local transaction — atomicity guaranteed
}

// Separate poller sends events from outbox to Kafka
@Scheduled(fixedDelay = 1000)
public void processOutbox() {
    outboxRepo.findUnprocessed().forEach(event -> {
        kafka.send(event.getType(), event.getPayload());
        outboxRepo.markProcessed(event.getId());
    });
}
```

---

### 5. REST vs gRPC — when to choose what?

| | REST/HTTP | gRPC |
|-|-----------|------|
| Protocol | HTTP/1.1 or 2 | HTTP/2 |
| Format | JSON (text) | Protobuf (binary) |
| Contract | OpenAPI (optional) | `.proto` (required) |
| Streaming | SSE/WebSocket | Native bi-directional |
| Performance | Good | 5–10× faster serialization |
| Browser support | Native | Needs grpc-web proxy |
| Use case | Public APIs, browser clients | Internal microservices, streaming |

**gRPC example (Java):**
```protobuf
// user.proto
service UserService {
    rpc GetUser(UserRequest) returns (UserResponse);
    rpc StreamUsers(UserFilter) returns (stream UserResponse);
}
```
```java
// Server implementation
@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    @Override
    public void getUser(UserRequest req, StreamObserver<UserResponse> obs) {
        User user = repo.findById(req.getId()).orElseThrow();
        obs.onNext(UserResponse.newBuilder().setName(user.getName()).build());
        obs.onCompleted();
    }
}
```

---

### 6. How do you design a connection pool?

```java
// HikariCP — fastest JDBC pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost/mydb");
config.setMaximumPoolSize(10);               // rule: cores × 2 for I/O
config.setMinimumIdle(2);
config.setConnectionTimeout(3000);           // 3s before throw
config.setIdleTimeout(600_000);             // 10 min idle before remove
config.setMaxLifetime(1_800_000);           // 30 min max lifetime
config.setLeakDetectionThreshold(5000);     // log if connection held > 5s

DataSource ds = new HikariDataSource(config);
```

**Pool sizing formula (database):**
```
For PostgreSQL: connections = (N_cores × 2) + effective_spindle_count
In practice: 10–20 connections per app instance is usually right
```

---

### 7. How do you implement event-driven architecture in Java?

**With Kafka (Spring Kafka):**
```java
// Producer
@Autowired KafkaTemplate<String, OrderEvent> kafka;

public void placeOrder(Order order) {
    order = orderRepo.save(order);
    kafka.send("orders", order.getId().toString(),
               new OrderEvent("PLACED", order));
}

// Consumer
@KafkaListener(topics = "orders", groupId = "inventory-service")
public void onOrder(OrderEvent event) {
    switch (event.type()) {
        case "PLACED" -> inventory.reserve(event.order());
        case "CANCELLED" -> inventory.release(event.order());
    }
}
```

**Key Kafka concepts:**
- **Partition** — unit of parallelism; consumer group has one consumer per partition max
- **Offset** — position in partition; commit after processing for at-least-once
- **Consumer group** — load-balances partitions across consumers
- **Exactly-once** — use `EXACTLY_ONCE_V2` producer config + transactional consumer

---

### 8. System Design: URL Shortener

**Java-specific design decisions:**
```java
// Base62 encoding for short code
public String encode(long id) {
    String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    StringBuilder sb = new StringBuilder();
    while (id > 0) { sb.append(chars.charAt((int)(id % 62))); id /= 62; }
    return sb.reverse().toString();
}

// Cache with Caffeine (in-process) + Redis (distributed)
@Cacheable("urls")
public String resolve(String code) { return urlRepo.findByCode(code).getLongUrl(); }

// Bloom filter to avoid DB hit for non-existent codes
BloomFilter<String> filter = BloomFilter.create(Funnels.stringFunnel(UTF_8), 10_000_000, 0.01);
if (!filter.mightContain(code)) return null;  // definitely doesn't exist
```

---

## Rapid-Fire System Design

1. How to detect deadlock in Java? → `ThreadMXBean.findDeadlockedThreads()` + JStack.
2. Circuit breaker library? → Resilience4j (preferred), Hystrix (deprecated).
3. Best Java HTTP server for microservices? → Spring Boot (full-featured), Javalin/Micronaut (lightweight).
4. How to profile Java memory? → Java Flight Recorder + JMC, heap dumps with `jmap`, async-profiler.
5. Distributed lock in Java? → Redisson `RLock` over Redis, or ZooKeeper.
6. Feature flags? → Togglz, LaunchDarkly Java SDK, or `@ConditionalOnProperty` for static flags.
7. Idempotency key? → Store in DB with unique constraint; return cached response on duplicate.
8. API versioning? → URL path `/v1/`, request header `Accept: application/vnd.api+v2`, or query param.
9. Saga vs 2PC? → Saga for microservices (eventual consistency); 2PC only within single resource manager.
10. Observability pillars? → Metrics (Micrometer), Tracing (OpenTelemetry), Logging (SLF4J → ELK).
