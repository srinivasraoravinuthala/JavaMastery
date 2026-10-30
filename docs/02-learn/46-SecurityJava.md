# 46 — Security in Java

**Previous:** [45 Reactive Java](45-ReactiveJava.md) · **Next:** [47 Cloud-Native Java](47-CloudNativeJava.md)

▶️ `pkg1core/core30SecurityDemo.java`

---

## Java Security Layers

```
Application layer   ← JWT, OAuth2, input validation, OWASP Top 10
Framework layer     ← Spring Security, Jakarta Security
JDK / JCA layer     ← Cipher, MessageDigest, KeyStore, JSSE
OS / Network layer  ← TLS, firewall, container isolation
```

---

## 1. Cryptography (JCA — Java Cryptography Architecture)

### Hashing
```java
import java.security.MessageDigest;
import java.util.HexFormat;

byte[] hash = MessageDigest.getInstance("SHA-256")
    .digest("hello".getBytes(StandardCharsets.UTF_8));
System.out.println(HexFormat.of().formatHex(hash));

// For passwords: use BCrypt / Argon2 — NOT plain SHA-256
// Example with Spring Security's BCryptPasswordEncoder:
// new BCryptPasswordEncoder().encode(rawPassword)
```

**Algorithm guide:**
| Purpose | Algorithm |
|---------|-----------|
| General hashing | SHA-256, SHA-3-256 |
| Password storage | BCrypt, Argon2, scrypt (NOT MD5/SHA1) |
| MAC (integrity) | HMAC-SHA256 |
| Symmetric encryption | AES/GCM/NoPadding (256-bit key) |
| Asymmetric encryption | RSA-OAEP, ECDSA |
| Key exchange | ECDH, X25519 |
| Quantum-resistant (Java 25+) | ML-KEM, ML-DSA (JEP 496/497) |

### Symmetric Encryption (AES-GCM)
```java
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;

// Generate key
KeyGenerator kg = KeyGenerator.getInstance("AES");
kg.init(256);
SecretKey key = kg.generateKey();

// Encrypt
byte[] iv = new byte[12];
new SecureRandom().nextBytes(iv);
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
byte[] ciphertext = cipher.doFinal("secret message".getBytes());

// Decrypt
cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
byte[] plaintext = cipher.doFinal(ciphertext);
System.out.println(new String(plaintext));
```

### KeyStore (Certificate & Key Management)
```java
KeyStore ks = KeyStore.getInstance("PKCS12");
ks.load(new FileInputStream("keystore.p12"), "password".toCharArray());

// Retrieve key and certificate
Key key = ks.getKey("mykey", "password".toCharArray());
Certificate cert = ks.getCertificate("mykey");
```

---

## 2. TLS / HTTPS (JSSE)

```java
import javax.net.ssl.*;
import java.security.KeyStore;

// Custom TrustManager (e.g., mutual TLS)
KeyStore trustStore = KeyStore.getInstance("PKCS12");
trustStore.load(new FileInputStream("truststore.p12"), "password".toCharArray());

TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);

SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

// Use with HttpClient (Java 11+)
HttpClient client = HttpClient.newBuilder()
    .sslContext(sslContext)
    .build();
```

**TLS best practices:**
- Minimum TLS 1.2; prefer TLS 1.3
- Disable SSLv3, TLS 1.0, 1.1 in `jdk.tls.disabledAlgorithms`
- Pin certificates in high-security scenarios
- Use `HTTPS` for all connections; never `http://` in production

---

## 3. JWT (JSON Web Tokens)

JWT = three Base64-encoded parts: `header.payload.signature`

```java
// Using java-jwt (Auth0 library)
// Maven: com.auth0:java-jwt:4.4.0

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

Algorithm alg = Algorithm.HMAC256("super-secret-key-min-32-chars!!");

// Sign
String token = JWT.create()
    .withSubject("user123")
    .withClaim("role", "admin")
    .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
    .sign(alg);

// Verify
DecodedJWT decoded = JWT.require(alg)
    .build()
    .verify(token);

System.out.println(decoded.getSubject());           // user123
System.out.println(decoded.getClaim("role").asString()); // admin
```

**JWT best practices:**
- Use RS256/ES256 for production (asymmetric — private key signs, public key verifies)
- Set short expiry + refresh token pattern
- Never store sensitive data in payload (it's only Base64-encoded, not encrypted)
- Validate `exp`, `iss`, `aud` claims

---

## 4. OAuth2 / OIDC with Spring Security

```java
// application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
        provider:
          google:
            issuer-uri: https://accounts.google.com
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(Customizer.withDefaults())   // Google/GitHub login
            .oauth2ResourceServer(r -> r.jwt(Customizer.withDefaults()))  // JWT validation
            .build();
    }
}
```

---

## 5. OWASP Top 10 — Java Mitigations

| OWASP Risk | Java Mitigation |
|-----------|-----------------|
| **A01 Broken Access Control** | Spring Security `@PreAuthorize`, method security |
| **A02 Cryptographic Failures** | Use AES-GCM, BCrypt; never MD5/SHA1 for passwords |
| **A03 Injection (SQL, etc.)** | Parameterized queries (`PreparedStatement`), JPA with bind params |
| **A04 Insecure Design** | Threat modeling, defense-in-depth |
| **A05 Security Misconfiguration** | Disable actuator endpoints in prod; HTTPS-only |
| **A06 Vulnerable Components** | Regularly update dependencies; use OWASP Dependency-Check |
| **A07 Auth Failures** | MFA, account lockout, secure session management |
| **A08 Software Integrity Failures** | Signed artifacts, verify checksums |
| **A09 Logging Failures** | Log security events; never log passwords/tokens |
| **A10 SSRF** | Validate/whitelist outbound URLs; block private IP ranges |

---

## 6. Secure Coding Patterns

```java
// SQL Injection Prevention — always use PreparedStatement
PreparedStatement ps = conn.prepareStatement(
    "SELECT * FROM users WHERE email = ?");
ps.setString(1, userInput);  // parameterized, never concatenate

// Input validation
if (!userInput.matches("[a-zA-Z0-9_]{3,50}")) {
    throw new IllegalArgumentException("Invalid username");
}

// Secure random numbers
SecureRandom random = new SecureRandom();
byte[] token = new byte[32];
random.nextBytes(token);  // use for CSRF tokens, password resets

// Never use Math.random() or new Random() for security
```

---

## Interview Questions

1. Why not use SHA-256 for password storage? → SHA-256 is fast — attackers can compute billions per second. BCrypt/Argon2 are intentionally slow and salted.
2. What is AES-GCM? → Authenticated encryption — provides both confidentiality (AES) and integrity/authentication (GCM tag). Detects tampering.
3. JWT structure? → `base64(header).base64(payload).signature` — payload readable without key; signature validates authenticity.
4. HMAC vs digital signature for JWT? → HMAC (HS256) uses a shared secret — signer = verifier; Digital sig (RS256) uses private key to sign, public key to verify — parties can be separate.
5. What is SSRF? → Server-Side Request Forgery — attacker tricks server into making requests to internal resources (e.g., AWS metadata endpoint).

---

**Next →** [47 Cloud-Native Java](47-CloudNativeJava.md)
