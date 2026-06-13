package pkg2versions;

/*
 * versions2Java7Features.java  (2011)  "Project Coin"
 * ------------------------------------------
 * FEATURES & WHY:
 *  - try-with-resources : auto-close resources, less boilerplate, no leaks.
 *  - Diamond operator <> : infer generic type on the right side.
 *  - Strings in switch  : cleaner branching on strings.
 *  - Multi-catch        : handle multiple exception types in one block.
 *  - Numeric underscores: readable literals (1_000_000).
 *  - Binary literals    : 0b1010.
 */
import java.util.*;

public class versions2Java7Features {

    static class Resource implements AutoCloseable {
        public void close() { System.out.println("resource closed"); }
        void doWork() { System.out.println("working"); }
    }

    public static void main(String[] args) {
        // Diamond operator: no need to repeat <String,List<Integer>>
        Map<String, List<Integer>> map = new HashMap<>();
        map.computeIfAbsent("a", k -> new ArrayList<>()).add(1);
        System.out.println("diamond map: " + map);

        // try-with-resources
        try (Resource r = new Resource()) {
            r.doWork();
        }

        // Strings in switch
        String cmd = "start";
        switch (cmd) {
            case "start" -> System.out.println("starting...");
            case "stop"  -> System.out.println("stopping...");
            default      -> System.out.println("unknown");
        }

        // Multi-catch
        try {
            if (cmd.equals("start")) throw new IllegalStateException("boom");
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.out.println("multi-catch: " + e.getMessage());
        }

        // Numeric underscores + binary literals
        int million = 1_000_000;
        int bits = 0b1010_1010;
        System.out.println("million=" + million + " binary=" + bits);
    }
}
