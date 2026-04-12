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

    // Generates keys on-the-fly per thread — same deterministic keys every run
    public RunResult benchmark(String name, int threadCount, long n, String opType, LongOp op) {
        long mask = universe - 1;
        long elapsed = timeOps(threadCount, n, (t, start, end) -> {
            Random rng = new Random(123456789L + t);
            for (long i = start; i < end; i++) {
                op.apply(rng.nextLong() & mask);
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
        if (args.length == 0) { printUsage(); return; }
        int testNum = Integer.parseInt(args[0]);
        switch (testNum) {
            case 1 -> {
                int bits = (args.length >= 2) ? Integer.parseInt(args[1]) : 32;
                long ops = (args.length >= 3) ? Long.parseLong(args[2]) : 1L << 20;
                BenchmarkSuite.threadScalability(bits, ops);
            }
            case 2 -> {
                int bits = (args.length >= 2) ? Integer.parseInt(args[1]) : 32;
                long ops = (args.length >= 3) ? Long.parseLong(args[2]) : 1L << 20;
                BenchmarkSuite.threadScalabilityVsConcurrentSkipList(bits, ops);
            }
        }
    }

    static void printUsage() {
        System.out.println("Usage: java Main <testNum> [args]");
        System.out.println("  1 [bits] [ops]   Thread scalability: YFastV1 vs YFastV2 (1-64 threads)");
        System.out.println("  2 [bits] [ops]   Thread scalability: YFastV1 vs YFastV2 vs ConcurrentSkipList (1-64 threads)");
    }
}
