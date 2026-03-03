import java.util.Random;

public final class OpsGenerator {

    public static final byte INSERT = 0;
    public static final byte QUERY = 1;
    public static final byte SUCCESSOR = 2;
    public static final byte DELETE = 3;
    public static final byte PREDECESSOR = 4;

    public long universe;
    public int size;
    public long seed;

    public int insertPercent;
    public int queryPercent;
    public int successorPercent;
    public int deletePercent;
    public int predecessorPercent;

    public long[] keys;
    public byte[] types;

    public OpsGenerator(long universe, int size) {
        this(universe, size, 1234567L, 40, 15, 15, 15, 15);
    }

    public OpsGenerator(long universe, int size, long seed,
            int insertPercent, int queryPercent, int successorPercent,
            int deletePercent, int predecessorPercent) {
        this.universe = universe;
        this.size = size;
        this.seed = seed;

        this.insertPercent = insertPercent;
        this.queryPercent = queryPercent;
        this.successorPercent = successorPercent;
        this.deletePercent = deletePercent;
        this.predecessorPercent = predecessorPercent;

        this.keys = new long[size];
        this.types = new byte[size];
        this.generate();
    }

    public void generate() {
        int threadCount = Runtime.getRuntime().availableProcessors();
        int chunkSize = (this.size + threadCount - 1) / threadCount;
        int insertThreshold = this.insertPercent;
        int queryThreshold = insertThreshold + this.queryPercent;
        int successorThreshold = queryThreshold + this.successorPercent;
        int deleteThreshold = successorThreshold + this.deletePercent;
        // rest is just predecessor

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, this.size);
            final long threadSeed = this.seed + t;

            threads[t] = new Thread(() -> {
                Random rng = new Random(threadSeed);
                for (int i = start; i < end; i++) {
                    this.keys[i] = rng.nextLong() & (this.universe - 1);
                    int r = rng.nextInt(100);

                    if (r < insertThreshold) {
                        this.types[i] = INSERT;
                    } else if (r < queryThreshold) {
                        this.types[i] = QUERY;
                    } else if (r < successorThreshold) {
                        this.types[i] = SUCCESSOR;
                    } else if (r < deleteThreshold) {
                        this.types[i] = DELETE;
                    } else {
                        this.types[i] = PREDECESSOR;
                    }
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
