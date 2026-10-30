# 50 — Jakarta EE & Microservices Frameworks

**Previous:** [49 JFR & Profiling](49-JFRAndProfiling.md) · **Next:** [51 gRPC in Java](51-gRPCInJava.md)

---

## Beyond Spring Boot

While Spring Boot dominates enterprise Java, the ecosystem has more options — each optimized for different constraints:

| Framework | Native Image | Startup | Memory | Best For |
|-----------|-------------|---------|--------|----------|
| **Spring Boot 3** | Yes (3.0+) | ~2s (JVM) / ~100ms (native) | ~200MB | Full-featured, biggest ecosystem |
| **Quarkus** | Yes (first-class) | ~2ms (native) | ~30MB | Cloud-native, serverless, K8s |
| **Micronaut** | Yes | ~50ms (JVM) | ~80MB | IoT, minimal footprint |
| **Helidon** | Yes | ~50ms (JVM) | ~70MB | Oracle cloud, MicroProfile |
| **Vert.x** | Yes | ~10ms | ~30MB | Reactive, event-driven, high throughput |

---

## 1. Quarkus

Quarkus is designed for GraalVM Native Image from the start — "Kubernetes-native Java".

### Quick Start

```bash
# Create a new Quarkus project
mvn io.quarkus.platform:quarkus-maven-plugin:3.9.0:create \
    -DprojectGroupId=com.example \
    -DprojectArtifactId=my-service \
    -Dextensions="rest,hibernate-orm-panache,jdbc-postgresql"

cd my-service

# Dev mode (live reload)
./mvnw quarkus:dev

# Build native executable (requires GraalVM)
./mvnw package -Pnative

# Run native — startup < 50ms
./target/my-service-1.0-runner
```

### REST Resource (JAX-RS annotations)

```java
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject UserService service;

    @GET
    public List<User> list() { return service.findAll(); }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return service.findById(id)
            .map(Response::ok)
            .orElse(Response.status(404))
            .build();
    }

    @POST
    public Response create(User user) {
        service.create(user);
        return Response.status(201).entity(user).build();
    }
}
```

### Panache (simplified JPA)

```java
import io.quarkus.hibernate.orm.panache.*;

@Entity
public class User extends PanacheEntity {    // auto id + helper methods
    public String name;
    public String email;

    // Custom queries as static methods
    public static List<User> findByName(String name) {
        return list("name", name);
    }

    public static Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}

// In resource — no repository boilerplate
User.findByEmail("alice@example.com")
    .ifPresent(u -> System.out.println(u.name));
```

---

## 2. Micronaut

Micronaut uses compile-time dependency injection (via annotation processing) — no reflection at runtime.

```bash
# Create project
mn create-app com.example.myapp --features=graalvm,data-jdbc,postgres

# Run
./mvnw mn:run
```

```java
// No Spring-style magic — DI resolved at compile time
@Singleton
public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository repo) { this.repo = repo; }

    public Optional<User> findById(Long id) { return repo.findById(id); }
}

@Controller("/users")
public class UserController {
    @Inject UserService service;

    @Get("/{id}")
    public Optional<User> get(Long id) { return service.findById(id); }
}
```

**Key difference from Spring:** No reflection-based bean creation → faster startup AND native-image-friendly without extra config.

---

## 3. Jakarta EE (formerly Java EE)

Jakarta EE is the community-driven successor to Java EE. Key specs:

| Spec | Purpose | Annotation |
|------|---------|-----------|
| **CDI** | Dependency injection | `@Inject`, `@ApplicationScoped` |
| **JAX-RS** | REST APIs | `@Path`, `@GET`, `@POST` |
| **JPA** | Database ORM | `@Entity`, `@NamedQuery` |
| **EJB** | Enterprise beans (legacy) | `@Stateless`, `@MessageDriven` |
| **JMS** | Messaging | `@MessageDriven` |
| **Security** | Auth | `@RolesAllowed`, `@PermitAll` |
| **Bean Validation** | Input validation | `@NotNull`, `@Size`, `@Email` |

```java
// CDI bean
@ApplicationScoped
public class OrderService {
    @Inject EntityManager em;
    @Inject Event<OrderCreated> events;

    @Transactional
    public Order create(Order order) {
        em.persist(order);
        events.fire(new OrderCreated(order));
        return order;
    }
}
```

**Server options:** WildFly, Payara, Open Liberty, TomEE.

---

## 4. Comparing Dependency Injection

```java
// Spring Boot
@Service
public class UserService { ... }

@Autowired                     // field injection (not recommended)
private UserService service;

// Quarkus (CDI)
@ApplicationScoped
public class UserService { ... }

@Inject
UserService service;

// Micronaut (compile-time)
@Singleton
public class UserService { ... }

// Constructor injection (preferred in all frameworks)
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }
}
```

---

## 5. Microservice Patterns in Java

### Service Discovery

```yaml
# Spring Boot with Eureka
spring:
  application:
    name: user-service
  cloud:
    discovery:
      enabled: true

eureka:
  client:
    serviceUrl:
      defaultZone: http://eureka:8761/eureka
```

```java
// Call service by name, not URL
@LoadBalanced
@Bean
RestTemplate restTemplate() { return new RestTemplate(); }

// In service
String result = restTemplate.getForObject("http://order-service/orders", String.class);
```

### Circuit Breaker (Resilience4j)

```java
@CircuitBreaker(name = "orderService", fallbackMethod = "fallbackOrders")
@Retry(name = "orderService")
@TimeLimiter(name = "orderService")
public CompletableFuture<List<Order>> getOrders(Long userId) {
    return CompletableFuture.supplyAsync(() ->
        restTemplate.getForObject("http://order-service/orders/" + userId, List.class)
    );
}

public CompletableFuture<List<Order>> fallbackOrders(Long userId, Throwable ex) {
    log.warn("Order service down, returning empty: {}", ex.getMessage());
    return CompletableFuture.completedFuture(List.of());
}
```

### API Gateway Pattern

```yaml
# Spring Cloud Gateway
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service          # load-balanced via Eureka
          predicates:
            - Path=/api/users/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: userCB
                fallbackUri: forward:/fallback/users
```

---

## MicroProfile (Jakarta EE for Microservices)

MicroProfile is a set of portable microservice specs on top of Jakarta EE:

```java
// MicroProfile Config — externalized configuration
@Inject
@ConfigProperty(name = "app.timeout", defaultValue = "30")
int timeout;

// MicroProfile Health
@Liveness
@ApplicationScoped
public class DatabaseLiveness implements HealthCheck {
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("database")
            .up()
            .withData("connection", "ok")
            .build();
    }
}

// MicroProfile JWT
@GET
@RolesAllowed("admin")
@Path("/admin")
public Response adminOnly(@Context SecurityContext ctx) {
    Principal user = ctx.getUserPrincipal();
    return Response.ok("Hello admin: " + user.getName()).build();
}
```

---

## Interview Questions

1. What makes Quarkus "Kubernetes-native"? → Designed for native image from start (fast start, small memory), live reload in dev, container-optimized defaults, first-class Kubernetes operator support.
2. How does Micronaut's DI differ from Spring's? → Micronaut processes annotations at compile time via APT, avoiding reflection at runtime. Spring resolves DI at runtime via reflection (requires native-image config). Micronaut is native-image-friendly by default.
3. What is MicroProfile? → A community specification (Eclipse Foundation) that layers microservice-specific APIs (Config, Health, Metrics, OpenAPI, JWT, Fault Tolerance) on top of Jakarta EE.
4. When would you choose Vert.x over Spring Boot? → When you need the absolute highest throughput with reactive programming — Vert.x uses a small event loop (like Node.js) and can handle 100K+ RPS on minimal hardware.
5. CDI vs Spring IoC? → Both are DI containers; CDI is the Jakarta EE standard (portable across WildFly, Payara, Quarkus). Spring IoC is Spring-specific but has a much larger ecosystem.

---

**Next →** [51 gRPC in Java](51-gRPCInJava.md)
