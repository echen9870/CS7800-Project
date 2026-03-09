package yFast;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;
import xFast.Node;
import xFast.XFastTrie;

public class ConcurrentYFastTrieV1 {

    public final int bits;
    public XFastTrie xfast;

    private final int maxBucketSize;

    public ConcurrentYFastTrieV1(int b, XFastTrie xfast) {
        this.bits = b;
        this.xfast = xfast;
        this.maxBucketSize = 32 * b;
    }

    // Which bucket x belongs in if it exists
    // xfast writes are rare (splits, rep changes) — fully optimistic read is fine
    private Node locateBucket(long x) {
        while (true) {
            long stamp = xfast.rw.tryOptimisticRead();
            Node node = xfast.predecessorNodeNoLock(x);
            if (xfast.rw.validate(stamp)) return node;
        }
    }

    // Fully optimistic query, we just keep retrying, no locks
    public boolean query(long x) {
        while (true) {
            Node node = locateBucket(x);

            // early exit
            if (node == null) return false;
            if (node.key == x) return true;

            long stamp = node.bucketRw.tryOptimisticRead();
            PrimitiveArray bucket = node.bucket;
            int pos = Arrays.binarySearch(bucket.data, 0, bucket.size, x);

            // Stale -> retry
            if (!node.bucketRw.validate(stamp)) continue;
            return pos >= 0;
        }
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        while (true) {
            Node node = locateBucket(x);

            if (node == null) {
                Node head = xfast.headLeaf;
                return head == null ? null : head.key;
            }

            if (node.key == x) return x;

            long stamp = node.bucketRw.tryOptimisticRead();
            PrimitiveArray bucket = node.bucket;
            Node freshNextNode = node.next;
            int idx = Arrays.binarySearch(bucket.data, 0, bucket.size, x);
            long inBucket = idx >= 0 ? bucket.data[idx] : ((-idx - 1) < bucket.size ? bucket.data[-idx - 1] : -1);

            if (!node.bucketRw.validate(stamp)) continue;

            if (inBucket != -1) return inBucket;

            return freshNextNode == null ? null : freshNextNode.key;
        }
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        while (true) {
            Node node = locateBucket(x);

            if (node == null)
                return null;

            if (node.key == x)
                return x;

            long stamp = node.bucketRw.tryOptimisticRead();
            PrimitiveArray bucket = node.bucket;
            int pos = Arrays.binarySearch(bucket.data, 0, bucket.size, x);
            int ip = pos >= 0 ? pos : -pos - 1;
            long inBucket = pos >= 0 ? bucket.data[pos] : (ip > 0 ? bucket.data[ip - 1] : -1);

            if (!node.bucketRw.validate(stamp)) continue;

            if (inBucket != -1) return inBucket;
        }
    }

    public void insert(long x) {
        while (true) {
            Node node = locateBucket(x);

            // x is smaller than every existing key — create a new head bucket
            if (node == null) {
                long lock = xfast.rw.writeLock();
                try {
                    Node headNode = xfast.headLeaf;
                    if (headNode != null && headNode.key <= x) continue;

                    PrimitiveArray bucket = new PrimitiveArray(new long[maxBucketSize], 1);
                    bucket.data[0] = x;
                    xfast.insertNoLockBetween(x, bucket, null, headNode);
                    return;
                } finally {
                    xfast.rw.unlockWrite(lock);
                }
            }

            long lock = node.bucketRw.writeLock();
            PrimitiveArray bucket = node.bucket;
            long xLock = (bucket.size + 1 == maxBucketSize) ? xfast.rw.writeLock() : 0;

            try {
                // Stale data
                if (xLock != 0 && bucket.size + 1 != maxBucketSize) continue;

                // Validate bucket is still alive and x belongs here
                if (bucket.size == 0 || bucket.data[0] != node.key) continue;
                Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key) continue;
                if (x < node.key) continue;

                // Duplicate
                if (bucket.sortedInsert(x) < 0) return; 
                // Need to split
                if (xLock != 0) splitListLocked(node);
                return;
            } finally {
                if (xLock != 0) xfast.rw.unlockWrite(xLock);
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    public boolean delete(long x) {
        while (true) {
            Node node = locateBucket(x);
            if (node == null) return false;

            long lock = node.bucketRw.writeLock();
            long xLock = (x == node.key) ? xfast.rw.writeLock() : 0;
            try {
                PrimitiveArray bucket = node.bucket;

                // Validate bucket is still alive and x belongs here
                if (bucket.size == 0 || bucket.data[0] != node.key) continue;
                Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key) continue;
                if (x < node.key) continue;

                // If our xFast write lock conditionals are stale
                if (xLock != 0 && x != bucket.data[0]) continue;

                if (!bucket.delete(x)) return false;

                if (xLock != 0) {
                    xfast.deleteNoLock(x);
                    if (bucket.size > 0) {
                        node.key = bucket.data[0];
                        xfast.reinsertNodeNoLock(node, node.prev, node.next);
                    }
                }

                return true;
            } finally {
                if (xLock != 0) {
                    xfast.rw.unlockWrite(xLock);
                }
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    private void splitListLocked(Node node) {
        PrimitiveArray newBucket = node.bucket.split(maxBucketSize);
        Node xfastNode = (Node) xfast.level[xfast.bits].get(node.key);
        xfast.insertNoLockBetween(newBucket.data[0], newBucket, xfastNode, node.next);
    }
}
