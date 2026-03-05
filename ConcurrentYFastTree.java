import java.util.Arrays;

public class ConcurrentYFastTree {

    public int bits = 0;
    public XFastTree xfast;

    private final int maxBucketSize;

    public ConcurrentYFastTree(int b) {
        this.bits = b;
        this.xfast = new XFastTree(b);
        this.maxBucketSize = 128 * b;
    }

    // Which bucket x belongs in if it exists
    // xfast writes are rare (splits, rep changes) — fully optimistic read is fine
    private XFastTree.Node locateBucket(long x) {
        while (true) {
            long stamp = xfast.rw.tryOptimisticRead();
            XFastTree.Node node = xfast.predecessorNodeNoLock(x);
            if (xfast.rw.validate(stamp)) return node;
        }
    }

    // Fully optimistic query, we just keep retrying, no locks
    public boolean query(long x) {
        while (true) {
            XFastTree.Node node = locateBucket(x);

            // early exit
            if (node == null) return false;
            if (node.key == x) return true;

            long stamp = node.bucketRw.tryOptimisticRead();
            int pos = Arrays.binarySearch(node.nums, 0, node.numsSize, x);

            // Stale -> retry
            if (!node.bucketRw.validate(stamp)) continue;
            return pos >= 0 ? true : false;
        }
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        while (true) {
            XFastTree.Node node = locateBucket(x);

            if (node == null) {
                XFastTree.Node head = xfast.headLeaf;
                return head == null ? null : head.key;
            }

            // x is the bucket rep — confirmed present at nums[0]
            if (node.key == x) return x;

            long stamp = node.bucketRw.tryOptimisticRead();
            XFastTree.Node freshNextNode = node.next;
            // binary search + value capture before validate so array reads are in the optimistic window
            int idx = Arrays.binarySearch(node.nums, 0, node.numsSize, x);
            long inBucket = idx >= 0 ? node.nums[idx] : ((-idx - 1) < node.numsSize ? node.nums[-idx - 1] : Long.MIN_VALUE);

            if (!node.bucketRw.validate(stamp)) continue;

            if (inBucket != Long.MIN_VALUE) return inBucket;

            // x is past all elements in this bucket — successor is the next bucket's rep
            return freshNextNode == null ? null : freshNextNode.key;
        }
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        while (true) {
            XFastTree.Node node = locateBucket(x);

            if (node == null)
                return null;

            // x is the bucket rep — confirmed present at nums[0]
            if (node.key == x)
                return x;

            long stamp = node.bucketRw.tryOptimisticRead();
            // binary search + value capture before validate so array reads are in the optimistic window
            int pos = Arrays.binarySearch(node.nums, 0, node.numsSize, x);
            int ip = pos >= 0 ? pos : -pos - 1;
            long inBucket = pos >= 0 ? node.nums[pos] : (ip > 0 ? node.nums[ip - 1] : Long.MIN_VALUE);

            if (!node.bucketRw.validate(stamp)) continue;

            if (inBucket != Long.MIN_VALUE) return inBucket;
        }
    }

    public void insert(long x) {
        while (true) {
            XFastTree.Node node = locateBucket(x);

            // x is smaller than every existing key — create a new head bucket
            if (node == null) {
                long lock = xfast.rw.writeLock();
                try {
                    if (xfast.predecessorNodeNoLock(x) != null)
                        continue;
                    long[] nums = new long[maxBucketSize];
                    nums[0] = x;
                    xfast.insertNoLock(x, nums, 1);
                    return;
                } finally {
                    xfast.rw.unlockWrite(lock);
                }
            }

            long lock = node.bucketRw.writeLock();
            try {
                XFastTree.Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key)
                    continue;

                long[] nums = node.nums;
                int numsSize = node.numsSize;

                // rep changed (or bucket emptied) while we waited for the lock — stale node
                if (numsSize == 0 || nums[0] != node.key) continue;

                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos >= 0) return;
                pos = -pos - 1;

                // grab xfast before touching the array if we know a split is coming
                long xLock = (numsSize + 1 == maxBucketSize) ? xfast.rw.writeLock() : 0;
                try {
                    System.arraycopy(nums, pos, nums, pos + 1, numsSize - pos);
                    nums[pos] = x;
                    node.numsSize = numsSize + 1;
                    if (xLock != 0) splitListLocked(node);
                } finally {
                    if (xLock != 0) xfast.rw.unlockWrite(xLock);
                }
                return;
            } finally {
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    public boolean delete(long x) {
        while (true) {
            XFastTree.Node node = locateBucket(x);

            if (node == null) return false;

            long lock = node.bucketRw.writeLock();
            try {
                XFastTree.Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key) continue;

                long[] nums = node.nums;
                int numsSize = node.numsSize;

                // rep changed (or bucket emptied) while we waited for the lock — stale node
                if (numsSize == 0 || nums[0] != node.key) continue;

                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos < 0) return false;

                // grab xfast before touching the array if x is the rep
                long xLock = (x == node.key) ? xfast.rw.writeLock() : 0;
                try {
                    System.arraycopy(nums, pos + 1, nums, pos, numsSize - pos - 1);
                    node.numsSize = numsSize - 1;
                    if (xLock != 0) {
                        xfast.deleteNoLock(x);
                        if (node.numsSize > 0) xfast.insertNoLock(nums[0], nums, node.numsSize);
                    }
                } finally {
                    if (xLock != 0) xfast.rw.unlockWrite(xLock);
                }
                return true;
            } finally {
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    private void splitListLocked(XFastTree.Node node) {
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        int half = numsSize / 2;
        int newNumsSize = numsSize - half;
        long[] newNums = new long[maxBucketSize];
        System.arraycopy(nums, half, newNums, 0, newNumsSize);

        node.numsSize = half;

        // caller already holds xfast writeLock
        xfast.insertNoLock(newNums[0], newNums, newNumsSize);
    }
}