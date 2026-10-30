package pkg1core;

/*
 * core30SecurityDemo.java
 * -----------------------
 * Demonstrates core Java security APIs:
 *   - SHA-256 hashing (MessageDigest)
 *   - AES/GCM authenticated encryption/decryption
 *   - HMAC-SHA256 (message authentication code)
 *   - SecureRandom token generation
 *
 * Compile: javac pkg1core/core30SecurityDemo.java
 * Run:     java  pkg1core.core30SecurityDemo
 */

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

public class core30SecurityDemo {

    public static void main(String[] args) throws Exception {
        hashingDemo();
        aesGcmDemo();
        hmacDemo();
        secureRandomDemo();
    }

    // ─── 1. Hashing ──────────────────────────────────────────────────────────

    static void hashingDemo() throws Exception {
        System.out.println("=== 1. SHA-256 Hashing ===");
        String input = "Hello, Java Security!";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        System.out.println("Input:   " + input);
        System.out.println("SHA-256: " + HexFormat.of().formatHex(hash));

        // Same input always produces same hash
        byte[] hash2 = md.digest(input.getBytes());
        System.out.println("Deterministic: " + (HexFormat.of().formatHex(hash)
            .equals(HexFormat.of().formatHex(hash2))));

        // SHA-3-256 (Keccak)
        MessageDigest sha3 = MessageDigest.getInstance("SHA3-256");
        System.out.println("SHA3-256: " + HexFormat.of().formatHex(sha3.digest(input.getBytes())));
        System.out.println();
    }

    // ─── 2. AES/GCM Authenticated Encryption ────────────────────────────────

    static void aesGcmDemo() throws Exception {
        System.out.println("=== 2. AES-256/GCM Authenticated Encryption ===");

        // Generate AES-256 key
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, new SecureRandom());
        SecretKey key = kg.generateKey();

        // Generate random 12-byte IV (nonce) — MUST be unique per encryption
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

        String plaintext = "Sensitive data: credit card 4111-1111-1111-1111";
        System.out.println("Plaintext: " + plaintext);

        // Encrypt
        Cipher encCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encCipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        encCipher.updateAAD("user-id-12345".getBytes()); // associated data
        byte[] ciphertext = encCipher.doFinal(plaintext.getBytes());
        System.out.println("Ciphertext (base64): " + Base64.getEncoder().encodeToString(ciphertext));

        // Decrypt
        Cipher decCipher = Cipher.getInstance("AES/GCM/NoPadding");
        decCipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        decCipher.updateAAD("user-id-12345".getBytes()); // same AAD required
        byte[] decrypted = decCipher.doFinal(ciphertext);
        System.out.println("Decrypted: " + new String(decrypted));

        // Tamper detection — modify ciphertext; decryption MUST fail
        ciphertext[0] ^= 0xFF;
        try {
            Cipher tamperedCipher = Cipher.getInstance("AES/GCM/NoPadding");
            tamperedCipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            tamperedCipher.updateAAD("user-id-12345".getBytes());
            tamperedCipher.doFinal(ciphertext);
            System.out.println("ERROR: tampered data was not detected!");
        } catch (AEADBadTagException e) {
            System.out.println("Tamper detected correctly: " + e.getClass().getSimpleName());
        }
        System.out.println();
    }

    // ─── 3. HMAC ─────────────────────────────────────────────────────────────

    static void hmacDemo() throws Exception {
        System.out.println("=== 3. HMAC-SHA256 (Message Authentication Code) ===");
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        SecretKeySpec hmacKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(hmacKey);
        byte[] tag = mac.doFinal("The message to authenticate".getBytes());
        System.out.println("HMAC: " + HexFormat.of().formatHex(tag));

        // Verify: recompute HMAC and compare — use MessageDigest.isEqual for timing-safe compare
        mac.reset();
        byte[] tag2 = mac.doFinal("The message to authenticate".getBytes());
        boolean valid = MessageDigest.isEqual(tag, tag2);  // constant-time comparison
        System.out.println("HMAC verified: " + valid);

        // Wrong key → different HMAC
        byte[] wrongKey = new byte[32];
        new SecureRandom().nextBytes(wrongKey);
        mac.init(new SecretKeySpec(wrongKey, "HmacSHA256"));
        byte[] wrongTag = mac.doFinal("The message to authenticate".getBytes());
        System.out.println("Wrong key mismatch: " + !MessageDigest.isEqual(tag, wrongTag));
        System.out.println();
    }

    // ─── 4. SecureRandom ─────────────────────────────────────────────────────

    static void secureRandomDemo() {
        System.out.println("=== 4. SecureRandom (CSRF token, session ID, API key) ===");
        SecureRandom rng = new SecureRandom();

        // 256-bit token (32 bytes)
        byte[] tokenBytes = new byte[32];
        rng.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        System.out.println("Random token: " + token);

        // Hex token
        byte[] hexBytes = new byte[16];
        rng.nextBytes(hexBytes);
        System.out.println("Hex token: " + HexFormat.of().formatHex(hexBytes));

        // Random number in range
        int otp = 100_000 + rng.nextInt(900_000);  // 6-digit OTP
        System.out.println("6-digit OTP: " + otp);

        System.out.println("\nKey rules:");
        System.out.println("  - Always use SecureRandom for security-sensitive values");
        System.out.println("  - Never use Math.random() or new Random() for tokens/passwords");
        System.out.println("  - 32+ bytes = 256-bit entropy; sufficient for all modern needs");
    }
}
