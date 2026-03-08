import java.util.Arrays;

public class ConcurrentYFastTrie {

    public int bits = 0;
    public XFastTrieInterface xfast;

    private final int maxBucketSize;

    public ConcurrentYFastTrie(int b, XFastTrieInterface xfast) {
        this.bits = b;
        this.xfast = xfast;
        this.maxBucketSize = 128 * b;
    }

    // Which bucket x belongs in if it exists
    // xfast writes are rare (splits, rep changes) — fully optimistic read is fine
    private XFastTrie.Node locateBucket(long x) {
        while (true) {
            long stamp = xfast.getLock(x).tryOptimisticRead();
            XFastTrie.Node node = xfast.predecessorNodeNoLock(x);
            if (xfast.getLock(x).validate(stamp)) return node;
        }
    }

    // Fully optimistic query, we just keep retrying, no locks
    public boolean query(long x) {
        while (true) {
            XFastTrie.Node node = locateBucket(x);

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
            XFastTrie.Node node = locateBucket(x);

            if (node == null) {
                XFastTrie.Node head = xfast.getHeadLeaf();
                return head == null ? null : head.key;
            }

            // x is the bucket rep — confirmed present at nums[0]
            if (node.key == x) return x;

            long stamp = node.bucketRw.tryOptimisticRead();
            XFastTrie.Node freshNextNode = node.next;
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
            XFastTrie.Node node = locateBucket(x);

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
            XFastTrie.Node node = locateBucket(x);

            // x is smaller than every existing key — create a new head bucket
            if (node == null) {
                long lock = xfast.getLock(x).writeLock();
                try {
                    // Stale
                    if (xfast.predecessorNodeNoLock(x) != null) continue;

                    long[] nums = new long[maxBucketSize];
                    nums[0] = x;
                    xfast.insertNoLock(x, nums, 1);
                    return;
                } finally {
                    xfast.getLock(x).unlockWrite(lock);
                }
            }

            long lock = node.bucketRw.writeLock();
            long xLock = (node.numsSize + 1 == maxBucketSize) ? xfast.getLock(x).writeLock() : 0;

            try {
                // Stale data
                // If our xFast write lock coditionals are stale, give up lock and retry
                if (xLock != 0 && node.numsSize + 1 != maxBucketSize) continue;

                // If our number should be inserted into the next node
                if (xfast.predecessorNodeNoLock(x) != node) continue;

                long[] nums = node.nums;
                int numsSize = node.numsSize;

                // rep changed (or bucket emptied) while we waited for the lock — stale node
                if (node.numsSize == 0 || nums[0] != node.key) continue;

                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos >= 0) return;
                pos = -pos - 1;

                // grab xfast before touching the array if we know a split is coming
                System.arraycopy(nums, pos, nums, pos + 1, numsSize - pos);
                nums[pos] = x;
                node.numsSize = numsSize + 1;
                if (xLock != 0) splitListLocked(node);
                return;
            } finally {
                if (xLock != 0) xfast.getLock(x).unlockWrite(xLock);
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    public boolean delete(long x) {
        while (true) {
            XFastTrie.Node node = locateBucket(x);
            if (node == null) return false;

            // Grab all the locks we need
            long lock = node.bucketRw.writeLock();
            long xLock = (x == node.key) ? xfast.getLock(x).writeLock() : 0;
            try {
                // Retry if stale data
                // If our xFast write lock coditionals are stale
                if (xLock != 0 && x != node.nums[0]) {
                    continue;
                }

                // If our number should be deleted from the next node
                Long pred = xfast.predecessorNoLock(x);
                if (pred == null || pred != node.key) {
                    continue;
                }

                // If our current node has changed
                if (node.numsSize == 0 || node.nums[0] != node.key) {
                    continue;
                }

                int pos = Arrays.binarySearch(node.nums, 0, node.numsSize, x);
                if (pos < 0) return false;

                System.arraycopy(node.nums, pos + 1, node.nums, pos, node.numsSize - pos - 1);
                node.numsSize = node.numsSize - 1;

                if (xLock != 0) {
                    xfast.deleteNoLock(x);
                    if (node.numsSize > 0) {
                        node.key = node.nums[0];
                        xfast.insertNoLock(node.nums[0], node.nums, node.numsSize);
                    }
                }

                return true;
            } finally {
                if (xLock != 0) {
                    xfast.getLock(x).unlockWrite(xLock);
                }
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    private void splitListLocked(XFastTrie.Node node) {
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        int half = numsSize / 2;
        int newNumsSize = numsSize - half;
        long[] newNums = new long[maxBucketSize];
        System.arraycopy(nums, half, newNums, 0, newNumsSize);

        node.numsSize = half;

        xfast.insertNoLock(newNums[0], newNums, newNumsSize);
    }
}