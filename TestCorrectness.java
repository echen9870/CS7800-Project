import java.util.Objects;
import java.util.Random;
import java.util.TreeSet;

// This file is used to test the correctness of single threaded implementation against a bst
// These single threaded implementation will then be used to test against their multithreaded counterparts

public class TestCorrectness {

    public static void runCorrectness(int bits, int insertCount, int checkCount) {
        XFastTree xFast = new XFastTree(bits);
        YFastTree yFast = new YFastTree(bits);
        ConcurrentYFastTree concurrentYFast = new ConcurrentYFastTree(bits);
        TreeSet<Long> bst = new TreeSet<>();
        long universe = 1L << bits;

        // Insert random values into all trees
        Random rng = new Random();
        for (int i = 0; i < insertCount; i++) {
            long value = rng.nextLong() & (universe - 1);
            xFast.insert(value);
            yFast.insert(value);
            concurrentYFast.insert(value);
            bst.add(value);
        }

        // Delete a portion of elements from all trees and bst
        int deleteCount = insertCount / 4;
        for (int i = 0; i < deleteCount; i++) {
            long value = rng.nextLong() & (universe - 1);
            xFast.delete(value);
            yFast.delete(value);
            concurrentYFast.delete(value);
            bst.remove(value);
        }

        // Query + successor + predecessor checks
        for (int i = 0; i < checkCount; i++) {
            long key = rng.nextLong() & (universe - 1);

            // --- Query ---
            if (bst.contains(key) != xFast.query(key)) {
                throw new RuntimeException("X Fast Query mismatch for key=" + key + " expected=" + bst.contains(key)
                        + " got=" + xFast.query(key));
            }
            if (bst.contains(key) != yFast.query(key)) {
                throw new RuntimeException("Y Fast Query mismatch for key=" + key + " expected=" + bst.contains(key)
                        + " got=" + yFast.query(key));
            }
            if (bst.contains(key) != concurrentYFast.query(key)) {
                throw new RuntimeException("ConcurrentY Fast Query mismatch for key=" + key + " expected="
                        + bst.contains(key) + " got=" + concurrentYFast.query(key));
            }

            // --- Successor ---
            if (!Objects.equals(bst.ceiling(key), xFast.successor(key))) {
                throw new RuntimeException("X Fast Successor mismatch for key=" + key + " expected=" + bst.ceiling(key)
                        + " got=" + xFast.successor(key));
            }
            if (!Objects.equals(bst.ceiling(key), yFast.successor(key))) {
                throw new RuntimeException("Y Fast Successor mismatch for key=" + key + " expected=" + bst.ceiling(key)
                        + " got=" + yFast.successor(key));
            }
            if (!Objects.equals(bst.ceiling(key), concurrentYFast.successor(key))) {
                throw new RuntimeException("ConcurrentY Fast Successor mismatch for key=" + key + " expected="
                        + bst.ceiling(key) + " got=" + concurrentYFast.successor(key));
            }

            // --- Predecessor ---
            if (!Objects.equals(bst.floor(key), yFast.predecessor(key))) {
                throw new RuntimeException("Y Fast Predecessor mismatch for key=" + key + " expected=" + bst.floor(key)
                        + " got=" + yFast.predecessor(key));
            }
            if (!Objects.equals(bst.floor(key), concurrentYFast.predecessor(key))) {
                throw new RuntimeException("ConcurrentY Fast Predecessor mismatch for key=" + key + " expected="
                        + bst.floor(key) + " got=" + concurrentYFast.predecessor(key));
            }
        }

        // Interleaved insert/delete/check phase
        for (int i = 0; i < checkCount / 4; i++) {
            long key = rng.nextLong() & (universe - 1);
            int op = rng.nextInt(5); // 0=insert, 1=delete, 2=query, 3=successor, 4=predecessor

            if (op == 0) {
                yFast.insert(key);
                concurrentYFast.insert(key);
                bst.add(key);
            } else if (op == 1) {
                yFast.delete(key);
                concurrentYFast.delete(key);
                bst.remove(key);
            } else if (op == 2) {
                if (bst.contains(key) != yFast.query(key)) {
                    throw new RuntimeException("Y Fast Query mismatch (mixed phase) for key=" + key + " expected="
                            + bst.contains(key) + " got=" + yFast.query(key));
                }
                if (bst.contains(key) != concurrentYFast.query(key)) {
                    throw new RuntimeException("ConcurrentY Fast Query mismatch (mixed phase) for key=" + key
                            + " expected=" + bst.contains(key) + " got=" + concurrentYFast.query(key));
                }
            } else if (op == 3) {
                if (!Objects.equals(bst.ceiling(key), yFast.successor(key))) {
                    throw new RuntimeException("Y Fast Successor mismatch (mixed phase) for key=" + key + " expected="
                            + bst.ceiling(key) + " got=" + yFast.successor(key));
                }
                if (!Objects.equals(bst.ceiling(key), concurrentYFast.successor(key))) {
                    throw new RuntimeException("ConcurrentY Fast Successor mismatch (mixed phase) for key=" + key
                            + " expected=" + bst.ceiling(key) + " got=" + concurrentYFast.successor(key));
                }
            } else {
                if (!Objects.equals(bst.floor(key), yFast.predecessor(key))) {
                    throw new RuntimeException("Y Fast Predecessor mismatch (mixed phase) for key=" + key + " expected="
                            + bst.floor(key) + " got=" + yFast.predecessor(key));
                }
                if (!Objects.equals(bst.floor(key), concurrentYFast.predecessor(key))) {
                    throw new RuntimeException("ConcurrentY Fast Predecessor mismatch (mixed phase) for key=" + key
                            + " expected=" + bst.floor(key) + " got=" + concurrentYFast.predecessor(key));
                }
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
