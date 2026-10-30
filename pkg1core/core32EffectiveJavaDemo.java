package pkg1core;

/*
 * core32EffectiveJavaDemo.java
 * ----------------------------
 * Key Effective Java (3rd Edition) patterns demonstrated with runnable code:
 *   - Static factory methods (Item 1)
 *   - Builder pattern (Item 2)
 *   - Enum singleton (Item 3)
 *   - Defensive copy for immutability (Item 17)
 *   - PECS wildcard rule (Item 31)
 *   - Side-effect-free streams (Item 46)
 *
 * EXPLANATION:
 *  Effective Java distills 20+ years of Java experience into 90 items.
 *  This demo covers the most-asked items with concrete runnable examples.
 *
 * Compile: javac pkg1core/core32EffectiveJavaDemo.java
 * Run:     java  pkg1core.core32EffectiveJavaDemo
 */

import java.util.*;
import java.util.stream.*;
import java.util.function.*;

public class core32EffectiveJavaDemo {

    public static void main(String[] args) {
        staticFactoryDemo();
        builderDemo();
        enumSingletonDemo();
        defensiveCopyDemo();
        pecsDemo();
        streamsDemo();
    }

    // ─── Item 1: Static Factory Methods ──────────────────────────────────────

    static class Connection {
        private final String url;
        private final boolean readOnly;

        private Connection(String url, boolean readOnly) {
            this.url = url;
            this.readOnly = readOnly;
        }

        // Named factories beat constructors — intent is clear
        public static Connection readOnly(String url)  { return new Connection(url, true); }
        public static Connection readWrite(String url) { return new Connection(url, false); }

        // Can cache common instances (impossible with constructors)
        private static final Connection MEMORY_DB = new Connection("mem://test", false);
        public static Connection inMemory() { return MEMORY_DB; }

        @Override public String toString() {
            return "Connection{url=" + url + ", readOnly=" + readOnly + "}";
        }
    }

    static void staticFactoryDemo() {
        System.out.println("=== Item 1: Static Factory Methods ===");
        System.out.println(Connection.readOnly("jdbc:postgresql://prod/db"));
        System.out.println(Connection.readWrite("jdbc:postgresql://dev/db"));
        System.out.println(Connection.inMemory());
        System.out.println();
    }

    // ─── Item 2: Builder Pattern ─────────────────────────────────────────────

    static final class Email {
        private final String to;
        private final String subject;
        private final String body;
        private final String cc;      // optional
        private final boolean html;   // optional, default false

        private Email(Builder b) {
            this.to = b.to; this.subject = b.subject; this.body = b.body;
            this.cc = b.cc; this.html = b.html;
        }

        public static class Builder {
            // Required
            private final String to;
            private final String subject;
            private final String body;
            // Optional — defaults
            private String cc = "";
            private boolean html = false;

            public Builder(String to, String subject, String body) {
                this.to = Objects.requireNonNull(to);
                this.subject = Objects.requireNonNull(subject);
                this.body = Objects.requireNonNull(body);
            }
            public Builder cc(String cc)      { this.cc = cc; return this; }
            public Builder html(boolean html) { this.html = html; return this; }
            public Email build()              { return new Email(this); }
        }

        @Override public String toString() {
            return String.format("Email{to=%s, subject='%s', cc=%s, html=%s}", to, subject, cc, html);
        }
    }

    static void builderDemo() {
        System.out.println("=== Item 2: Builder Pattern ===");
        Email e1 = new Email.Builder("alice@example.com", "Hello", "Body text").build();
        Email e2 = new Email.Builder("bob@example.com", "Report", "<h1>Report</h1>")
            .cc("manager@example.com").html(true).build();
        System.out.println(e1);
        System.out.println(e2);
        System.out.println();
    }

    // ─── Item 3: Enum Singleton ───────────────────────────────────────────────

    enum AppConfig {
        INSTANCE;
        private final String apiKey = System.getenv().getOrDefault("API_KEY", "dev-key-123");
        private int maxRetries = 3;

        public String getApiKey()   { return apiKey; }
        public int getMaxRetries()  { return maxRetries; }
        public void setMaxRetries(int n) { maxRetries = n; }
    }

    static void enumSingletonDemo() {
        System.out.println("=== Item 3: Enum Singleton ===");
        System.out.println("API key: " + AppConfig.INSTANCE.getApiKey());
        AppConfig.INSTANCE.setMaxRetries(5);
        System.out.println("Max retries: " + AppConfig.INSTANCE.getMaxRetries());
        // Same instance guaranteed — safe for serialization and reflection
        System.out.println("Same instance: " + (AppConfig.INSTANCE == AppConfig.INSTANCE));
        System.out.println();
    }

    // ─── Item 17: Immutability & Defensive Copy ───────────────────────────────

    static final class DateRange {
        private final Date start;
        private final Date end;

        public DateRange(Date start, Date end) {
            // Defensive copy IN — protect from external mutation
            this.start = new Date(start.getTime());
            this.end   = new Date(end.getTime());
            if (this.start.after(this.end)) {
                throw new IllegalArgumentException("start must be before end");
            }
        }

        public Date getStart() { return new Date(start.getTime()); }  // copy OUT
        public Date getEnd()   { return new Date(end.getTime()); }

        @Override public String toString() {
            return "DateRange{" + start + " → " + end + "}";
        }
    }

    @SuppressWarnings("deprecation")
    static void defensiveCopyDemo() {
        System.out.println("=== Item 17: Defensive Copy ===");
        Date start = new Date(2025 - 1900, 0, 1);   // Jan 1 2025
        Date end   = new Date(2025 - 1900, 11, 31);  // Dec 31 2025
        DateRange range = new DateRange(start, end);

        // Mutating original Date has NO effect on range
        start.setYear(2000 - 1900);
        System.out.println("Range after mutating original start: " + range);

        // Mutating returned Date has NO effect on range
        range.getStart().setYear(2000 - 1900);
        System.out.println("Range after mutating returned start: " + range);
        System.out.println();
    }

    // ─── Item 31: PECS (Producer Extends, Consumer Super) ────────────────────

    // Reads from src (produces T) — <? extends T>
    static <T> void copy(List<? super T> dest, List<? extends T> src) {
        for (T item : src) dest.add(item);
    }

    // Computes sum — needs to read Numbers (producer)
    static double sum(Collection<? extends Number> nums) {
        return nums.stream().mapToDouble(Number::doubleValue).sum();
    }

    static void pecsDemo() {
        System.out.println("=== Item 31: PECS Wildcards ===");

        List<Integer> integers = List.of(1, 2, 3);
        List<Number> numbers = new ArrayList<>();
        copy(numbers, integers);   // List<? super Integer> = List<Number> ✓
        System.out.println("Copied: " + numbers);

        List<Double> doubles = List.of(1.5, 2.5, 3.5);
        System.out.println("Sum of integers: " + sum(integers));
        System.out.println("Sum of doubles:  " + sum(doubles));
        System.out.println();
    }

    // ─── Item 46: Side-Effect-Free Streams ───────────────────────────────────

    static void streamsDemo() {
        System.out.println("=== Item 46: Prefer Side-Effect-Free Streams ===");
        List<String> words = List.of("Java", "is", "fun", "and", "fast", "and", "powerful");

        // BAD — side effect inside stream (mutation in forEach)
        Map<String, Long> freqBad = new HashMap<>();
        words.stream().forEach(w -> freqBad.merge(w, 1L, Long::sum));
        System.out.println("Bad (mutation in stream): " + freqBad);

        // GOOD — use Collectors; no mutation inside stream
        Map<String, Long> freqGood = words.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        System.out.println("Good (collect):           " + freqGood);

        // Group by length — purely functional
        Map<Integer, List<String>> byLength = words.stream()
            .collect(Collectors.groupingBy(String::length));
        System.out.println("Grouped by length: " + byLength);

        // Top 3 most frequent words
        freqGood.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .forEach(e -> System.out.printf("  '%s' × %d%n", e.getKey(), e.getValue()));
        System.out.println();
    }
}
