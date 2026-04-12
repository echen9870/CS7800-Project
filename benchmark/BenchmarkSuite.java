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

    // Test 1 : Thread Scalability on YFastV1 and YFastV2 with parameter
    // bits : number of bits in the universe
    // ops : number of operations = 2^20 = 1048576
    public static void threadScalability(int  bits, long ops){
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

     

    
}
