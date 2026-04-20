package benchmark;

import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import xFast.ConcurrentXFastTrie;
import xFast.XFastTrie;
import yFast.ConcurrentYFastTrieV1;
import yFast.ConcurrentYFastTrieV2;
import java.util.Random;
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

    static void warmup(int bits){
        ConcurrentYFastTrieV1 warmV1 = new ConcurrentYFastTrieV1(bits, new XFastTrie(bits));
        ConcurrentYFastTrieV2 warmV2 = new ConcurrentYFastTrieV2(bits, new ConcurrentXFastTrie(bits, 4));
        Random rng = new Random(42);
        long universe = 1L << bits;
        for (int i = 0; i < 200_000; i++) {
                long k = rng.nextLong() & (universe - 1);
                warmV1.insert(k); warmV1.query(k); warmV1.successor(k); warmV1.predecessor(k); warmV1.delete(k);
                warmV2.insert(k); warmV2.query(k); warmV2.successor(k); warmV2.predecessor(k); warmV2.delete(k);
        }
        System.gc();
        try{
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Test 1 : Thread Scalability on YFastV1 and YFastV2 with parameter
    // bits : number of bits in the universe
    // ops : number of operations = 2^20 = 1048576
    public static void threadScalability(int  bits, long ops){
        warmup(bits);
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe); 
        header("Thread Scalability: bits = " + bits + " ops = " + ops);

        for(int threads = 1; threads <= 64; threads *= 2){
            subheader("Threads = " + threads);
            ConcurrentYFastTrieV1 yv1 = new ConcurrentYFastTrieV1(bits, new XFastTrie(bits));
            runAll(fw, "YFastV1_t" + threads, threads, ops,
                    x -> yv1.insert(x), x -> yv1.query(x), x -> yv1.successor(x),
                    x -> yv1.predecessor(x), x -> yv1.delete(x));

            ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits,
                    new ConcurrentXFastTrie(bits, threads));
            runAll(fw, "YFastV2_t" + threads, threads, ops,
                    x -> yv2.insert(x), x -> yv2.query(x), x -> yv2.successor(x),
                    x -> yv2.predecessor(x), x -> yv2.delete(x));
        }
    }

    // Test 2 : Thread Scalability on YFastV1 and YFastV2 vs ConcurrentSkipList with parameter
    // bits : number of bits in the universe
    // ops : number of operations = 2^20 = 1048576
    public static void threadScalabilityVsConcurrentSkipList(int bits, long ops) {
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        header("Thread Scalability vs SkipList: bits = " + bits + " ops = " + ops);
        for (int threads = 1; threads <= 64; threads *= 2) {
                subheader("Threads = " + threads);
                ConcurrentSkipListSet<Long> skipList = new ConcurrentSkipListSet<>();
                runAll(fw, "SkipList_t" + threads, threads, ops,
                        x -> skipList.add(x), x -> skipList.contains(x), x -> skipList.ceiling(x),
                        x -> skipList.floor(x), x -> skipList.remove(x));
                ConcurrentYFastTrieV1 yv1 = new ConcurrentYFastTrieV1(bits, new XFastTrie(bits));
                runAll(fw, "YFastV1_t" + threads, threads, ops,
                        x -> yv1.insert(x), x -> yv1.query(x), x -> yv1.successor(x),
                        x -> yv1.predecessor(x), x -> yv1.delete(x));
                ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits,
                        new ConcurrentXFastTrie(bits, threads));
                runAll(fw, "YFastV2_t" + threads, threads, ops,
                        x -> yv2.insert(x), x -> yv2.query(x), x -> yv2.successor(x),
                        x -> yv2.predecessor(x), x -> yv2.delete(x));
        }
    }
     

     // Test 3 : LFL Lock Bounding vs Unbounding over threads
     // bits : number of bits in the universe
     // ops : number of operations = 2^20 = 1048576
    public static void lflBoundedVsUnboundedOverThreads(int bits, long ops) {
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        header("LFL Bounded vs Unbounded: bits = " + bits + " ops = " + ops);
        for (int threads = 1; threads <= 64; threads *= 2) {
                subheader("Threads = " + threads);
                // Bounded: maxLFL capped at ~3*threads (default behavior)
                ConcurrentXFastTrie boundedXFast = new ConcurrentXFastTrie(bits, threads);
                ConcurrentYFastTrieV2 bounded = new ConcurrentYFastTrieV2(bits, boundedXFast);
                runAll(fw, "YFastV2_bounded_t" + threads, threads, ops,
                        x -> bounded.insert(x), x -> bounded.query(x), x -> bounded.successor(x),
                        x -> bounded.predecessor(x), x -> bounded.delete(x));

                // Unbounded: LFL can grow up to bits-1
                ConcurrentXFastTrie unboundedXFast = new ConcurrentXFastTrie(bits, threads, bits - 1);
                ConcurrentYFastTrieV2 unbounded = new ConcurrentYFastTrieV2(bits, unboundedXFast);
                runAll(fw, "YFastV2_unbounded_t" + threads, threads, ops,
                        x -> unbounded.insert(x), x -> unbounded.query(x), x -> unbounded.successor(x),
                        x -> unbounded.predecessor(x), x -> unbounded.delete(x));
        }
    }       

    // Test 4 : Unbounded LFL on  YFastV2 Ops Sweep
    // bits : number of bits in the universe
    public static void v2OpsSweeping(int bits) {
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        int threads = 64;
        long[] opsList = {1L << 20, 1L << 22, 1L << 23, 1L << 24, 1L << 25, 1L << 26, 1L << 27, 1L << 28, 1L << 29};
        header("YFastV2 Ops Sweep: bits = " + bits + " threads = " + threads);
        for (long ops : opsList) {
            subheader("ops = " + ops + " (2^" + Long.numberOfTrailingZeros(ops) + ")");
            ConcurrentXFastTrie xfast = new ConcurrentXFastTrie(bits, threads, bits);
            ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits, xfast);
            System.out.println(fw.benchmark("YFastV2_ops" + ops, threads, ops, "insert",
                    x -> yv2.insert(x)));
            System.out.println("  LFL after insert: " + xfast.lowestFullLevel
                    + "  locks: " + xfast.locks.length
                    + "  size: " + xfast.size.get());
        }
    }

    // Test 5 : Ops Sweep on ConcurrentSkipList
    // bits : number of bits in the universe
    public static void v2OpsSweepingVsConcurrentSkipList(int bits) {
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        int threads = 64;
        long[] opsList = {1L << 20, 1L << 22, 1L << 23, 1L << 24, 1L << 25, 1L << 26, 1L << 27, 1L << 28, 1L << 29};
        header("SkipList Ops Sweep: bits = " + bits + " threads = " + threads);
        for (long ops : opsList) {
            subheader("ops = " + ops + " (2^" + Long.numberOfTrailingZeros(ops) + ")");
            ConcurrentSkipListSet<Long> skipList = new ConcurrentSkipListSet<>();
            System.out.println(fw.benchmark("SkipList_ops" + ops, threads, ops, "insert",
                    x -> skipList.add(x)));
        }
    }

    // Test 6: Ops Sweep on YFastV2 bounded LFL
    // bits : number of bits in the universe
    public static void v2BoundedLFLOpsSweeping(int bits) {
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        int threads = 64;
        long[] opsList = {1L << 20, 1L << 22, 1L << 23, 1L << 24, 1L << 25, 1L << 26, 1L << 27, 1L << 28, 1L << 29};
        header("YFastV2 Bounded LFL Ops Sweep: bits = " + bits + " threads = " + threads);
        for (long ops : opsList) {
            subheader("ops = " + ops + " (2^" + Long.numberOfTrailingZeros(ops) + ")");
            ConcurrentXFastTrie xfast = new ConcurrentXFastTrie(bits, threads);
            ConcurrentYFastTrieV2 yv2 = new ConcurrentYFastTrieV2(bits, xfast);
            System.out.println(fw.benchmark("YFastV2_ops" + ops, threads, ops, "insert",
                    x -> yv2.insert(x)));
            System.out.println("  LFL after insert: " + xfast.lowestFullLevel
                    + "  locks: " + xfast.locks.length
                    + "  size: " + xfast.size.get());
        }
    }

    // Test 7: Unified ops sweep — V2-bounded vs V2-unbounded vs SkipList
    // at 64 threads, insert+query, varying N.
    public static void unifiedOpsSweep(int bits) {
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        int threads = 64;
        long[] opsList = {1L << 21, 1L << 22, 1L << 23, 1L << 24, 1L << 25, 1L << 26, 1L << 27};

        header("Unified Ops Sweep: bits=" + bits + " threads=" + threads);

        for (long ops : opsList) {
                subheader("ops = 2^" + Long.numberOfTrailingZeros(ops));
                
                // // --- V2 Bounded ---
                // {
                // ConcurrentXFastTrie xfast = new ConcurrentXFastTrie(bits, threads);
                // ConcurrentYFastTrieV2 y = new ConcurrentYFastTrieV2(bits, xfast);
                // System.out.println(fw.benchmark("V2bounded_" + ops, threads, ops, "insert",
                //         x -> y.insert(x)));
                // System.out.println(fw.benchmark("V2bounded_" + ops, threads, ops, "query",
                //         x -> y.query(x)));
                // }
                
                // --- V2 Unbounded ---
                {
                ConcurrentXFastTrie xfast = new ConcurrentXFastTrie(bits, threads, bits - 1);
                ConcurrentYFastTrieV2 y = new ConcurrentYFastTrieV2(bits, xfast);
                System.out.println(fw.benchmark("V2unbounded_" + ops, threads, ops, "insert",
                        x -> y.insert(x)));
                System.out.println(fw.benchmark("V2unbounded_" + ops, threads, ops, "query",
                        x -> y.query(x)));
                }
                
                // --- SkipList ---
                {
                ConcurrentSkipListSet<Long> sl = new ConcurrentSkipListSet<>();
                System.out.println(fw.benchmark("SkipList_" + ops, threads, ops, "insert",
                        x -> sl.add(x)));
                System.out.println(fw.benchmark("SkipList_" + ops, threads, ops, "query",
                        x -> sl.contains(x)));
                }
        }
    }

    // Test 8: Unified thread sweep - V2-bounded vs V2-unbounded vs Skiplist
    public static void unifiedThreadSweep(int bits, long ops) {
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        header("Unified threads Sweep at : bits=" + bits + " ops=" + ops);
        for (int threads = 1; threads <= 64; threads *= 2) {
                subheader("threads = " + threads);
                // // --- V2 Bounded ---
                // {
                // ConcurrentXFastTrie xfast = new ConcurrentXFastTrie(bits, threads);
                // ConcurrentYFastTrieV2 y = new ConcurrentYFastTrieV2(bits, xfast);
                // System.out.println(fw.benchmark("V2bounded_" + ops, threads, ops, "insert",
                //         x -> y.insert(x)));
                // System.out.println(fw.benchmark("V2bounded_" + ops, threads, ops, "query",
                //         x -> y.query(x)));
                // }
                
                // --- V2 Unbounded ---
                {
                ConcurrentXFastTrie xfast = new ConcurrentXFastTrie(bits, threads, bits - 1);
                ConcurrentYFastTrieV2 y = new ConcurrentYFastTrieV2(bits, xfast);
                System.out.println(fw.benchmark("V2unbounded_" + ops, threads, ops, "insert",
                        x -> y.insert(x)));
                System.out.println(fw.benchmark("V2unbounded_" + ops, threads, ops, "query",
                        x -> y.query(x)));
                }
                
                // --- SkipList ---
                {
                ConcurrentSkipListSet<Long> sl = new ConcurrentSkipListSet<>();
                System.out.println(fw.benchmark("SkipList_" + ops, threads, ops, "insert",
                        x -> sl.add(x)));
                System.out.println(fw.benchmark("SkipList_" + ops, threads, ops, "query",
                        x -> sl.contains(x)));
                }
        }
              
    }

    // Test 9: SubUniverse Bucket Size Sweep for V2
    // Sweeps bucket size 
    public static void bucketSizeSweep(int bits, long ops){
        long universe = 1L << bits;
        BenchmarkFramework fw = new BenchmarkFramework(universe);
        int threads = 16;
        int[] multipliers = {1, 2, 4, 8, 16, 32, 64, 128};


        header("Bucket Size Sweep: bits=" + bits + " threads=" + threads + " ops=" + ops);

        for (int mult : multipliers) {
                int bucketSize = mult * bits;
                subheader("bucket = " + mult + "x bits = " + bucketSize);
                // --- V2 ---
                {
                ConcurrentXFastTrie xfast = new ConcurrentXFastTrie(bits, threads);
                ConcurrentYFastTrieV2 y = new ConcurrentYFastTrieV2(bits, xfast, bucketSize);
                System.out.println(fw.benchmark("V2_b" + bucketSize, threads, ops, "insert",
                        x -> y.insert(x)));
                System.out.println(fw.benchmark("V2_b" + bucketSize, threads, ops, "query",
                        x -> y.query(x)));
                System.out.println(fw.benchmark("V2_b" + bucketSize, threads, ops, "successor",
                        x -> y.successor(x)));
                System.out.println(fw.benchmark("V2_b" + bucketSize, threads, ops, "predecessor",
                        x -> y.predecessor(x)));
                System.out.println(fw.benchmark("V2_b" + bucketSize, threads, ops, "delete",
                        x -> y.delete(x)));
                }

                System.gc();
                try { Thread.sleep(200); } catch (InterruptedException e) {}
        }
    }
}
