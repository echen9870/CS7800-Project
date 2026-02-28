import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class Main {
    public static void main(String[] args) {

        int bits = Integer.parseInt(args[0]);
        int universe = 1 << bits;

        TestingFramework framework = new TestingFramework(universe);
        framework.generateOps(
            (int) Math.min(1L << (bits + 2), Integer.MAX_VALUE),
            (int) Math.min(1L << (bits - 2), Integer.MAX_VALUE)
        );

        // BST
        // TreeSet<Integer> bst = new TreeSet<>();
        // {
        //     RunResult r = framework.runRandom(
        //             "BST",
        //             1,
        //             (x) -> bst.add(x),
        //             (x) -> bst.contains(x),
        //             (x) -> bst.ceiling(x)
        //     );
        //     results.add(r);
        // }

        // // XFast 1 thread
        // XFastTree xFast1 = new XFastTree(bits);
        // {
        //     RunResult r = framework.runRandom(
        //             "XFastTree",
        //             1,
        //             (x) -> xFast1.insert(x),
        //             (x) -> xFast1.query(x),
        //             (x) -> xFast1.successor(x)
        //     );
        //     results.add(r);
        // }

        // // XFast 8 threads
        // XFastTree xFast8 = new XFastTree(bits);
        // {
        //     RunResult r = framework.runRandom(
        //             "XFastTree",
        //             8,
        //             (x) -> xFast8.insert(x),
        //             (x) -> xFast8.query(x),
        //             (x) -> xFast8.successor(x)
        //     );
        //     results.add(r);
        // }

        // YFast 1 thread
        // ConcurrentYFastTree yFast = new ConcurrentYFastTree(bits);
        // {
        //     RunResult r = framework.runRandom(
        //             "ConcurrentYFastTree",
        //             1,
        //             (x) -> yFast.insert(x),
        //             (x) -> yFast.query(x),
        //             (x) -> yFast.successor(x)
        //     );
        //     System.out.println(r);
        // }

        // Concurrent Y-Fast 16 threads
        ConcurrentYFastTree concurrentYFast = new ConcurrentYFastTree(bits);
        {
            RunResult r = framework.runRandom(
                    "ConcurrentYFastTree",
                    16,
                    (x) -> concurrentYFast.insert(x),
                    (x) -> concurrentYFast.query(x),
                    (x) -> concurrentYFast.successor(x)
            );
            System.out.println(r);
        }

        // Test Correctness
        // for (int x = 0; x < universe; x++) {
        //     boolean b0 = bst.contains(x);
        //     boolean b1 = xFast1.query(x);
        //      boolean b2 = yFast.query(x);
        //     boolean b3 = xFast8.query(x);
        //  boolean b4 = concurrentYFast.query(x);

            // if (b2 != b4) {
            //     throw new RuntimeException("Existence mismatch (concurrentYFast) at x=" + x + " yFast=" + b2 + " concurrentYFast=" + b4);
            // }
            // if (b0 != b2) {
            //     throw new RuntimeException("Existence mismatch (YFAST) at x=" + x + " bst=" + b0 + " yfast=" + b2);
            // }
        //     if (b0 != b3) {
        //         throw new RuntimeException("Existence mismatch (XFast8) at x=" + x + " bst=" + b0 + " xfast=" + b3);
        //     }
        //     if (b0 != b4) {
        //         throw new RuntimeException("Existence mismatch (ConcurrentYFast) at x=" + x + " bst=" + b0 + " concurrentYFast=" + b4);
        //     }
        // }
    }
}