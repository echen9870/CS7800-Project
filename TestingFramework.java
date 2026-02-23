import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class TestingFramework {
    public int universe;

    public double insertTimeSeconds;
    public double queryTimeSeconds;
    public double successorTimeSeconds;

    public int[] nums;

    public TestingFramework(int universe) {
        this.universe = universe;

        this.insertTimeSeconds = 0.0;
        this.queryTimeSeconds = 0.0;
        this.successorTimeSeconds = 0.0;

        this.nums = new int[0];
    }

    public void generateNumbers(int n) {
        Random rng = new Random(1234567);
        this.nums = new int[n];
        for (int i = 0; i < n; i++) {
            this.nums[i] = rng.nextInt(this.universe);
        }
    }

    public RunResult run(String name, int threadCount, IntOp insertFn, BoolOp queryFn, IntResultOp successorFn) {
        this.insertTimeSeconds = runPhase(threadCount, (startIndex, endIndex) -> {
            for (int i = startIndex; i < endIndex; i++) {
                insertFn.apply(this.nums[i]);
            }
        });

        this.queryTimeSeconds = runPhase(threadCount, (startIndex, endIndex) -> {
            for (int i = startIndex; i < endIndex; i++) {
                queryFn.apply(this.nums[i]);
            }
        });

        this.successorTimeSeconds = runPhase(threadCount, (startIndex, endIndex) -> {
            for (int i = startIndex; i < endIndex; i++) {
                successorFn.apply(this.nums[i]);
            }
        });

        return new RunResult(
                name + "_" + threadCount + "thread",
                this.nums.length,
                this.universe,
                this.insertTimeSeconds,
                this.queryTimeSeconds,
                this.successorTimeSeconds
        );
    }

    public double runPhase(int threadCount, RangeOp workFn) {
        int n = this.nums.length;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        Thread[] threads = new Thread[threadCount];

        for (int threadId = 0; threadId < threadCount; threadId++) {
            int startIndex = (int) (((long) threadId) * n / threadCount);
            int endIndex = (int) (((long) (threadId + 1)) * n / threadCount);

            threads[threadId] = new Thread(() -> {
                try {
                    startLatch.await();
                    workFn.apply(startIndex, endIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });

            threads[threadId].start();
        }

        long startTime = System.nanoTime();
        startLatch.countDown();

        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        return (endTime - startTime) / 1e9;
    }

    @FunctionalInterface
    public interface IntOp {
        void apply(int x);
    }

    @FunctionalInterface
    public interface BoolOp {
        boolean apply(int x);
    }

    @FunctionalInterface
    public interface IntResultOp {
        Integer apply(int x);
    }

    @FunctionalInterface
    public interface RangeOp {
        void apply(int startIndex, int endIndex);
    }
}