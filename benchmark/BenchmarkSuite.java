package benchmark;

import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import xFast.ConcurrentXFastTrie;
import xFast.XFastTrie;
import yFast.ConcurrentYFastTrieV1;
import yFast.ConcurrentYFastTrieV2;

public class BenchmarkSuite {

    static final int DEFAULT_THREADS = 16;
    static final long OP_COUNT = 1L << 20;  // ~1M ops, default for tests 2/4/5
    static final long UNIVERSE = 1L << 32;  // full 32-bit universe for tests 2/4/5

    static void header(String msg)    { System.out.println("\n=== " + msg + " ==="); }
    static void subheader(String msg) { System.out.println("-- " + msg + " --"); }

    // random mix of insert/query/successor with a fill phase before timing
    static void runRandom(BenchmarkFramework fw, String name, int threads, long n,
            BenchmarkFramework.LongOp insert,
            BenchmarkFramework.LongOp query,
            BenchmarkFramework.LongOp successor) {
        System.out.println(fw.benchmarkRandom(name, threads, n, n / 4,
                insert, query, successor));
    }

    // each op type run in isolation, insert first so the structure has data for the rest
    static void runPerOp(BenchmarkFramework fw, String name, int threads, long n,
            BenchmarkFramework.LongOp insert,
            BenchmarkFramework.LongOp query,
            BenchmarkFramework.LongOp successor,
            BenchmarkFramework.LongOp predecessor,
            BenchmarkFramework.LongOp delete) {
        System.out.println(fw.benchmark(name, threads, n, "insert",      insert));
        System.out.println(fw.benchmark(name, threads, n, "query",       query));
        System.out.println(fw.benchmark(name, threads, n, "successor",   successor));
        System.out.println(fw.benchmark(name, threads, n, "predecessor", predecessor));
        System.out.println(fw.benchmark(name, threads, n, "delete",      delete));
    }

    static void runAll(BenchmarkFramework fw, String name, int threads, long n,
            BenchmarkFramework.LongOp insert,
            BenchmarkFramework.LongOp query,
            BenchmarkFramework.LongOp successor,
            BenchmarkFramework.LongOp predecessor,
            BenchmarkFramework.LongOp delete) {
        subheader(name + "  threads=" + threads);
        runRandom(fw, name + "_random", threads, n, insert, query, successor);
        runPerOp(fw,  name + "_perOp",  threads, n, insert, query, successor, predecessor, delete);
    }

    // Test 1: sweep universe/op counts {2^16, 2^20, 2^24}
    // compares BST / XFast / ConcurrentXFast / YFastV1 / YFastV2
    // concurrent data structures are given 16 threads
    public static void test1() {
        header("Test1: sweep bits={16,20,24}");

        for (int bits : new int[]{ 14, 16, 18 }) {
            long n = 1L << bits;
            BenchmarkFramework fw = new BenchmarkFramework(1L << bits);
            subheader("bits=" + bits + "  ops = 2^" + bits + " = " + n);

            TreeSet<Long> bst = new TreeSet<>();
            runAll(fw, "BST", 1, n,
                            x -> bst.add(x), x -> bst.contains(x), x -> bst.ceiling(x),
                            x -> bst.floor(x), x -> bst.remove(x));

            ConcurrentSkipListSet<Long> skipList = new ConcurrentSkipListSet<>();
            runAll(fw, "SkipList", 16, n,
                    x -> skipList.add(x), x -> skipList.contains(x), x -> skipList.ceiling(x),
                    x -> skipList.floor(x), x -> skipList.remove(x));

            XFastTrie xfast = new XFastTrie(bits);
            runAll(fw, "XFastTrie", 16, n,
                    x -> xfast.insert(x), x -> xfast.query(x), x -> xfast.successor(x),
                    x -> xfast.predecessor(x), x -> xfast.delete(x));

            ConcurrentXFastTrie cxfast = new ConcurrentXFastTrie(bits, 16);
            runAll(fw, "ConcurrentXFastTrie", 16, n,
                    x -> cxfast.insert(x), x -> cxfast.query(x), x -> cxfast.successor(x),
                    x -> cxfast.predecessor(x), x -> cxfast.delete(x));

            ConcurrentYFastTrieV1 yv1 = new ConcurrentYFastTrieV1(bits, new XFastTrie(bits));
            runAll(fw, "YFastV1", 16, n,
                    x -> yv1.insert(x), x -> yv1.query(x), x -> yv1.successor(x),
                    x -> yv1.predecessor(x), x -> yv1.delete(x));

            ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits,
                    new ConcurrentXFastTrie(bits, 16));
            runAll(fw, "YFastV2", 16, n,
                    x -> yv2.insert(x), x -> yv2.query(x), x -> yv2.successor(x),
                    x -> yv2.predecessor(x), x -> yv2.delete(x));
        }
    }

    // Test 2: universe sizes > 2 ** 31, BST vs YFastV1 vs YFastV2
    // concurrent data structures are given 16 threads
    public static void test2() {
        int bits = 32;
        header("Test2: bits=" + bits + "  universe=2^" + bits);
        BenchmarkFramework fw = new BenchmarkFramework(1L << 32);

        ConcurrentSkipListSet<Long> bst = new ConcurrentSkipListSet<>();
        runAll(fw, "BST", 16, OP_COUNT,
                x -> bst.add(x), x -> bst.contains(x), x -> bst.ceiling(x),
                x -> bst.floor(x), x -> bst.remove(x));

        ConcurrentYFastTrieV1 yv1 = new ConcurrentYFastTrieV1(bits, new XFastTrie(bits));
        runAll(fw, "YFastV1", 16, OP_COUNT,
                x -> yv1.insert(x), x -> yv1.query(x), x -> yv1.successor(x),
                x -> yv1.predecessor(x), x -> yv1.delete(x));

        ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits,
                new ConcurrentXFastTrie(bits, 16));
        runAll(fw, "YFastV2", 16, OP_COUNT,
                x -> yv2.insert(x), x -> yv2.query(x), x -> yv2.successor(x),
                x -> yv2.predecessor(x), x -> yv2.delete(x));
    }

    // Test 3: large universe passed at runtime (bits > 32)
    // compares YFastV2 with LFL bounded by 3*threads vs YFastV2 with no LFL cap
    public static void test3(int bits) {
        long universe = 1L << bits;
        header("Test3: bits=" + bits + "  universe=2^" + bits);
        BenchmarkFramework fw = new BenchmarkFramework(universe);

        // bounded: maxLFL capped at log2(3 * threads), limits partition lock count
        ConcurrentYFastTrieV2 bounded = new ConcurrentYFastTrieV2(bits,
                new ConcurrentXFastTrie(bits, DEFAULT_THREADS));
        runAll(fw, "YFastV2_LFLbounded", DEFAULT_THREADS, OP_COUNT,
                x -> bounded.insert(x), x -> bounded.query(x), x -> bounded.successor(x),
                x -> bounded.predecessor(x), x -> bounded.delete(x));

        // unbounded: LFL can grow all the way to bits-1, more partition locks but finer grained
        ConcurrentYFastTrieV2 unbounded = new ConcurrentYFastTrieV2(bits,
                new ConcurrentXFastTrie(bits, DEFAULT_THREADS, bits - 1));
        runAll(fw, "YFastV2_LFLunbounded", DEFAULT_THREADS, OP_COUNT,
                x -> unbounded.insert(x), x -> unbounded.query(x), x -> unbounded.successor(x),
                x -> unbounded.predecessor(x), x -> unbounded.delete(x));
    }

    // Test 4: sweep bucket sizes (1x to 128x bits) for YFastV1 and YFastV2, universe=2^32
    public static void test4() {
        int bits = 32;
        header("Test4: bucket-size sweep  bits=" + bits + "  universe=2^" + bits);
        int[] multipliers = {1, 2, 4, 8, 16, 32, 64, 128};
        BenchmarkFramework fw = new BenchmarkFramework(UNIVERSE);

        for (int mult : multipliers) {
            int bucketSize = mult * bits;
            subheader("bucket = " + mult + " × bits = " + bucketSize);

            ConcurrentYFastTrieV1 yv1 = new ConcurrentYFastTrieV1(bits,
                    new XFastTrie(bits), bucketSize);
            runAll(fw, "YFastV1_b" + bucketSize, DEFAULT_THREADS, OP_COUNT,
                    x -> yv1.insert(x), x -> yv1.query(x), x -> yv1.successor(x),
                    x -> yv1.predecessor(x), x -> yv1.delete(x));

            ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits,
                    new ConcurrentXFastTrie(bits, DEFAULT_THREADS), bucketSize);
            runAll(fw, "YFastV2_b" + bucketSize, DEFAULT_THREADS, OP_COUNT,
                    x -> yv2.insert(x), x -> yv2.query(x), x -> yv2.successor(x),
                    x -> yv2.predecessor(x), x -> yv2.delete(x));
        }
    }

    // Test 5: sweep thread counts (1 to nCPU) for YFastV1 and YFastV2, universe=2^32
    public static void test5() {
        int bits = 32;
        int maxThreads = Runtime.getRuntime().availableProcessors();
        header("ThreadTest: bits=" + bits + "  maxThreads=" + maxThreads);
        BenchmarkFramework fw = new BenchmarkFramework(UNIVERSE);

        for (int threads = 1; threads <= maxThreads; threads *= 2) {
            subheader("threads=" + threads);

            ConcurrentYFastTrieV1 yv1 = new ConcurrentYFastTrieV1(bits, new XFastTrie(bits));
            runAll(fw, "YFastV1_t" + threads, threads, OP_COUNT,
                    x -> yv1.insert(x), x -> yv1.query(x), x -> yv1.successor(x),
                    x -> yv1.predecessor(x), x -> yv1.delete(x));

            ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits,
                    new ConcurrentXFastTrie(bits, threads));
            runAll(fw, "YFastV2_t" + threads, threads, OP_COUNT,
                    x -> yv2.insert(x), x -> yv2.query(x), x -> yv2.successor(x),
                    x -> yv2.predecessor(x), x -> yv2.delete(x));
        }
    }
}
