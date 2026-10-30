# 51 — gRPC in Java

**Previous:** [50 Jakarta EE & Microservices](50-JakartaEEMicroservices.md) · **Next:** [52 Kafka Client](52-KafkaClient.md)

---

## What is gRPC?

gRPC (Google Remote Procedure Call) is a high-performance RPC framework using:
- **Protobuf** (Protocol Buffers) for binary serialization — 5–10× smaller than JSON
- **HTTP/2** for multiplexed, bi-directional streaming
- **Code generation** from `.proto` schema files

---

## 1. Define the Service (`.proto` file)

```protobuf
// src/main/proto/user.proto
syntax = "proto3";
package com.example;
option java_package = "com.example.grpc";

message UserRequest {
    int64 id = 1;
}

message UserResponse {
    int64 id = 1;
    string name = 2;
    string email = 3;
}

message UserFilter {
    string role = 1;
}

service UserService {
    // Unary — one request, one response
    rpc GetUser(UserRequest) returns (UserResponse);

    // Server-side streaming — one request, many responses
    rpc StreamUsers(UserFilter) returns (stream UserResponse);

    // Client-side streaming — many requests, one response
    rpc BatchCreate(stream UserResponse) returns (UserResponse);

    // Bi-directional streaming
    rpc Chat(stream UserRequest) returns (stream UserResponse);
}
```

---

## 2. Maven Setup

```xml
<dependencies>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-netty-shaded</artifactId>
        <version>1.62.2</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-protobuf</artifactId>
        <version>1.62.2</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-stub</artifactId>
        <version>1.62.2</version>
    </dependency>
</dependencies>

<build>
  <extensions>
    <extension>
      <groupId>kr.motd.maven</groupId>
      <artifactId>os-maven-plugin</artifactId>
      <version>1.7.1</version>
    </extension>
  </extensions>
  <plugins>
    <plugin>
      <groupId>org.xolstice.maven.plugins</groupId>
      <artifactId>protobuf-maven-plugin</artifactId>
      <version>0.6.1</version>
      <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.25.1:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.62.2:exe:${os.detected.classifier}</pluginArtifact>
      </configuration>
      <executions>
        <execution>
          <goals><goal>compile</goal><goal>compile-custom</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

After running `mvn compile`, generated classes appear in `target/generated-sources/`.

---

## 3. Server Implementation

```java
import io.grpc.*;
import io.grpc.stub.StreamObserver;

// Extend the generated base class
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {

    @Override
    public void getUser(UserRequest req, StreamObserver<UserResponse> obs) {
        // Unary RPC
        User user = userRepository.findById(req.getId())
            .orElseThrow(() -> Status.NOT_FOUND
                .withDescription("User " + req.getId() + " not found")
                .asRuntimeException());

        UserResponse response = UserResponse.newBuilder()
            .setId(user.getId())
            .setName(user.getName())
            .setEmail(user.getEmail())
            .build();

        obs.onNext(response);     // send the single result
        obs.onCompleted();        // signal done
    }

    @Override
    public void streamUsers(UserFilter filter, StreamObserver<UserResponse> obs) {
        // Server streaming — send many responses
        userRepository.findByRole(filter.getRole()).forEach(user -> {
            obs.onNext(UserResponse.newBuilder()
                .setId(user.getId())
                .setName(user.getName())
                .build());
        });
        obs.onCompleted();
    }
}

// Start the server
public class GrpcServer {
    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(9090)
            .addService(new UserServiceImpl())
            .addService(ProtoReflectionService.newInstance())  // optional: reflection for tools
            .build()
            .start();

        System.out.println("gRPC server started on port 9090");
        server.awaitTermination();
    }
}
```

---

## 4. Client

```java
// Create a channel (connection)
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9090)
    .usePlaintext()   // no TLS — only for dev
    .build();

// Blocking stub (synchronous)
UserServiceGrpc.UserServiceBlockingStub stub =
    UserServiceGrpc.newBlockingStub(channel);

// Unary call
UserResponse user = stub.getUser(
    UserRequest.newBuilder().setId(42).build()
);
System.out.println("Got user: " + user.getName());

// Server streaming
stub.streamUsers(UserFilter.newBuilder().setRole("admin").build())
    .forEachRemaining(u -> System.out.println("  " + u.getName()));

// Async stub (non-blocking)
UserServiceGrpc.UserServiceStub asyncStub = UserServiceGrpc.newStub(channel);
asyncStub.getUser(UserRequest.newBuilder().setId(1).build(),
    new StreamObserver<>() {
        public void onNext(UserResponse r)   { System.out.println(r.getName()); }
        public void onError(Throwable t)     { t.printStackTrace(); }
        public void onCompleted()            { channel.shutdown(); }
    });

// Always close channel when done
channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
```

---

## 5. gRPC with Spring Boot (`grpc-spring-boot-starter`)

```xml
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-server-spring-boot-starter</artifactId>
    <version>3.1.0</version>
</dependency>
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-client-spring-boot-starter</artifactId>
    <version>3.1.0</version>
</dependency>
```

```java
// Server
@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    @Autowired UserRepository repo;

    @Override
    public void getUser(UserRequest req, StreamObserver<UserResponse> obs) {
        repo.findById(req.getId()).ifPresentOrElse(
            u -> { obs.onNext(toProto(u)); obs.onCompleted(); },
            () -> obs.onError(Status.NOT_FOUND.asRuntimeException())
        );
    }
}

// Client
@Component
public class UserClient {
    @GrpcClient("user-service")   // configured in application.yml
    private UserServiceGrpc.UserServiceBlockingStub stub;

    public UserResponse getUser(long id) {
        return stub.getUser(UserRequest.newBuilder().setId(id).build());
    }
}
```

```yaml
# application.yml
grpc:
  server:
    port: 9090
  client:
    user-service:
      address: static://localhost:9090
      negotiationType: plaintext
```

---

## 6. Error Handling

```java
// Server side — use Status codes
throw Status.INVALID_ARGUMENT
    .withDescription("id must be positive")
    .augmentDescription("got: " + id)
    .asRuntimeException();

throw Status.UNAUTHENTICATED
    .withDescription("JWT token missing or invalid")
    .asRuntimeException();

// Client side — catch StatusRuntimeException
try {
    UserResponse user = stub.getUser(req);
} catch (StatusRuntimeException e) {
    switch (e.getStatus().getCode()) {
        case NOT_FOUND     -> System.out.println("User not found");
        case UNAUTHENTICATED -> System.out.println("Login required");
        case UNAVAILABLE   -> System.out.println("Service down");
        default            -> throw e;
    }
}
```

**gRPC Status codes:** `OK`, `CANCELLED`, `UNKNOWN`, `INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS`, `PERMISSION_DENIED`, `UNAUTHENTICATED`, `RESOURCE_EXHAUSTED`, `UNAVAILABLE`.

---

## 7. REST vs gRPC — When to Choose

| | REST/HTTP | gRPC |
|-|-----------|------|
| Protocol | HTTP/1.1 or 2 | HTTP/2 |
| Format | JSON (text, human-readable) | Protobuf (binary, compact) |
| Schema | OpenAPI (optional) | `.proto` (required, enforced) |
| Browser | Native | Needs grpc-web proxy |
| Streaming | SSE/WebSocket (workarounds) | Native 4 modes |
| Performance | Good | 5–10× faster serialization |
| Ecosystem | Huge | Smaller but growing |
| Best for | Public APIs, browser clients | Internal microservices |

---

## Interview Questions

1. What serialization format does gRPC use? → Protocol Buffers (protobuf) — binary, schema-enforced, 3–10× more compact than JSON.
2. What HTTP version does gRPC require? → HTTP/2 — for header compression, multiplexing, and bi-directional streaming.
3. What are the 4 gRPC streaming modes? → Unary (1:1), server streaming (1:N), client streaming (N:1), bi-directional streaming (N:N).
4. How do you handle errors in gRPC? → Throw `Status.CODE.asRuntimeException()` on server; catch `StatusRuntimeException` and check `.getStatus().getCode()` on client.
5. gRPC vs REST for internal microservices? → gRPC is preferred: strongly typed contracts, faster binary protocol, streaming support. REST is preferred for public APIs (browser-accessible, no protobuf tooling needed).

---

**Next →** [52 Kafka Client](52-KafkaClient.md)
