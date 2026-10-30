package pkg1core;

/*
 * core31PatternMatchingAdvanced.java
 * -----------------------------------
 * Pattern matching features across Java 16 → 25:
 *   - instanceof binding (Java 16)
 *   - switch type patterns + guarded cases (Java 21)
 *   - Record patterns + nested deconstruction (Java 21)
 *   - Unnamed variables _ in patterns (Java 22)
 *   - Real-world ADT example (sealed + record + switch)
 *
 * Compile: javac pkg1core/core31PatternMatchingAdvanced.java
 * Run:     java  pkg1core.core31PatternMatchingAdvanced
 */

import java.util.List;

public class core31PatternMatchingAdvanced {

    // ── Sealed type hierarchy (ADT) ──────────────────────────────────────────
    sealed interface Shape permits Circle, Rectangle, Triangle {}
    record Circle(double radius) implements Shape {}
    record Rectangle(double width, double height) implements Shape {}
    record Triangle(double base, double height) implements Shape {}

    // Arithmetic expression tree
    sealed interface Expr permits Num, Add, Mul, Neg {}
    record Num(double v)             implements Expr {}
    record Add(Expr l, Expr r)       implements Expr {}
    record Mul(Expr l, Expr r)       implements Expr {}
    record Neg(Expr inner)           implements Expr {}

    // Nested address
    record Address(String city, String country) {}
    record Person(String name, int age, Address address) {}

    public static void main(String[] args) {
        instanceofPatterns();
        switchTypePatterns();
        guardedPatterns();
        recordPatterns();
        unnamedVariables();
        expressionEvaluator();
    }

    // ─── 1. instanceof Patterns (Java 16) ────────────────────────────────────

    static void instanceofPatterns() {
        System.out.println("=== 1. instanceof Patterns ===");
        Object[] values = {"hello", 42, 3.14, null, List.of(1,2,3)};

        for (Object o : values) {
            // Old style needed: if (o instanceof String) { String s = (String) o; ... }
            // Pattern style: bind directly
            if (o instanceof String s && s.length() > 4) {
                System.out.println("  Long string: " + s.toUpperCase());
            } else if (o instanceof Integer n) {
                System.out.println("  Integer: " + (n * 2));
            } else if (o instanceof Double d) {
                System.out.printf("  Double: %.2f%n", d);
            } else if (o == null) {
                System.out.println("  Null value (null never matches patterns)");
            } else {
                System.out.println("  Other: " + o.getClass().getSimpleName());
            }
        }
        System.out.println();
    }

    // ─── 2. Switch Type Patterns (Java 21) ───────────────────────────────────

    static void switchTypePatterns() {
        System.out.println("=== 2. Switch Type Patterns ===");
        List<Shape> shapes = List.of(
            new Circle(5),
            new Rectangle(4, 6),
            new Triangle(3, 8)
        );

        for (Shape shape : shapes) {
            // Compiler verifies exhaustiveness because Shape is sealed
            double area = switch (shape) {
                case Circle c    -> Math.PI * c.radius() * c.radius();
                case Rectangle r -> r.width() * r.height();
                case Triangle t  -> 0.5 * t.base() * t.height();
            };
            System.out.printf("  %-25s area = %.2f%n", shape, area);
        }

        // null handling in switch (Java 21)
        Object o = null;
        String desc = switch (o) {
            case null          -> "nothing";
            case Integer i     -> "int " + i;
            case String s      -> "string: " + s;
            default            -> "other";
        };
        System.out.println("  null switch: " + desc);
        System.out.println();
    }

    // ─── 3. Guarded Patterns (when clauses, Java 21) ─────────────────────────

    static void guardedPatterns() {
        System.out.println("=== 3. Guarded Patterns (when) ===");
        Object[] nums = { -5, 0, 7, 100L, 3.14, "not a number" };

        for (Object n : nums) {
            String label = switch (n) {
                case Integer i when i < 0  -> "negative int: " + i;
                case Integer i when i == 0 -> "zero";
                case Integer i             -> "positive int: " + i;
                case Long l when l > 50    -> "big long: " + l;
                case Long l                -> "small long: " + l;
                case Double d              -> String.format("double %.2f", d);
                case String _              -> "not a number";
                default                    -> "unknown";
            };
            System.out.println("  " + label);
        }
        System.out.println();
    }

    // ─── 4. Record Patterns (Java 21) ────────────────────────────────────────

    static void recordPatterns() {
        System.out.println("=== 4. Record Patterns ===");

        // Deconstruct record in instanceof
        Object obj = new Circle(7.5);
        if (obj instanceof Circle(double r)) {
            System.out.printf("  Circle radius = %.1f, area = %.2f%n", r, Math.PI * r * r);
        }

        // Nested record deconstruction
        Person alice = new Person("Alice", 30, new Address("London", "UK"));
        if (alice instanceof Person(String name, _, Address(String city, _))) {
            System.out.println("  " + name + " lives in " + city);
        }

        // In switch — exhaustive on sealed hierarchy
        List<Expr> exprs = List.of(
            new Num(42),
            new Add(new Num(3), new Num(4)),
            new Neg(new Num(5))
        );
        for (Expr e : exprs) {
            System.out.println("  eval(" + e + ") = " + eval(e));
        }
        System.out.println();
    }

    // ─── 5. Unnamed Variables (Java 22) ──────────────────────────────────────

    static void unnamedVariables() {
        System.out.println("=== 5. Unnamed Variables `_` (Java 22) ===");

        // In enhanced-for — counting without using the variable
        var items = List.of("a", "b", "c", "d");
        int count = 0;
        for (var _ : items) count++;
        System.out.println("  Count: " + count);

        // In catch — exception not needed
        int parsed = -1;
        try {
            parsed = Integer.parseInt("abc");
        } catch (NumberFormatException _) {
            parsed = 0;  // default
        }
        System.out.println("  Parsed with fallback: " + parsed);

        // In record pattern — ignore components
        Object point = new Rectangle(10, 20);
        if (point instanceof Rectangle(double w, _)) {
            System.out.println("  Rectangle width only: " + w);
        }

        System.out.println();
    }

    // ─── 6. Real Expression Evaluator ────────────────────────────────────────

    static void expressionEvaluator() {
        System.out.println("=== 6. Expression Tree Evaluator (ADT + patterns) ===");
        // (3 + 4) * -(2)  =  7 * -2  = -14
        Expr expr = new Mul(
            new Add(new Num(3), new Num(4)),
            new Neg(new Num(2))
        );
        System.out.println("  Expression: " + prettyPrint(expr));
        System.out.println("  Result: " + eval(expr));
    }

    static double eval(Expr e) {
        return switch (e) {
            case Num(double v)       -> v;
            case Add(var l, var r)   -> eval(l) + eval(r);
            case Mul(var l, var r)   -> eval(l) * eval(r);
            case Neg(var inner)      -> -eval(inner);
        };
    }

    static String prettyPrint(Expr e) {
        return switch (e) {
            case Num(double v)       -> String.valueOf(v);
            case Add(var l, var r)   -> "(" + prettyPrint(l) + " + " + prettyPrint(r) + ")";
            case Mul(var l, var r)   -> "(" + prettyPrint(l) + " * " + prettyPrint(r) + ")";
            case Neg(var inner)      -> "-" + prettyPrint(inner);
        };
    }
}
