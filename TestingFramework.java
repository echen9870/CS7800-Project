import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class TestingFramework {

    public static final byte INSERT = 0;
    public static final byte QUERY = 1;
    public static final byte SUCCESSOR = 2;

    public long universe;

    public int[] benchOpKeys;

    public TestingFramework() {

    }

    public TestingFramework(long universe) {
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
        this.benchOpKeys = new int[n];
        for (int i = 0; i < n; i++) {
            this.benchOpKeys[i] = rng.nextInt() & Integer.MAX_VALUE;
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
}
