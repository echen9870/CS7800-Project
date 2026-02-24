import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

public class Main {
    public static void main(String[] args) {

        int bits = 26;
        int universe = 1 << bits;

        // Build framework and generate numbers
        TestingFramework framework = new TestingFramework(universe);
        framework.generateNumbers(1 << 26);

        List<RunResult> results = new ArrayList<>();

        XFastTree xfast = new XFastTree(bits);

        // XFast
        {
            
            RunResult r = framework.run(
                    "XFastTree",
                    1,
                    (x) -> xfast.insert(x),
                    (x) -> xfast.query(x),
                    (x) -> xfast.successor(x)
            );

            results.add(r);
        }

        // XFast 4 threads
        {
            RunResult r = framework.run(
                    "XFastTree",
                    4,
                    (x) -> xfast.insert(x),
                    (x) -> xfast.query(x),
                    (x) -> xfast.successor(x)
            );

            results.add(r);
        }

        // BST
        // {
        //     TreeSet<Integer> set = new TreeSet<>();
        //     RunResult r = framework.run(
        //             "BST",
        //             1,
        //             (x) -> set.add(x),
        //             (x) -> set.contains(x),
        //             (x) -> set.ceiling(x)
        //     );

        //     results.add(r);
        // }

        // ConcurrentSkipListSet 1 thread
        // {
        //     ConcurrentSkipListSet<Integer> set = new ConcurrentSkipListSet<>();

        //     RunResult r = framework.run(
        //             "ConcurrentSkipListSet",
        //             1,
        //             (x) -> set.add(x),
        //             (x) -> set.contains(x),
        //             (x) -> set.ceiling(x)
        //     );

        //     results.add(r);
        // }

        // ConcurrentSkipListSet 4 threads
        // {
        //     ConcurrentSkipListSet<Integer> set = new ConcurrentSkipListSet<>();

        //     RunResult r = framework.run(
        //             "ConcurrentSkipListSet",
        //             4,
        //             (x) -> set.add(x),
        //             (x) -> set.contains(x),
        //             (x) -> set.ceiling(x)
        //     );

        //     results.add(r);
        // }

        // Print results
        for (RunResult r : results) {
            System.out.println(r);
        }
    }
}
