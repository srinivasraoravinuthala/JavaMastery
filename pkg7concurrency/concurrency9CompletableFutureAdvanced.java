package pkg7concurrency;

/*
 * concurrency9CompletableFutureAdvanced.java
 * ------------------------------------------
 * Advanced CompletableFuture patterns:
 *   - Parallel fan-out + combine
 *   - Exception handling chains
 *   - Timeout with orTimeout / completeOnTimeout
 *   - allOf + anyOf patterns
 *   - Custom executor for blocking work
 *
 * Compile: javac pkg7concurrency/concurrency8CompletableFutureAdvanced.java
 * Run:     java  pkg7concurrency.concurrency8CompletableFutureAdvanced
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

public class concurrency8CompletableFutureAdvanced {

    // Use a dedicated pool for blocking I/O (simulated here)
    private static final ExecutorService IO_POOL =
        Executors.newVirtualThreadPerTaskExecutor();  // Java 21+

    public static void main(String[] args) throws Exception {
        parallelFanOut();
        exceptionHandling();
        timeoutPatterns();
        allOfAnyOf();
        IO_POOL.shutdown();
    }

    // ─── 1. Parallel Fan-Out & Combine ───────────────────────────────────────

    static void parallelFanOut() throws Exception {
        System.out.println("=== 1. Parallel Fan-Out ===");
        long start = System.currentTimeMillis();

        // Three independent "service calls" running concurrently
        CompletableFuture<String> userCF  = CompletableFuture.supplyAsync(
            () -> simulateCall("user",   200), IO_POOL);
        CompletableFuture<Integer> scoreCF = CompletableFuture.supplyAsync(
            () -> simulateCallInt("score", 150), IO_POOL);
        CompletableFuture<String> avatarCF = CompletableFuture.supplyAsync(
            () -> simulateCall("avatar", 180), IO_POOL);

        // Combine when all are done
        CompletableFuture<Void> all = CompletableFuture.allOf(userCF, scoreCF, avatarCF);
        all.thenRun(() -> {
            String user   = userCF.join();
            int score     = scoreCF.join();
            String avatar = avatarCF.join();
            System.out.printf("  user=%s  score=%d  avatar=%s%n", user, score, avatar);
        }).get();

        long elapsed = System.currentTimeMillis() - start;
        // Should be ~200ms (parallel), not 530ms (sequential)
        System.out.printf("  Elapsed: %dms (parallel — max of individual latencies)%n%n", elapsed);
    }

    // ─── 2. Exception Handling ───────────────────────────────────────────────

    static void exceptionHandling() throws Exception {
        System.out.println("=== 2. Exception Handling ===");

        // exceptionally — recover from any error
        String result = CompletableFuture.supplyAsync(() -> {
                if (true) throw new RuntimeException("Service down");
                return "ok";
            })
            .exceptionally(ex -> "fallback: " + ex.getMessage())
            .get();
        System.out.println("  exceptionally: " + result);

        // handle — intercept both success and error
        String result2 = CompletableFuture.supplyAsync(() -> "success")
            .handle((value, ex) -> {
                if (ex != null) return "error: " + ex.getMessage();
                return "handled: " + value.toUpperCase();
            })
            .get();
        System.out.println("  handle: " + result2);

        // whenComplete — side effect (logging), doesn't change result
        CompletableFuture.supplyAsync(() -> 42)
            .whenComplete((v, ex) -> {
                if (ex != null) System.out.println("  Error: " + ex);
                else System.out.println("  whenComplete got: " + v);
            })
            .get();

        System.out.println();
    }

    // ─── 3. Timeout Patterns ─────────────────────────────────────────────────

    static void timeoutPatterns() throws Exception {
        System.out.println("=== 3. Timeout Patterns ===");

        // orTimeout — fail with TimeoutException after delay
        try {
            CompletableFuture.supplyAsync(() -> slowCall())
                .orTimeout(100, TimeUnit.MILLISECONDS)
                .get();
        } catch (ExecutionException e) {
            System.out.println("  orTimeout triggered: " + e.getCause().getClass().getSimpleName());
        }

        // completeOnTimeout — substitute default value on timeout
        String val = CompletableFuture.supplyAsync(() -> slowCall())
            .completeOnTimeout("default-on-timeout", 100, TimeUnit.MILLISECONDS)
            .get();
        System.out.println("  completeOnTimeout result: " + val);

        System.out.println();
    }

    // ─── 4. allOf and anyOf ──────────────────────────────────────────────────

    static void allOfAnyOf() throws Exception {
        System.out.println("=== 4. allOf / anyOf ===");

        List<CompletableFuture<Integer>> futures = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> CompletableFuture.supplyAsync(
                () -> simulateCallInt("task-" + i, i * 50L), IO_POOL))
            .collect(Collectors.toList());

        // allOf: all complete, then collect results
        CompletableFuture<List<Integer>> allResults = CompletableFuture
            .allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());

        System.out.println("  allOf results: " + allResults.get());

        // anyOf: first to complete wins
        CompletableFuture<Object> first = CompletableFuture.anyOf(
            CompletableFuture.supplyAsync(() -> simulateCall("fast",  50), IO_POOL),
            CompletableFuture.supplyAsync(() -> simulateCall("slow", 200), IO_POOL)
        );
        System.out.println("  anyOf first: " + first.get());
        System.out.println();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static <T> T simulateCall(String name, long delayMs) {
        try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return (T) name.toUpperCase();
    }

    static int simulateCallInt(String name, long delayMs) {
        try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return name.hashCode() & 0xFF;
    }

    static String slowCall() {
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "slow-result";
    }
}
