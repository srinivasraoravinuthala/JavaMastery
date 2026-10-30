package pkg6jvm;

/*
 * jvm4FlightRecorderDemo.java
 * ---------------------------
 * Java Flight Recorder (JFR) demo:
 *   - Custom JFR event definition and recording
 *   - Reading back events from a JFR recording programmatically
 *   - JVM diagnostic commands overview
 *
 * EXPLANATION:
 *  JFR is a low-overhead production profiler built into the JDK.
 *  Custom events let you capture business-level timing alongside JVM events.
 *
 * Compile: javac pkg6jvm/jvm4FlightRecorderDemo.java
 * Run:     java  pkg6jvm.jvm4FlightRecorderDemo
 *
 * To record full JFR (all JVM events + custom):
 *   java -XX:StartFlightRecording=duration=10s,filename=demo.jfr \
 *        pkg6jvm.jvm4FlightRecorderDemo
 */

import jdk.jfr.*;
import jdk.jfr.consumer.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class jvm4FlightRecorderDemo {

    // ── Custom JFR Event ─────────────────────────────────────────────────────

    @Label("Service Call")
    @Description("Tracks duration and result of external service calls")
    @Category({"Application", "Services"})
    @StackTrace(true)
    static class ServiceCallEvent extends Event {
        @Label("Service Name")
        String serviceName;

        @Label("Operation")
        String operation;

        @Label("Success")
        boolean success;

        @Label("Duration ms")
        long durationMs;
    }

    // ── Simulated Service ─────────────────────────────────────────────────────

    static String callService(String service, String op, long simulatedDelayMs) {
        var event = new ServiceCallEvent();
        event.serviceName = service;
        event.operation = op;
        event.begin();

        try {
            Thread.sleep(simulatedDelayMs);     // simulate I/O
            event.success = true;
            return "OK";
        } catch (InterruptedException e) {
            event.success = false;
            Thread.currentThread().interrupt();
            return "ERROR";
        } finally {
            event.end();
            event.durationMs = event.getDuration().toMillis();
            event.commit();   // writes to active JFR recording (if any)
        }
    }

    // ── Programmatic JFR Recording ────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("=== JFR Custom Event Demo ===\n");

        Path jfrFile = Files.createTempFile("jfr-demo-", ".jfr");

        // Start recording programmatically
        var recording = new jdk.jfr.Recording();
        recording.enable(ServiceCallEvent.class).withStackTrace();
        recording.setToDisk(true);
        recording.setDestination(jfrFile);
        recording.start();

        System.out.println("Recording started...");

        // Simulate some service calls
        String[] services = {"UserService", "OrderService", "InventoryService"};
        String[] ops = {"GET", "CREATE", "UPDATE", "DELETE"};
        var rng = new Random();

        for (int i = 0; i < 10; i++) {
            String svc = services[rng.nextInt(services.length)];
            String op  = ops[rng.nextInt(ops.length)];
            long delay = 10 + rng.nextInt(200);
            String result = callService(svc, op, delay);
            System.out.printf("  %-20s %-8s %3dms → %s%n", svc, op, delay, result);
        }

        recording.stop();
        System.out.println("\nRecording saved to: " + jfrFile);

        // Read back and analyze
        System.out.println("\n=== Reading JFR events back ===");
        analyzeRecording(jfrFile);

        // Cleanup
        Files.deleteIfExists(jfrFile);
    }

    static void analyzeRecording(Path jfrFile) throws Exception {
        var events = RecordingFile.readAllEvents(jfrFile);

        // Filter to our custom events only
        var serviceEvents = events.stream()
            .filter(e -> e.getEventType().getName().contains("ServiceCallEvent"))
            .toList();

        System.out.printf("Total JVM events in recording: %d%n", events.size());
        System.out.printf("Custom ServiceCallEvent count: %d%n", serviceEvents.size());

        if (serviceEvents.isEmpty()) {
            System.out.println("(No ServiceCallEvents captured — check event class name)");
            return;
        }

        // Per-service stats
        Map<String, List<Long>> byService = new LinkedHashMap<>();
        for (var e : serviceEvents) {
            String svc = e.getValue("serviceName").toString();
            long dur    = (long) e.getValue("durationMs");
            byService.computeIfAbsent(svc, k -> new ArrayList<>()).add(dur);
        }

        System.out.println("\n── Per-service latency stats ──");
        for (var entry : byService.entrySet()) {
            var durations = entry.getValue();
            double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            long max    = durations.stream().mapToLong(Long::longValue).max().orElse(0);
            System.out.printf("  %-20s  calls=%d  avg=%5.1fms  max=%dms%n",
                entry.getKey(), durations.size(), avg, max);
        }
    }

    // ── JVM Diagnostic Reference ─────────────────────────────────────────────
    /*
     * Useful jcmd commands (run these against a live JVM process):
     *
     *   jcmd                           # list all running JVMs
     *   jcmd <PID> VM.flags            # show JVM flags
     *   jcmd <PID> GC.heap_info        # heap summary
     *   jcmd <PID> GC.class_histogram  # top classes by instance count
     *   jcmd <PID> Thread.print        # thread dump
     *   jcmd <PID> GC.heap_dump /tmp/heap.hprof  # heap dump
     *
     *   jcmd <PID> JFR.start duration=60s filename=app.jfr
     *   jcmd <PID> JFR.stop
     *   jcmd <PID> JFR.dump filename=dump.jfr
     *
     * async-profiler (external tool):
     *   ./asprof -d 30 -f flamegraph.html <PID>   # CPU flamegraph
     *   ./asprof -d 30 -e alloc -f alloc.html <PID>  # allocation flamegraph
     */
}
