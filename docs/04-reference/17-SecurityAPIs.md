# Java Security APIs Reference

**Back to Index:** [04-reference/00-INDEX.md](00-INDEX.md)

---

## Java Cryptography Architecture (JCA)

### Algorithm Names Quick Reference

| Purpose | Algorithm string | Notes |
|---------|-----------------|-------|
| Hash | `SHA-256`, `SHA-512`, `SHA3-256` | Don't use MD5/SHA-1 for security |
| Password hash | Use BCrypt, Argon2 (external libs) | Never use any SHA for passwords |
| MAC | `HmacSHA256`, `HmacSHA512` | Integrity + authenticity |
| Symmetric cipher | `AES/GCM/NoPadding` | Authenticated encryption |
| Asymmetric cipher | `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` | OAEP, not PKCS1 |
| Key agreement | `ECDH`, `X25519` | Perfect Forward Secrecy |
| Signature | `SHA256withRSA`, `SHA256withECDSA`, `Ed25519` | |
| PQ-Crypto (Java 25+) | `ML-KEM-768`, `ML-DSA-65` | NIST post-quantum standards |
| Key generation | `AES`, `RSA`, `EC`, `X25519`, `Ed25519` | |
| RNG | `SecureRandom` | Never `new Random()` for security |

---

## MessageDigest (Hashing)

```java
import java.security.MessageDigest;
import java.util.HexFormat;

// Basic SHA-256
MessageDigest md = MessageDigest.getInstance("SHA-256");
byte[] hash = md.digest("input".getBytes(StandardCharsets.UTF_8));
String hex = HexFormat.of().formatHex(hash);

// Incremental update
md.reset();
md.update(part1);
md.update(part2);
byte[] result = md.digest();

// SHA-3 (Keccak)
MessageDigest sha3 = MessageDigest.getInstance("SHA3-256");
```

---

## HMAC (Message Authentication Code)

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

byte[] keyBytes = "my-secret-key-32-bytes!!!!!!!!!".getBytes();
SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");

Mac mac = Mac.getInstance("HmacSHA256");
mac.init(key);
byte[] hmac = mac.doFinal("message".getBytes(StandardCharsets.UTF_8));
String hex = HexFormat.of().formatHex(hmac);
```

---

## Cipher — Symmetric Encryption

### AES/GCM (Authenticated Encryption — recommended)

```java
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;

// Key generation
KeyGenerator kg = KeyGenerator.getInstance("AES");
kg.init(256, new SecureRandom());
SecretKey key = kg.generateKey();

// Encrypt
byte[] iv = new byte[12];   // GCM: 12-byte IV
new SecureRandom().nextBytes(iv);
GCMParameterSpec spec = new GCMParameterSpec(128, iv);  // 128-bit auth tag

Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, spec);

// Optional: Additional Authenticated Data (AAD) — not encrypted but integrity-protected
cipher.updateAAD("header-data".getBytes());

byte[] ciphertext = cipher.doFinal("plaintext".getBytes(StandardCharsets.UTF_8));
// ciphertext includes the 16-byte GCM authentication tag at the end

// Decrypt
Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
dec.init(Cipher.DECRYPT_MODE, key, spec);
dec.updateAAD("header-data".getBytes());
byte[] plaintext = dec.doFinal(ciphertext);

// Encode key for storage
String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
SecretKey restoredKey = new SecretKeySpec(Base64.getDecoder().decode(encodedKey), "AES");
```

### AES/CBC (Legacy — avoid for new code)
```java
// CBC doesn't provide authenticity — add separate HMAC if using CBC
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
IvParameterSpec ivSpec = new IvParameterSpec(iv);   // 16-byte IV for AES
cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
```

---

## KeyPair — Asymmetric Cryptography

```java
// RSA key pair
KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
kpg.initialize(2048);   // 2048 minimum; 4096 for high security
KeyPair pair = kpg.generateKeyPair();
PublicKey pub   = pair.getPublic();
PrivateKey priv = pair.getPrivate();

// EC key pair (smaller keys, same security level)
kpg = KeyPairGenerator.getInstance("EC");
kpg.initialize(new ECGenParameterSpec("secp256r1"));  // P-256
pair = kpg.generateKeyPair();

// Ed25519 (modern EdDSA — recommended for signatures)
kpg = KeyPairGenerator.getInstance("Ed25519");
pair = kpg.generateKeyPair();
```

---

## Signature (Digital Signatures)

```java
// Sign
Signature signer = Signature.getInstance("SHA256withRSA");
signer.initSign(privateKey);
signer.update(data);
byte[] signature = signer.sign();

// Verify
Signature verifier = Signature.getInstance("SHA256withRSA");
verifier.initVerify(publicKey);
verifier.update(data);
boolean valid = verifier.verify(signature);

// Ed25519 (more concise, faster)
Signature ed = Signature.getInstance("Ed25519");
ed.initSign(edPrivateKey);
ed.update(data);
byte[] edSig = ed.sign();
```

---

## KeyStore

```java
// Load PKCS12 keystore
KeyStore ks = KeyStore.getInstance("PKCS12");
try (InputStream is = new FileInputStream("keystore.p12")) {
    ks.load(is, "password".toCharArray());
}

// List aliases
ks.aliases().asIterator().forEachRemaining(System.out::println);

// Get private key and certificate chain
Key key  = ks.getKey("my-alias", "key-password".toCharArray());
Certificate[] chain = ks.getCertificateChain("my-alias");

// Store a key
ks.setKeyEntry("new-key", secretKey, "key-pw".toCharArray(), null);
try (OutputStream os = new FileOutputStream("keystore.p12")) {
    ks.store(os, "store-password".toCharArray());
}

// Create empty keystore
KeyStore newKs = KeyStore.getInstance("PKCS12");
newKs.load(null, null);
```

---

## TrustManager & SSLContext

```java
import javax.net.ssl.*;

// Custom TrustManager for mutual TLS
KeyStore trustStore = KeyStore.getInstance("PKCS12");
trustStore.load(new FileInputStream("truststore.p12"), "password".toCharArray());

TrustManagerFactory tmf = TrustManagerFactory
    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);

// Custom KeyManager (client certificate)
KeyManagerFactory kmf = KeyManagerFactory
    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
kmf.init(clientKeyStore, "key-password".toCharArray());

SSLContext ctx = SSLContext.getInstance("TLSv1.3");
ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

// Use with HttpClient
HttpClient client = HttpClient.newBuilder()
    .sslContext(ctx)
    .sslParameters(sslParams())
    .build();

private SSLParameters sslParams() {
    SSLParameters p = new SSLParameters();
    p.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
    p.setCipherSuites(new String[]{
        "TLS_AES_256_GCM_SHA384",           // TLS 1.3
        "TLS_AES_128_GCM_SHA256"
    });
    p.setEndpointIdentificationAlgorithm("HTTPS");  // verify hostname
    return p;
}
```

---

## SecureRandom

```java
// Use SecureRandom for all security-sensitive random values
SecureRandom rng = new SecureRandom();                // auto-selects best algorithm

// Generate random bytes (CSRF token, session ID, etc.)
byte[] token = new byte[32];
rng.nextBytes(token);
String tokenHex = HexFormat.of().formatHex(token);

// Strong instance (blocks until sufficient entropy available)
SecureRandom strong = SecureRandom.getInstanceStrong();

// Seeded (deterministic — for testing only!)
SecureRandom seeded = SecureRandom.getInstance("SHA1PRNG");
seeded.setSeed(12345L);    // NEVER in production
```

---

## Password Encoding (via Spring Security)

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

// BCrypt (default, widely used)
BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);  // strength 10-14
String hash = bcrypt.encode("my-password");
boolean matches = bcrypt.matches("my-password", hash);

// Argon2id (strongest, recommended for new systems)
Argon2PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
String hash2 = argon2.encode("my-password");
```

---

## JWT Libraries

| Library | Dependency | Notes |
|---------|-----------|-------|
| **java-jwt** (Auth0) | `com.auth0:java-jwt:4.4.0` | Simple, widely used |
| **JJWT** (io.jsonwebtoken) | `io.jsonwebtoken:jjwt-api:0.12.3` | Full-featured |
| **Nimbus JOSE+JWT** | `com.nimbusds:nimbus-jose-jwt:9.40` | Standards-focused, FAPI-certified |
| **Spring Security OAuth2** | Spring Boot starter | Integrated, uses Nimbus under the hood |

```java
// JJWT — sign with RSA
String token = Jwts.builder()
    .subject("user123")
    .claim("role", "admin")
    .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
    .signWith(rsaPrivateKey, Jwts.SIG.RS256)
    .compact();

// Verify
Claims claims = Jwts.parser()
    .verifyWith(rsaPublicKey)
    .build()
    .parseSignedClaims(token)
    .getPayload();
String subject = claims.getSubject();
```

---

## jdk.crypto.* Providers & Algorithms

```java
// List all providers and algorithms
for (Provider p : Security.getProviders()) {
    System.out.println(p.getName() + " " + p.getVersionStr());
    p.keySet().forEach(k -> System.out.println("  " + k));
}

// Check if an algorithm is available
try {
    Cipher.getInstance("AES/GCM/NoPadding");
    System.out.println("AES-GCM available");
} catch (NoSuchAlgorithmException e) {
    System.out.println("Not available: " + e.getMessage());
}
```

---

## JCA Security Properties (`java.security`)

Key properties in `$JAVA_HOME/conf/security/java.security`:

```properties
# Disable weak algorithms
jdk.tls.disabledAlgorithms=SSLv3, TLSv1, TLSv1.1, RC4, MD5withRSA, \
    DH keySize < 1024, EC keySize < 224

# Minimum RSA key size
jdk.certpath.disabledAlgorithms=MD2, MD5, SHA1 jdkCA & usage TLSServer, \
    RSA keySize < 2048

# Restrict TLS cipher suites system-wide
```

Override at runtime (carefully):
```java
Security.setProperty("jdk.tls.disabledAlgorithms", "");  // enable all — for testing only
```

---

**Learn more:** [`46-SecurityJava.md`](../02-learn/46-SecurityJava.md)
