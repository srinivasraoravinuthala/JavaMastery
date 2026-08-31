# 40 — Spring Boot Intro

**Previous:** [39 Resilience Patterns](39-ResiliencePatterns.md)

▶️ See [15 Spring Boot Interview](../03-interview/15-SpringBoot.md) for 80+ Q&A

---

## What is Spring Boot?

Spring Boot is the **opinionated** way to build production Java apps — auto-configuration, embedded Tomcat, and a vast ecosystem (Spring Data, Security, Cloud).

| Concept | What it does |
|---------|--------------|
| `@SpringBootApplication` | Enables auto-config + component scan |
| `@RestController` | REST endpoints with `@GetMapping`, etc. |
| `@Autowired` / constructor injection | Dependency injection |
| `application.properties` | Externalized configuration |
| Spring Data JPA | Repository abstraction over Hibernate |

---

## Minimal REST controller (conceptual)

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return service.findById(id);
    }
}
```

---

## Learning path for Spring

1. Solid **OOP + JDBC + REST** (chapters 10–31) — you have runnable examples
2. Read [15 Spring Boot Interview](../03-interview/15-SpringBoot.md) — 80+ questions with answers
3. Build a small project: Spring Initializr → REST + JPA + H2
4. Official docs: [spring.io/guides](https://spring.io/guides)

> No dedicated `pkg21spring` yet — use Spring Initializr for hands-on labs. Interview content covers DI, Boot auto-config, Actuator, and testing.

**Full interview topic →** [15 Spring Boot](../03-interview/15-SpringBoot.md)

**Career guide →** [06-career/02-InterviewGuide.md](../06-career/02-InterviewGuide.md)

**Back to index →** [00-INDEX](00-INDEX.md)
