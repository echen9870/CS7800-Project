package yFast;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import xFast.ConcurrentXFastTrie;
import xFast.XFastChecker;
import xFast.XFastTrie;

public class TestYFastQueryConcurrencyCorrectness {

    static final int BITS = 20;
    static final int THREADS = 16;
    static final int KEYS_PER_THREAD = 5000;
    static final long UNIVERSE = 1L << BITS;
    static ConcurrentYFastTrieV2 yFastTrie = new ConcurrentYFastTrieV2(BITS, new ConcurrentXFastTrie(BITS, 16));
    static TreeSet<Long> bst = new TreeSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("Testing Concurrent Insertion");
        testConcurrentInsert();
        System.out.println("Testing Concurrent Deletion");
        testConcurrentDelete();
        System.out.println("All concurrent correctness tests passed.");
    }

    // All threads insert disjoint key sets concurrently.
    static void testConcurrentInsert() throws Exception {
        ArrayList<Long> keys = generateKeys(THREADS * KEYS_PER_THREAD);

        // Insertions into YFast
        runConcurrently(THREADS, keys.size(), (start, end) -> {
            for (int i = start; i < end; i++) {
                yFastTrie.insert(keys.get(i));
            }
        });

        // Insertions into BST
        bst.addAll(keys);

        // Ops Check The Entire Universe
        assertOps(yFastTrie, bst);

        // Check XFast
        XFastChecker.check(yFastTrie.xfast);

        System.out.println("concurrent insert: OK (" + bst.size() + " remaining keys, " + THREADS + " threads)");
    }

    // All threads delete disjoint subsets concurrently.
    static void testConcurrentDelete() throws Exception {
        ArrayList<Long> keys = generateKeys(THREADS * KEYS_PER_THREAD);

        runConcurrently(THREADS, keys.size(), (start, end) -> {
            for (int i = start; i < end; i++) {
                yFastTrie.delete(keys.get(i));
            }
        });

        // Deletions into BST
        bst.removeAll(keys);

        // Ops Check The Entire Universe
        assertOps(yFastTrie, bst);

        // Check XFast
        XFastChecker.check(yFastTrie.xfast);

        System.out.println("concurrent delete: OK (" + bst.size() + " remaining keys, " + THREADS + " threads)");
    }

    // --- Helpers ---
    @FunctionalInterface
    interface RangeTask {
        void run(int start, int end) throws Exception;
    }

    static void runConcurrently(int threadCount, int totalWork, RangeTask task) throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> err = new AtomicReference<>();

        int chunkSize = totalWork / threadCount;

        for (int t = 0; t < threadCount; t++) {
            final int start = t * chunkSize;
            final int end = (t == threadCount - 1) ? totalWork : start + chunkSize;

            new Thread(() -> {
                try {
                    startLatch.await();
                    task.run(start, end);
                } catch (Throwable e) {
                    err.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        if (err.get() != null) throw new RuntimeException(err.get());
    }

    static ArrayList<Long> generateKeys(int n) {
        ArrayList<Long> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            keys.add(ThreadLocalRandom.current().nextLong(UNIVERSE));
        }
        return keys;
    }

    static void assertOps(ConcurrentYFastTrieV2 tree, TreeSet<Long> expected) {
        for (long key = 0; key < UNIVERSE; key++) {
            if (!Objects.equals(tree.successor(key), expected.ceiling(key)))
            throw new AssertionError("Successor mismatch: key=" + key + " expected=" + expected.ceiling(key) + " got=" + tree.successor(key)) ;

            if (!Objects.equals(tree.predecessor(key), expected.floor(key)))
                throw new AssertionError("Predecessor mismatch: key=" + key + " expected=" + expected.floor(key) + " got=" + tree.predecessor(key));

            if (!Objects.equals(tree.query(key), expected.contains(key)))
                throw new AssertionError("Query mismatch: key=" + key + " expected=" + expected.contains(key) + " tree.query(key)=" + tree.query(key));
        }
    }
}
