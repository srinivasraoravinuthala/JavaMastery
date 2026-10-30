# 42 — Pattern Matching Deep Dive (Java 16 → 25)

**Previous:** [41 REST & Frontend](41-RestAndFrontend.md) · **Next:** [43 Foreign Function & Memory API](43-ForeignMemoryAPI.md)

▶️ `pkg1core/core31PatternMatchingAdvanced.java`

---

## Why Pattern Matching?

Pattern matching combines **testing**, **binding**, and **destructuring** into one compact expression. Java introduced it gradually via Project Amber across Java 16–25.

---

## 1. `instanceof` Pattern (Java 16, final)

```java
// Old
if (obj instanceof String) {
    String s = (String) obj;
    System.out.println(s.toUpperCase());
}

// Pattern matching
if (obj instanceof String s) {
    System.out.println(s.toUpperCase());   // s is String, already cast
}

// Combined with logic (guarded)
if (obj instanceof String s && s.length() > 5) {
    System.out.println("Long string: " + s);
}

// Negation — s is NOT in scope after negation
if (!(obj instanceof String s)) {
    return;
}
System.out.println(s);  // s still in scope here
```

**Rules:**
- `null` never matches any pattern — `obj instanceof String s` is `false` when `obj == null`.
- Binding variable scope follows control flow (definite assignment analysis).

---

## 2. Pattern Matching for `switch` (Java 21, final)

```java
sealed interface Shape permits Circle, Rectangle, Triangle {}
record Circle(double radius) implements Shape {}
record Rectangle(double w, double h) implements Shape {}
record Triangle(double base, double height) implements Shape {}

static double area(Shape s) {
    return switch (s) {
        case Circle c       -> Math.PI * c.radius() * c.radius();
        case Rectangle r    -> r.w() * r.h();
        case Triangle t     -> 0.5 * t.base() * t.height();
    };   // No default needed — sealed, exhaustive
}
```

**Null handling in switch:**
```java
static String describe(Object o) {
    return switch (o) {
        case null           -> "nothing";
        case Integer i      -> "int " + i;
        case String s       -> "string " + s;
        default             -> "other: " + o.getClass().getSimpleName();
    };
}
```

---

## 3. Guarded Patterns (`when` clauses, Java 21)

```java
static String classify(Number n) {
    return switch (n) {
        case Integer i when i < 0   -> "negative int";
        case Integer i when i == 0  -> "zero";
        case Integer i              -> "positive int: " + i;
        case Double d when d.isNaN()-> "NaN double";
        case Double d               -> "double: " + d;
        default                     -> "other number";
    };
}
```

`when` guard is evaluated only if the type pattern matches. Order matters — more specific cases first.

---

## 4. Record Patterns (Java 21, final)

Deconstruct record components directly in patterns:

```java
record Point(int x, int y) {}
record Line(Point start, Point end) {}

static void printX(Object o) {
    if (o instanceof Point(int x, int y)) {
        System.out.println("x=" + x + " y=" + y);
    }
}

// Nested deconstruction
static double length(Object o) {
    return switch (o) {
        case Line(Point(int x1, int y1), Point(int x2, int y2)) -> {
            double dx = x2 - x1, dy = y2 - y1;
            yield Math.sqrt(dx * dx + dy * dy);
        }
        default -> 0;
    };
}
```

---

## 5. Unnamed Variables `_` in Patterns (Java 22, final)

```java
// Ignore components you don't need
if (o instanceof Point(int x, _)) {
    System.out.println("x = " + x);
}

// Ignore entire matched variable
switch (shape) {
    case Circle _    -> System.out.println("It's a circle");
    case Rectangle _ -> System.out.println("It's a rectangle");
}
```

---

## 6. Primitive Patterns (Java 23, preview → Java 25, final)

Extend patterns to primitive types — eliminating the need for boxing:

```java
// Before (must box or use separate if-else)
Object val = 42;
if (val instanceof Integer i) { ... }

// Java 25: primitive pattern in instanceof
long big = 999_999L;
if (big instanceof int i) {             // narrowing check + bind
    System.out.println("fits in int: " + i);
}

// In switch
static String describeNumber(long n) {
    return switch (n) {
        case byte b  -> "byte: " + b;
        case short s -> "short: " + s;
        case int i   -> "int: " + i;
        default      -> "long: " + n;
    };
}
```

---

## 7. Real-World Example: Expression Tree Evaluator

```java
sealed interface Expr permits Num, Add, Mul, Neg {}
record Num(double value)        implements Expr {}
record Add(Expr left, Expr right) implements Expr {}
record Mul(Expr left, Expr right) implements Expr {}
record Neg(Expr expr)            implements Expr {}

static double eval(Expr e) {
    return switch (e) {
        case Num(double v)          -> v;
        case Add(var l, var r)      -> eval(l) + eval(r);
        case Mul(var l, var r)      -> eval(l) * eval(r);
        case Neg(var inner)         -> -eval(inner);
    };
}

// Usage
Expr expr = new Add(new Num(3), new Mul(new Num(4), new Neg(new Num(2))));
System.out.println(eval(expr));   // 3 + (4 * -2) = -5.0
```

This replaces visitor pattern boilerplate with readable, exhaustive, compiler-checked code.

---

## 8. Migration Guide

| Old style | Modern pattern equivalent |
|-----------|--------------------------|
| `instanceof + cast` | `instanceof Type t` |
| `if-else instanceof chain` | `switch` with type patterns |
| Visitor pattern | sealed + record patterns + switch |
| `obj == null ? … : …` | `case null` in switch |
| Dummy catch variable | `catch (Exception _)` |
| Skip for-each variable | `for (int _ : list)` |

---

## Interview Questions

1. What is a guarded pattern? → `case Type t when condition` — guard evaluated after type match.
2. Are record patterns recursive? → Yes, nested as deep as needed.
3. When must you have a `default` in switch? → Non-sealed, non-enum selector types.
4. Can you use `_` for all components? → Yes, `case Point(_, _)` is valid.
5. Primitive patterns vs autoboxing? → Primitive patterns avoid boxing overhead; match by value range.

---

**Next →** [43 Foreign Function & Memory API](43-ForeignMemoryAPI.md)
