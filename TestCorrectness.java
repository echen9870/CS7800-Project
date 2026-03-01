import java.util.Random;
import java.util.TreeSet;
import java.util.Objects;

// This file is used to test the correctness of single threaded implementation against a bst
// These single threaded implementation will then be used to test against their multithreaded counterparts

public class TestCorrectness {

    public static void runCorrectness(int bits, int insertCount, int checkCount) {
        XFastTree xFast = new XFastTree(bits);
        YFastTree yFast = new YFastTree(bits);
        ConcurrentYFastTree concurrentYFast = new ConcurrentYFastTree(bits);
        TreeSet<Long> bst = new TreeSet<>();
        long universe = 1L << bits;


        // Insert random values into both trees
        Random rng = new Random();
        for (int i = 0; i < insertCount; i++) {
            long value = rng.nextLong() & (universe - 1);
            xFast.insert(value);
            yFast.insert(value);
            concurrentYFast.insert(value);
            bst.add(value);
        }

        // Query + successor checks
        for (int i = 0; i < checkCount; i++) {
            long key = rng.nextLong() & (universe - 1);

            // Query mismatch
            if (bst.contains(key) != xFast.query(key)) {
                throw new RuntimeException("X Fast Query mismatch for key=" + key +" expected=" + bst.contains(key) + " got=" + xFast.query(key));
            }
            if (bst.contains(key) != yFast.query(key)) {
                throw new RuntimeException("y Fast Query mismatch for key=" + key +" expected=" + bst.contains(key) + " got=" + yFast.query(key));
            }
            if (bst.contains(key) != concurrentYFast.query(key)) {
                throw new RuntimeException("concurrentY Fast Query mismatch for key=" + key +" expected=" + bst.contains(key) + " got=" + concurrentYFast.query(key));
            }
            // // Predecessor mismatch
            // if (!Objects.equals(bst.floor(key), xFast.predecessor(key))) {
            //     throw new RuntimeException("X Fast Predecessor mismatch for key=" + key + " expected=" + bst.floor(key) +" got=" + xFast.predecessor(key));
            // }
            // if (!Objects.equals(bst.floor(key), yFast.bucketRep(key))) {
            //     throw new RuntimeException("y Fast Predecessor mismatch for key=" + key + " expected=" + bst.floor(key) +" got=" + yFast.bucketRep(key));
            // }
            // if (!Objects.equals(bst.floor(key), concurrentYFast.bucketRep(key))) {
            //     throw new RuntimeException("concurrentY Fast Predecessor mismatch for key=" + key + " expected=" + bst.floor(key) + " got=" + concurrentYFast.bucketRep(key));
            // }

            // Successor mismatch
            if (!Objects.equals(bst.ceiling(key), xFast.successor(key))) {
                throw new RuntimeException("X Fast Successor mismatch for key=" + key +" expected=" + bst.ceiling(key) + " got=" + xFast.successor(key));
            }
            if (!Objects.equals(bst.ceiling(key), yFast.successor(key))) {
                throw new RuntimeException("Y Fast Successor mismatch for key=" + key +" expected=" + bst.ceiling(key) + " got=" + yFast.successor(key));
            }
            if (!Objects.equals(bst.ceiling(key), concurrentYFast.successor(key))) {
                throw new RuntimeException("concurrentY Fast Successor mismatch for key=" + key +" expected=" + bst.ceiling(key) + " got=" + concurrentYFast.successor(key));
            }
        }
    }

    public static void main(String[] args) {

        int insertCount = 1 << 12;
        int checkCount = 1 << 16;

        try {
            runCorrectness(18, insertCount, checkCount);
            System.out.println("Correctness OK: bits=" + 18 +
                    " inserted=" + insertCount +
                    " checks=" + checkCount);
        } catch (RuntimeException e) {
            System.out.println("Correctness FAILED: " + e.getMessage());
            throw e;
        }
    }
}
