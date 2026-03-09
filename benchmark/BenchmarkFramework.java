package benchmark;

import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import xFast.ConcurrentXFastTrie;
import xFast.XFastTrie;
import yFast.ConcurrentYFastTrieV1;
import yFast.ConcurrentYFastTrieV2;

public class BenchmarkFramework {

    public static final byte INSERT = 0;
    public static final byte QUERY = 1;
    public static final byte SUCCESSOR = 2;

    public long universe;

    public int[] benchOpKeys;

    public BenchmarkFramework(long universe) {
        this.universe = universe;
    }

    // Runs n random ops (mix of insert/query/successor) across threadCount threads
    // Fill keys are inserted before timing starts, generated on the fly
    public RunResult benchmarkRandom(String name, int threadCount, long n, long fill,
            LongOp insertFn, LongOp queryFn, LongOp successorFn) {

        // Fill phase, just populate the data structure
        long fillChunk = (fill + threadCount - 1) / threadCount;
        Thread[] fillThreads = new Thread[threadCount];
        for (int thread = 0; thread < threadCount; thread++) {
            final long start = thread * fillChunk;
            final long end = Math.min(start + fillChunk, fill);
            final long seed = 123456789 + thread;

            fillThreads[thread] = new Thread(() -> {
                Random rng = new Random(seed);
                for (long i = start; i < end; i++) {
                    insertFn.apply(rng.nextLong() & (universe - 1));
                }
            });
            fillThreads[thread].start();

        }
        for (Thread thread : fillThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Timed phase
        long elapsed = timeOps(threadCount, n, (threadIdx, start, end) -> {
            Random rng = new Random(123456789 + threadIdx);
            for (long i = start; i < end; i++) {
                long key = rng.nextLong() & (universe - 1);
                int op = (int) (rng.nextLong()) % 3;
                if (op == INSERT) {
                    insertFn.apply(key);
                } else if (op == QUERY) {
                    queryFn.apply(key);
                } else {
                    successorFn.apply(key);
                }
            }
        });
        return new RunResult(name + "_" + threadCount + "thread", n, this.universe, elapsed / 1e9);
    }

    // Run this before benchmark(), generates positive ints
    public void generateBenchmarkOps(int n) {
        Random rng = new Random(123456789);
        int mask = (int) (universe - 1);
        this.benchOpKeys = new int[n];
        for (int i = 0; i < n; i++) {
            this.benchOpKeys[i] = rng.nextInt() & mask;
        }
    }

    public RunResult benchmark(String name, int threadCount, String opType, LongOp op) {
        int n = benchOpKeys.length;
        long elapsed = timeOps(threadCount, (long) n, (t, start, end) -> {
            for (long i = start; i < end; i++) {
                op.apply(benchOpKeys[(int) i]);
            }
        });
        return new RunResult(name + "_" + opType + "_" + threadCount + "t", n, universe, elapsed / 1e9);
    }

    private long timeOps(int threadCount, long total, ChunkRunner runner) {
        long chunkSize = (total + threadCount - 1) / threadCount;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            final long start = t * chunkSize;
            final long end = Math.min(start + chunkSize, total);
            new Thread(() -> {
                try {
                    startLatch.await();
                    runner.run(threadIdx, start, end);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        long start = System.nanoTime();
        startLatch.countDown();

        boolean interrupted = false;
        while (true) {
            try {
                doneLatch.await();
                break;
            }
            catch (InterruptedException e) {
                interrupted = true;
            }
        }
        long elapsed = System.nanoTime() - start;

        if (interrupted) Thread.currentThread().interrupt();
        return elapsed;
    }

    @FunctionalInterface
    public interface ChunkRunner {
        void run(int threadIndex, long start, long end);
    }

    @FunctionalInterface
    public interface LongOp {
        void apply(long x);
    }

    public static void main(String[] args) {
        int bits = Integer.parseInt(args[0]);
        runPerOpBenchmarks(bits);
    }

    static void runRandomBenchmark(int bits) {
        long opCount = 1L << (bits + 2);
        long fillCount = 1L << (bits - 2);

        BenchmarkFramework framework = new BenchmarkFramework(1L << bits);

        TreeSet<Long> bst = new TreeSet<>();
        RunResult bstResult = framework.benchmarkRandom("TreeSet", 1, opCount, fillCount,
                x -> bst.add(x),
                x -> bst.contains(x),
                x -> bst.ceiling(x));
        System.out.println(bstResult);

        ConcurrentYFastTrieV1 concurrentYFast = new ConcurrentYFastTrieV1(bits, new XFastTrie(bits));
        RunResult yFastResult = framework.benchmarkRandom("ConcurrentYFastTrieV1", 16, opCount, fillCount,
                x -> concurrentYFast.insert(x),
                x -> concurrentYFast.query(x),
                x -> concurrentYFast.successor(x));
        System.out.println(yFastResult);
    }

    static void runPerOpBenchmarks(int bits) {
        int opCount = 1 << bits;
        long universe = 1L << 31;

        BenchmarkFramework framework = new BenchmarkFramework(universe);
        framework.generateBenchmarkOps(opCount);

        System.out.println("ConcurrentYFastTrieV1 + XFastTrie backend (16 threads), bits=" + 31);
        ConcurrentYFastTrieV1 yFastV1 = new ConcurrentYFastTrieV1(31, new XFastTrie(31));
        System.out.println(framework.benchmark("V1+XFast", 16, "insert", x -> yFastV1.insert(x)));
        System.out.println(framework.benchmark("V1+XFast", 16, "query", x -> yFastV1.query(x)));
        System.out.println(framework.benchmark("V1+XFast", 16, "successor", x -> yFastV1.successor(x)));
        System.out.println(framework.benchmark("V1+XFast", 16, "delete", x -> yFastV1.delete(x)));

        System.gc();
        System.out.println("\nConcurrentYFastTrieV2 + ConcurrentXFastTrie backend (16 threads), bits=" + 31);
        ConcurrentXFastTrie concurrentXFastTrie = new ConcurrentXFastTrie(31, 16);
        ConcurrentYFastTrieV2 yFastV2 = new ConcurrentYFastTrieV2(31, concurrentXFastTrie);
        System.out.println(framework.benchmark("V2+ConcurrentXFast", 16, "insert", x -> yFastV2.insert(x)));
        System.out.println("LFL after insert: " + concurrentXFastTrie.lowestFullLevel);
        System.out.println(framework.benchmark("V2+ConcurrentXFast", 16, "query", x -> yFastV2.query(x)));
        System.out.println(framework.benchmark("V2+ConcurrentXFast", 16, "successor", x -> yFastV2.successor(x)));
        System.out.println(framework.benchmark("V2+ConcurrentXFast", 16, "delete", x -> yFastV2.delete(x)));
        System.out.println("LFL after delete: " + concurrentXFastTrie.lowestFullLevel);
    }
}
