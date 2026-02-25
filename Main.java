import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

public class Main {
    public static void main(String[] args) {

        int bits = 24;
        int universe = 1 << bits;

        // Build framework and generate numbers
        TestingFramework framework = new TestingFramework(universe);
        framework.generateOps(1 << 28, 1 << 20);

        

        List<RunResult> results = new ArrayList<>();

        // BST
        TreeSet<Integer> bst = new TreeSet<>();
        {
            RunResult r = framework.runRandom(
                    "BST",
                    1,
                    (x) -> bst.add(x),
                    (x) -> bst.contains(x),
                    (x) -> bst.ceiling(x)
            );

            results.add(r);
        }

        // XFast
        XFastTree xFast = new XFastTree(bits);
        {
            RunResult r = framework.runRandom(
                    "XFastTree",
                    1,
                    (x) -> xFast.insert(x),
                    (x) -> xFast.query(x),
                    (x) -> xFast.successor(x)
            );

            results.add(r);
        }

        // YFast
        YFastTree yFast = new YFastTree(bits);
        {
            RunResult r = framework.runRandom(
                    "YFastTree",
                    1,
                    (x) -> yFast.insert(x),
                    (x) -> yFast.query(x),
                    (x) -> yFast.successor(x)
            );

            results.add(r);
        }

        // Test Correctness
        for (int x = 0; x < universe; x++) {
            boolean b0 = bst.contains(x);
            boolean b1 = xFast.query(x);
            boolean b2 = yFast.query(x);

            if (b0 != b1) {
                throw new RuntimeException("Existence mismatch (XFAST) at x=" + x + " bst=" + b0 + " xfast=" + b1);
            }
            if (b0 != b2) {
                throw new RuntimeException("Existence mismatch (YFAST) at x=" + x + " bst=" + b0 + " yfast=" + b2);
            }
        }

        // Print results
        for (RunResult r : results) {
            System.out.println(r);
        }
    }
}
