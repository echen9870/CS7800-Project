import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class TestCorrectnessConcurrent {

    // This runs a single threaded application, and puts the expected answers in expectedBool and expectedInt, we use these two arrays
    // Call before runConcurrentAndCheck function
    public static void runSequentialExpected(int bits, long[] keys, byte[] types, boolean[] expectedBool, Long[] expectedLong) {
        YFastTree yfast = new YFastTree(bits);

        for (int i = 0; i < keys.length; i++) {
            long key = keys[i];

            if (types[i] == OpsGenerator.INSERT) {
                yfast.insert(key);
            } else if (types[i] == OpsGenerator.QUERY) {
                expectedBool[i] = yfast.query(key);
            } else {
                expectedLong[i] = yfast.successor(key);
            }
        }
    }

    public static void runConcurrentAndCheck(int bits, long[] keys, byte[] types, boolean[] expectedBool, Long[] expectedLong, int threadCount) {
        ConcurrentYFastTree concurrent = new ConcurrentYFastTree(bits);

        AtomicInteger nextIndex = new AtomicInteger(0);
        AtomicInteger turn = new AtomicInteger(0);
        AtomicReference<RuntimeException> error = new AtomicReference<>(null);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int threadId = 0; threadId < threadCount; threadId++) {
            new Thread(() -> {
                try {
                    startLatch.await();

                    while (error.get() == null) {
                        int i = nextIndex.getAndIncrement();
                        if (i >= keys.length) break;

                        while (turn.get() != i) {
                            if (error.get() != null) return;
                            LockSupport.parkNanos(1L);
                        }

                        long key = keys[i];

                        if (types[i] == OpsGenerator.INSERT) {
                            concurrent.insert(key);
                        } else if (types[i] == OpsGenerator.QUERY) {
                            if (concurrent.query(key) != expectedBool[i]) {
                                error.compareAndSet(null, new RuntimeException("QUERY mismatch at op=" + i + " key=" + key));
                                return;
                            }
                        } else {
                            if (!Objects.equals(concurrent.successor(key), expectedLong[i])) {
                                error.compareAndSet(null, new RuntimeException("SUCCESSOR mismatch at op=" + i + " key=" + key));
                                return;
                            }
                        }

                        turn.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    error.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();

        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        RuntimeException e = error.get();
        if (e != null) throw e;
    }

    public static void main(String[] args) {
        int bits = 16;
        long universe = 1L << bits;

        int opCount = 1 << 20;
        int threadCount = 1;

        OpsGenerator gen = new OpsGenerator(universe, opCount, 123456789, 80, 10, 10);

        boolean[] expectedBool = new boolean[opCount];
        Long[] expectedLong = new Long[opCount];
        Arrays.fill(expectedLong, null);

        try {
            runSequentialExpected(bits, gen.keys, gen.types, expectedBool, expectedLong);
            System.out.println("Built expected answers with YFastTree: ops=" + opCount);

            runConcurrentAndCheck(bits, gen.keys, gen.types, expectedBool, expectedLong, threadCount);
            System.out.println("Concurrent correctness OK vs YFastTree: ops=" + opCount + " threads=" + threadCount);
        } catch (RuntimeException e) {
            System.out.println("Correctness FAILED: " + e.getMessage());
            throw e;
        }
    }
}
