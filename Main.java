import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

public class Main {

    public static void main(String[] args) {

        int bits = Integer.parseInt(args[0]);
        runPerOpBenchmarks(bits);
    }

    // Generates a random mix of operations and tests total time for operations
    static void runRandomBenchmark(int bits, long universe) {
        long opCount = 1L << (bits + 2);
        long fillCount = 1L << (bits - 2);

        TestingFramework framework = new TestingFramework(universe);

        TreeSet<Long> bst = new TreeSet<>();
        RunResult bstResult = framework.benchmarkRandom("TreeSet", 1, opCount, fillCount,
                x -> bst.add(x),
                x -> bst.contains(x),
                x -> bst.ceiling(x));
        System.out.println(bstResult);

        ConcurrentYFastTree concurrentYFast = new ConcurrentYFastTree(bits);
        RunResult yFastResult = framework.benchmarkRandom("ConcurrentYFastTree", 16, opCount, fillCount,
                x -> concurrentYFast.insert(x),
                x -> concurrentYFast.query(x),
                x -> concurrentYFast.successor(x));
        System.out.println(yFastResult);
    }

    // Tests each type of operation by itself
    // Do not run this with bits set more than 28, as it breaks the maximum length of array
    static void runPerOpBenchmarks(int bits) {
        int opCount = 1 << (bits + 2);

        TestingFramework framework = new TestingFramework();
        framework.generateBenchmarkOps(opCount);

        System.out.println("TreeSet (1 thread)");
        TreeSet<Long> bst = new TreeSet<>();
        System.out.println(framework.benchmark("TreeSet", 1, "insert", x -> bst.add(x)));
        System.out.println(framework.benchmark("TreeSet", 1, "query", x -> bst.contains(x)));
        System.out.println(framework.benchmark("TreeSet", 1, "successor", x -> bst.ceiling(x)));
        System.out.println(framework.benchmark("TreeSet", 1, "delete", x -> bst.remove(x)));

        System.out.println("ConcurrentSkipListSet (16 threads)");
        ConcurrentSkipListSet<Long> skipList = new ConcurrentSkipListSet<>();
        System.out.println(framework.benchmark("ConcurrentSkipListSet", 16, "insert", x -> skipList.add(x)));
        System.out.println(framework.benchmark("ConcurrentSkipListSet", 16, "query", x -> skipList.contains(x)));
        System.out.println(framework.benchmark("ConcurrentSkipListSet", 16, "successor", x -> skipList.ceiling(x)));
        System.out.println(framework.benchmark("ConcurrentSkipListSet", 16, "delete", x -> skipList.remove(x)));

        System.out.println("ConcurrentYFastTree (16 threads)");
        ConcurrentYFastTree yFast = new ConcurrentYFastTree(31);
        System.out.println(framework.benchmark("ConcurrentYFastTree", 16, "insert", x -> yFast.insert(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTree", 16, "query", x -> yFast.query(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTree", 16, "successor", x -> yFast.successor(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTree", 16, "delete", x -> yFast.delete(x)));
    }
}
