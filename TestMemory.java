public class TestMemory {

    public static void main(String[] args) throws Exception {

        int bits = Integer.parseInt(args[0]);
        long universe = 1L << bits;

        ConcurrentYFastTree y = new ConcurrentYFastTree(bits);

        for (long i = 0L; i < universe; i++) {
            y.insert(i);
        }

        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();

        double usedMB = usedBytes / (1024.0 * 1024.0);
        double usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0);

        double committedMB = rt.totalMemory() / (1024.0 * 1024.0);
        double maxMB = rt.maxMemory() / (1024.0 * 1024.0);

        System.out.printf("Memory = %.2f MB (%.4f GB)%n", usedMB, usedGB);
        System.out.printf("Committed = %.2f MB, Max = %.2f MB%n", committedMB, maxMB);
        System.out.println("xfast.size = " + y.xfast.size);
    }
}
