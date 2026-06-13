package pkg16advconcurrency;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/*
 * advconcurrency7StructuredConcurrency.java
 * -----------------------------------------
 * Structured task patterns: parent owns child lifetimes (Java 21+ preview API).
 *
 * DEFINITION:
 *   StructuredTaskScope groups subtasks so they cancel together when the scope
 *   closes or one fails. This demo uses CompletableFuture (stable API) for the
 *   same failover pattern; StructuredTaskScope requires --enable-preview on JDK 25.
 *
 * StructuredTaskScope (preview) equivalent:
 *   try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
 *       scope.fork(() -> fetchFrom("primary", 200));
 *       scope.fork(() -> fetchFrom("backup", 50));
 *       scope.join();
 *       return scope.result();
 *   }
 *
 * Run with preview API: java --enable-preview advconcurrency7StructuredConcurrency.java
 */
public class advconcurrency7StructuredConcurrency {

    static String fetchFrom(String source, int delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
        return "data-from-" + source;
    }

    public static void main(String[] args) throws Exception {
        // Failover race: first successful result wins (CompletableFuture stable equivalent)
        CompletableFuture<String> primary = CompletableFuture.supplyAsync(() -> {
            try { return fetchFrom("primary", 200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new RuntimeException(e);
            }
        });
        CompletableFuture<String> backup = CompletableFuture.supplyAsync(() -> {
            try { return fetchFrom("backup", 50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new RuntimeException(e);
            }
        });

        String winner = primary.applyToEither(backup, s -> s)
                .get(1, TimeUnit.SECONDS);
        System.out.println("Race winner: " + winner);

        // All must succeed in parallel
        CompletableFuture<String> db = CompletableFuture.supplyAsync(() -> {
            try { return fetchFrom("db", 30); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new RuntimeException(e);
            }
        });
        CompletableFuture<String> cache = CompletableFuture.supplyAsync(() -> {
            try { return fetchFrom("cache", 20); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new RuntimeException(e);
            }
        });
        System.out.println("Parallel: " + db.get() + " + " + cache.get());
    }
}
