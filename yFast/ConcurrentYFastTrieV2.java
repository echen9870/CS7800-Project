package yFast;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;
import xFast.ConcurrentXFastTrie;
import xFast.Node;

// Lock ordering (always acquired in this order to prevent deadlocks):
//   1. bucketRw           — per-bucket, protects bucket data
//   2. partition locks    — per-LFL-node, acquired in ascending key order (pred → x → succ)
//   3. levelLock          — global, readLock keeps locks[] stable, writeLock for LFL transitions
public class ConcurrentYFastTrieV2 {

    public final int bits;
    public ConcurrentXFastTrie xfast;
    private final int maxBucketSize;

    public ConcurrentYFastTrieV2(int b, ConcurrentXFastTrie xfast) {
        this.bits = b;
        this.xfast = xfast;
        this.maxBucketSize = 32 * b;
    }

    private Node locateBucket(long x) {
        while (true) {
            StampedLock lock = xfast.getLock(x);
            long stamp = lock.tryOptimisticRead();
            Node node = xfast.predecessorNodeNoLock(x);
            if (lock.validate(stamp)) return node;
        }
    }

    // After insert/split: advance LFL if the next level is now full
    private void afterInsert() {
        int lfl = xfast.lowestFullLevel;
        if (lfl >= xfast.maxLFL || xfast.level[lfl + 1].size() != (1L << (lfl + 1))) return;
        long stamp = xfast.levelLock.writeLock();
        lfl = xfast.lowestFullLevel;
        if (lfl < xfast.maxLFL && xfast.level[lfl + 1].size() == (1L << (lfl + 1))) xfast.advanceLFL();
        xfast.levelLock.unlockWrite(stamp);
    }

    // After rep-delete: retreat LFL if the current level is no longer full
    private void afterDelete() {
        int lfl = xfast.lowestFullLevel;
        if (lfl <= 0 || xfast.level[lfl].size() >= (1L << lfl)) return;
        long stamp = xfast.levelLock.writeLock();
        lfl = xfast.lowestFullLevel;
        if (lfl > 0 && xfast.level[lfl].size() < (1L << lfl)) xfast.retreatLFL();
        xfast.levelLock.unlockWrite(stamp);
    }

    public boolean query(long x) {
        while (true) {
            Node node = locateBucket(x);
            if (node == null) return false;
            if (node.key == x) return true;

            long stamp = node.bucketRw.tryOptimisticRead();
            PrimitiveArray bucket = node.bucket;
            int pos = Arrays.binarySearch(bucket.data, 0, bucket.size, x);
            if (!node.bucketRw.validate(stamp)) continue;
            return pos >= 0;
        }
    }

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
            Node nextNode = node.next;
            long succ = bucket.successor(x);
            if (!node.bucketRw.validate(stamp)) continue;

            if (succ != -1) return succ;
            return nextNode == null ? null : nextNode.key;
        }
    }

    public Long predecessor(long x) {
        while (true) {
            Node node = locateBucket(x);
            if (node == null) return null;
            if (node.key == x) return x;

            long stamp = node.bucketRw.tryOptimisticRead();
            PrimitiveArray bucket = node.bucket;
            long pred = bucket.predecessor(x);
            if (!node.bucketRw.validate(stamp)) continue;
            return pred;
        }
    }

    public void insert(long x) {
        boolean didSplit = false;
        while (true) {
            Node node = locateBucket(x);
            StampedLock xLock = null;    
            long xStamp = -1;
            StampedLock succLock = null;  
            long succStamp = -1;
            long levelStamp = -1;
            long bucketStamp = -1;

            // Head insert: x is smaller than every existing key
            // Since x < headNode.key, getLock(x) covers headNode's partition too
            if (node == null) {
                Node headNode = xfast.headLeaf;
                try {
                    // Get bucket stamp
                    bucketStamp = (headNode != null) ? headNode.bucketRw.writeLock() : -1;

                    // Stale
                    if (headNode != null && headNode.key <= x) continue;

                    // Get xLock/headLock
                    xLock = xfast.getLock(x);
                    xStamp = xLock.writeLock();

                    // Get succLock is successor exists
                    if (headNode != null) {
                        Node succNode = headNode.next;
                        if (succNode != null) {
                            succLock = xfast.getLock(succNode.key);
                            if (succLock != xLock) succStamp = succLock.writeLock();
                        }
                    }

                    // Get level lock
                    levelStamp = xfast.levelLock.readLock();

                    // Stale
                    if (xfast.headLeaf != headNode) continue;
                    // If first insert
                    if (headNode == null) {
                        PrimitiveArray bucket = new PrimitiveArray(new long[maxBucketSize], 1);
                        bucket.data[0] = x;
                        xfast.insertNoLockBetween(x, bucket, null, null);
                    } else {
                        PrimitiveArray bucket = headNode.bucket;
                        bucket.sortedInsert(x);
                        long oldKey = headNode.key;
                        xfast.deleteNoLock(oldKey);
                        headNode.key = x;
                        xfast.reinsertNodeNoLock(headNode, null, headNode.next);
                        if (bucket.size == maxBucketSize) {
                            PrimitiveArray newBucket = bucket.split(maxBucketSize);
                            xfast.insertNoLockBetween(newBucket.data[0], newBucket, headNode, headNode.next);
                        }
                    }
                    return;
                } finally {
                    if (levelStamp != -1) xfast.levelLock.unlockRead(levelStamp);
                    if (succStamp != -1) succLock.unlockWrite(succStamp);
                    if (xStamp != -1) xLock.unlockWrite(xStamp);
                    if (bucketStamp != -1) headNode.bucketRw.unlockWrite(bucketStamp);
                }
            }

            // Normal insert into existing bucket
            bucketStamp = node.bucketRw.writeLock();
            PrimitiveArray bucket = node.bucket;
            boolean willSplit = (bucket.size + 1 == maxBucketSize);
            if (willSplit) {
                xLock = xfast.getLock(x);
                xStamp = xLock.writeLock();
                Node succNode = node.next;
                if (succNode != null) {
                    succLock = xfast.getLock(succNode.key);
                    if (succLock != xLock) succStamp = succLock.writeLock();
                }
                levelStamp = xfast.levelLock.readLock();
            }

            try {
                if (bucket.size == 0 || bucket.data[0] != node.key) continue;
                Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key) continue;
                if (x < node.key) continue;

                // duplicate
                if (bucket.sortedInsert(x) < 0) break; 

                if (willSplit) {
                    PrimitiveArray newBucket = bucket.split(maxBucketSize);
                    xfast.insertNoLockBetween(newBucket.data[0], newBucket, node, node.next);
                    didSplit = true;
                }
                break;
            } finally {
                if (levelStamp != -1) xfast.levelLock.unlockRead(levelStamp);
                if (succStamp != -1) succLock.unlockWrite(succStamp);
                if (xStamp != -1) xLock.unlockWrite(xStamp);
                node.bucketRw.unlockWrite(bucketStamp);
            }
        }
        if (didSplit) afterInsert();
    }

    public boolean delete(long x) {
        while (true) {
            Node node = locateBucket(x);
            if (node == null) return false;

            long bucketStamp = node.bucketRw.writeLock();
            PrimitiveArray bucket = node.bucket;
            boolean isRepDelete = (x == node.key);

            // Partition locks in ascending key order (pred → x → succ), then levelLock
            StampedLock predLock = null;  long predStamp = -1;
            StampedLock xLock = null;     long xStamp = -1;
            StampedLock succLock = null;  long succStamp = -1;
            long levelStamp = -1;

            if (isRepDelete) {
                Node predNode = node.prev;
                if (predNode != null) {
                    predLock = xfast.getLock(predNode.key);
                    predStamp = predLock.writeLock();
                }
                xLock = xfast.getLock(x);
                if (xLock != predLock) xStamp = xLock.writeLock();
                Node succNode = node.next;
                if (succNode != null) {
                    succLock = xfast.getLock(succNode.key);
                    if (succLock != xLock && succLock != predLock) succStamp = succLock.writeLock();
                }
                levelStamp = xfast.levelLock.readLock();
            }

            try {
                if (bucket.size == 0 || bucket.data[0] != node.key) continue;
                Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key) continue;
                if (x < node.key) continue;

                if (!bucket.delete(x)) return false;
                if (!isRepDelete) return true;

                xfast.deleteNoLock(x);
                if (bucket.size > 0) {
                    node.key = bucket.data[0];
                    xfast.reinsertNodeNoLock(node, node.prev, node.next);
                }
                break; // rep-delete done
            } finally {
                if (levelStamp != -1) xfast.levelLock.unlockRead(levelStamp);
                if (succStamp != -1) succLock.unlockWrite(succStamp);
                if (xStamp != -1) xLock.unlockWrite(xStamp);
                if (predStamp != -1) predLock.unlockWrite(predStamp);
                node.bucketRw.unlockWrite(bucketStamp);
            }
        }
        afterDelete();
        return true;
    }
}
