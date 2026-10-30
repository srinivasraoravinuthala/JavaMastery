package pkg2versions;

/*
 * versions12ForeignMemoryAPI.java
 * --------------------------------
 * Foreign Function & Memory API (JEP 454) — final in Java 22.
 * Demonstrates off-heap memory allocation, layout, and VarHandle access.
 *
 * Compile: javac pkg2versions/versions12ForeignMemoryAPI.java
 * Run:     java  pkg2versions.versions12ForeignMemoryAPI
 *
 * NOTE: calling actual native functions (strlen, etc.) requires
 * the native library to be available.  This demo focuses on the
 * memory management half which is always available.
 */

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public class versions12ForeignMemoryAPI {

    // --- Layout for a C-style struct:  struct Point { int x; int y; }
    private static final StructLayout POINT_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    );

    private static final VarHandle POINT_X =
        POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));
    private static final VarHandle POINT_Y =
        POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));

    public static void main(String[] args) {
        basicAllocation();
        structLayout();
        arrayLayout();
        arenaLifetimes();
    }

    /** 1. Simplest possible off-heap allocation */
    static void basicAllocation() {
        System.out.println("=== 1. Basic Allocation ===");
        try (Arena arena = Arena.ofConfined()) {
            // Allocate space for a single int
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_INT);
            seg.set(ValueLayout.JAVA_INT, 0, 42);
            int v = seg.get(ValueLayout.JAVA_INT, 0);
            System.out.println("Stored and retrieved: " + v);  // 42

            // Allocate a byte array
            MemorySegment bytes = arena.allocate(8);
            for (int i = 0; i < 8; i++) {
                bytes.set(ValueLayout.JAVA_BYTE, i, (byte) (i * 10));
            }
            System.out.print("Bytes: ");
            for (int i = 0; i < 8; i++) {
                System.out.print(bytes.get(ValueLayout.JAVA_BYTE, i) + " ");
            }
            System.out.println();
        } // arena closes here — memory freed immediately
        System.out.println("Arena closed; memory freed.");
    }

    /** 2. Struct-like layout with VarHandle access */
    static void structLayout() {
        System.out.println("\n=== 2. Struct Layout (Point{int x, int y}) ===");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment point = arena.allocate(POINT_LAYOUT);

            // Write fields via VarHandle
            POINT_X.set(point, 0L, 10);
            POINT_Y.set(point, 0L, 20);

            // Read fields
            int x = (int) POINT_X.get(point, 0L);
            int y = (int) POINT_Y.get(point, 0L);
            System.out.printf("Point { x=%d, y=%d }%n", x, y);
            System.out.println("Byte size: " + POINT_LAYOUT.byteSize());  // 8
        }
    }

    /** 3. Array of structs */
    static void arrayLayout() {
        System.out.println("\n=== 3. Array of Structs ===");
        int count = 4;
        SequenceLayout arrayLayout = MemoryLayout.sequenceLayout(count, POINT_LAYOUT);
        VarHandle axh = arrayLayout.varHandle(
            MemoryLayout.PathElement.sequenceElement(),
            MemoryLayout.PathElement.groupElement("x"));
        VarHandle ayh = arrayLayout.varHandle(
            MemoryLayout.PathElement.sequenceElement(),
            MemoryLayout.PathElement.groupElement("y"));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment arr = arena.allocate(arrayLayout);
            for (long i = 0; i < count; i++) {
                axh.set(arr, 0L, i, (int)(i * 10));
                ayh.set(arr, 0L, i, (int)(i * 10 + 1));
            }
            for (long i = 0; i < count; i++) {
                System.out.printf("  [%d] x=%d y=%d%n",
                    i, axh.get(arr, 0L, i), ayh.get(arr, 0L, i));
            }
        }
    }

    /** 4. Arena types and lifetimes */
    static void arenaLifetimes() {
        System.out.println("\n=== 4. Arena Lifetimes ===");

        // Confined — closed explicitly with try-with-resources (single thread)
        try (Arena confined = Arena.ofConfined()) {
            MemorySegment s = confined.allocate(4);
            s.set(ValueLayout.JAVA_INT, 0, 1);
            System.out.println("Confined arena value: " + s.get(ValueLayout.JAVA_INT, 0));
        }

        // Auto — GC-managed, no explicit close needed
        Arena auto = Arena.ofAuto();
        MemorySegment autoSeg = auto.allocate(4);
        autoSeg.set(ValueLayout.JAVA_INT, 0, 999);
        System.out.println("Auto arena value: " + autoSeg.get(ValueLayout.JAVA_INT, 0));
        // freed eventually by GC

        // Global — never freed (use for process-lifetime data)
        MemorySegment global = Arena.global().allocate(4);
        global.set(ValueLayout.JAVA_INT, 0, 777);
        System.out.println("Global arena value: " + global.get(ValueLayout.JAVA_INT, 0));

        System.out.println("\nKey insight: Arena.ofConfined() is the workhorse — ");
        System.out.println("deterministic deallocation, single-thread safe.");
    }
}
