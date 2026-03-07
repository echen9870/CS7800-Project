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

        ConcurrentYFastTrie concurrentYFast = new ConcurrentYFastTrie(bits, new XFastTrie(bits));
        RunResult yFastResult = framework.benchmarkRandom("ConcurrentYFastTrie", 16, opCount, fillCount,
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

        System.out.println("ConcurrentYFastTrie + XFastTrie backend (16 threads)");
        ConcurrentYFastTrie yFast = new ConcurrentYFastTrie(31, new XFastTrie(31));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+XFast", 16, "insert", x -> yFast.insert(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+XFast", 16, "query", x -> yFast.query(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+XFast", 16, "successor", x -> yFast.successor(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+XFast", 16, "delete", x -> yFast.delete(x)));

        System.out.println("ConcurrentYFastTrie + ConcurrentXFastTrie backend (16 threads)");
        ConcurrentYFastTrie yFastCX = new ConcurrentYFastTrie(31, new ConcurrentXFastTrie(31));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+ConcurrentXFast", 16, "insert", x -> yFastCX.insert(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+ConcurrentXFast", 16, "query", x -> yFastCX.query(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+ConcurrentXFast", 16, "successor", x -> yFastCX.successor(x)));
        System.out.println(framework.benchmark("ConcurrentYFastTrie+ConcurrentXFast", 16, "delete", x -> yFastCX.delete(x)));
    }
}
