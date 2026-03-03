public class Main {

    public static void main(String[] args) {

        int bits = Integer.parseInt(args[0]);
        long universe = 1L << bits;

        TestingFramework framework = new TestingFramework(universe);
        framework.generateOps(
                (int) Math.min(1L << (bits + 2), Integer.MAX_VALUE - 8),
                (int) Math.min(1L << (bits - 2), Integer.MAX_VALUE - 8));

        // BST
        // TreeSet<Long> bst = new TreeSet<>();
        // {
        // RunResult r = framework.runRandom(
        // "BST",
        // 1,
        // (x) -> bst.add(x),
        // (x) -> bst.contains(x),
        // (x) -> bst.ceiling(x)
        // );
        // results.add(r);
        // }
        // // XFast 1 thread
        // XFastTree xFast1 = new XFastTree(bits);
        // {
        // RunResult r = framework.runRandom(
        // "XFastTree",
        // 1,
        // (x) -> xFast1.insert(x),
        // (x) -> xFast1.query(x),
        // (x) -> xFast1.successor(x)
        // );
        // results.add(r);
        // }
        // // XFast 8 threads
        // XFastTree xFast8 = new XFastTree(bits);
        // {
        // RunResult r = framework.runRandom(
        // "XFastTree",
        // 8,
        // (x) -> xFast8.insert(x),
        // (x) -> xFast8.query(x),
        // (x) -> xFast8.successor(x)
        // );
        // results.add(r);
        // }
        // YFast 1 thread
        // ConcurrentYFastTree yFast = new ConcurrentYFastTree(bits);
        // {
        // RunResult r = framework.runRandom(
        // "ConcurrentYFastTree",
        // 1,
        // (x) -> yFast.insert(x),
        // (x) -> yFast.query(x),
        // (x) -> yFast.successor(x)
        // );
        // System.out.println(r);
        // }
        // Concurrent Y-Fast 16 threads
        ConcurrentYFastTree concurrentYFast = new ConcurrentYFastTree(bits);
        {
            RunResult r = framework.runRandom(
                    "ConcurrentYFastTree",
                    16,
                    (x) -> concurrentYFast.insert(x),
                    (x) -> concurrentYFast.query(x),
                    (x) -> concurrentYFast.successor(x));
            System.out.println(r);
        }
    }
}
