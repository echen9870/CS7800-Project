import java.util.Random;

public class OpsGenerator {
    public static final byte INSERT   = 0;
    public static final byte QUERY    = 1;
    public static final byte SUCCESSOR = 2;

    public int universe;
    public int size;
    public long seed;

    public int insertPercent;
    public int queryPercent;
    public int successorPercent;

    public int[] keys;
    public byte[] types;

    public OpsGenerator(int universe, int size) {
        this(universe, size, 1234567L, 50, 25, 25);
    }

    public OpsGenerator(int universe, int size, long seed, int insertPercent, int queryPercent, int successorPercent) {
        this.universe = universe;
        this.size = size;
        this.seed = seed;

        this.insertPercent = insertPercent;
        this.queryPercent = queryPercent;
        this.successorPercent = successorPercent;

        this.keys = new int[size];
        this.types = new byte[size];
        this.generate();
    }

    public void generate() {
        int threadCount = Runtime.getRuntime().availableProcessors();
        int chunkSize = (this.size + threadCount - 1) / threadCount;
        int insertThreshold = this.insertPercent;
        int queryThreshold = this.insertPercent + this.queryPercent;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, this.size);
            final long threadSeed = this.seed + t;

            threads[t] = new Thread(() -> {
                Random rng = new Random(threadSeed);
                for (int i = start; i < end; i++) {
                    this.keys[i] = rng.nextInt(this.universe);
                    int r = rng.nextInt(100);
                    if (r < insertThreshold) this.types[i] = INSERT;
                    else if (r < queryThreshold) this.types[i] = QUERY;
                    else this.types[i] = SUCCESSOR;
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
