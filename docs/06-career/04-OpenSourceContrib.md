# Open Source Contribution & Java Portfolio

**Back to:** [Career Index](00-INDEX.md)

---

## Why Contribute to Open Source

- **Visibility** — GitHub profile as living portfolio
- **Learning** — read production-quality code from top engineers
- **Network** — meet principal engineers and project leads
- **Career** — many top Java companies (Red Hat, Pivotal, JetBrains, Oracle) hire from their OSS communities
- **Credibility** — merged PRs trump side projects on a résumé

---

## 1. Contributing to OpenJDK

OpenJDK is the reference implementation of Java — contributing here is the highest-prestige Java contribution.

### Getting Started

```bash
# Clone the JDK source
git clone https://github.com/openjdk/jdk.git
cd jdk

# Build (requires build tools — see doc/building.md)
bash configure
make images

# Run tests
make test TEST="test/jdk/java/util/..."
```

**Good first contribution types:**
- Fix a bug listed on [bugs.openjdk.org](https://bugs.openjdk.org) with label "good-first-bug"
- Improve test coverage for existing APIs
- Javadoc improvements
- Performance micro-benchmarks (JMH)

**Process:**
1. Sign [OCA (Oracle Contributor Agreement)](https://oca.opensource.oracle.com/)
2. Find a bug / enhancement in JDK bug tracker
3. Submit patch to [OpenJDK Mailing Lists](https://mail.openjdk.org/) or via PR on GitHub
4. Get reviewed by a Reviewer/Committer

---

## 2. Contributing to Spring

Spring Framework, Spring Boot, and Spring Security are among the most impactful Java projects.

```bash
# Fork and clone
git clone https://github.com/spring-projects/spring-framework.git

# Build
./gradlew build

# Run tests
./gradlew test
```

**Good entry points:**
- [Spring Boot issues labeled `status: waiting-for-triage`](https://github.com/spring-projects/spring-boot/issues)
- Documentation improvements (spring.io docs are in the same repo)
- Sample application contributions

**Contribution guide:** https://github.com/spring-projects/spring-framework/blob/main/CONTRIBUTING.md

---

## 3. Contributing to Apache Projects

Apache Software Foundation hosts many core Java libraries:

| Project | What it is | GitHub |
|---------|-----------|--------|
| **Apache Kafka** | Distributed messaging | github.com/apache/kafka |
| **Apache Maven** | Build tool | github.com/apache/maven |
| **Apache Commons** | Utility libraries | github.com/apache/commons-lang |
| **Apache HttpComponents** | HTTP client | github.com/apache/httpcomponents-client |
| **Apache Camel** | Integration framework | github.com/apache/camel |

**Process:**
1. Sign Apache CLA
2. Join dev mailing list (dev@kafka.apache.org etc.)
3. Pick a JIRA ticket marked "newbie" or "minor"
4. Submit PR and engage with committers

---

## 4. Building a Java Portfolio

### Projects That Stand Out

**Tier 1 — Demonstrates breadth:**
```
REST API with:
  - Spring Boot 3 + Java 21
  - Virtual threads (spring.threads.virtual.enabled=true)
  - PostgreSQL + R2DBC or JPA
  - Spring Security + JWT
  - Docker + docker-compose
  - OpenAPI/Swagger documentation
  - GH Actions CI with JUnit tests
```

**Tier 2 — Demonstrates depth:**
```
Distributed system:
  - Multiple services (user, order, inventory)
  - Kafka for async events
  - Outbox pattern
  - Distributed tracing with OTel + Jaeger
  - Kubernetes deployment manifests
```

**Tier 3 — Demonstrates expertise:**
```
Performance-critical service:
  - GraalVM native binary
  - JFR profiling screenshots showing optimization
  - Benchmarks before/after (JMH)
  - Custom Spring Boot starter
  - Published to Maven Central
```

---

## 5. GitHub Profile Tips

### Profile README

Create `github.com/<username>/<username>/README.md` — shown on your profile:

```markdown
## Java Engineer | 7+ years | Spring Boot · Kafka · GraalVM

Currently working on: [JavaMastery](https://github.com/...)

Top skills:
- Java 21/25 (virtual threads, pattern matching, records)
- Spring Boot 3 + cloud-native deployment
- Apache Kafka event-driven architectures
- GraalVM Native Image optimization

📝 [Blog](https://yourblog.dev) · 💼 [LinkedIn](https://linkedin.com/in/...)
```

### Contribution Activity

- Contribute consistently (even small docs/test improvements count)
- Participate in issues — ask good questions and help others
- Star and watch repos you want to contribute to eventually

---

## 6. JavaMastery as Your Portfolio

This repository itself demonstrates:
- Deep Java knowledge (48+ chapters, 50+ interview questions)
- Code examples for every concept (pkg1core through pkg21spring)
- Modern Java (Java 22–25 demo files)
- Documentation skills

**Enhance it further:**
- Add your own examples to the packages
- Open PRs to fix issues in the docs
- Write a blog post referencing the learning path
- Create YouTube/blog content using the code examples

---

## 7. Publish to Maven Central

Publishing your own library to Maven Central is a significant portfolio item:

```bash
# Requirements: Sonatype OSSRH account + GPG key + POM metadata

# pom.xml requirements
<groupId>com.yourhandle</groupId>
<artifactId>your-library</artifactId>
<version>1.0.0</version>

# Required elements: name, description, url, licenses, developers, scm

# Deploy
mvn clean deploy -Prelease
# Then promote on Sonatype OSSRH → syncs to Maven Central in 2-4 hours
```

---

**Related →** [Career Roadmap](01-Roadmap.md) · [Salary & Roles](03-SalaryAndRoles.md)
