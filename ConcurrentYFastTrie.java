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

    // Which bucket x belongs in if it exists.
    private XFastTrie.Node locateBucket(long x) {
        return xfast.predecessorNode(x);
    }

    // Fully optimistic query, we just keep retrying, no locks
    public boolean query(long x) {
        while (true) {
            XFastTrie.Node node = locateBucket(x);

            if (node == null) return false;
            if (node.key == x) return true;

            long stamp = node.bucketRw.tryOptimisticRead();
            int pos = Arrays.binarySearch(node.nums, 0, node.numsSize, x);

            if (!node.bucketRw.validate(stamp)) continue;
            return pos >= 0;
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

            if (node.key == x) return x;

            long stamp = node.bucketRw.tryOptimisticRead();
            XFastTrie.Node freshNextNode = node.next;
            int idx = Arrays.binarySearch(node.nums, 0, node.numsSize, x);
            long inBucket = idx >= 0 ? node.nums[idx] : ((-idx - 1) < node.numsSize ? node.nums[-idx - 1] : Long.MIN_VALUE);

            if (!node.bucketRw.validate(stamp)) continue;

            if (inBucket != Long.MIN_VALUE) return inBucket;
            return freshNextNode == null ? null : freshNextNode.key;
        }
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        while (true) {
            XFastTrie.Node node = locateBucket(x);

            if (node == null) return null;
            if (node.key == x) return x;

            long stamp = node.bucketRw.tryOptimisticRead();
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
                // Re-check: a predecessor may have appeared since locateBucket
                if (xfast.predecessorNode(x) != null) continue;
                long[] nums = new long[maxBucketSize];
                nums[0] = x;
                xfast.insert(x, nums, 1);
                return;
            }

            long lock = node.bucketRw.writeLock();
            try {
                XFastTrie.Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key) continue;

                long[] nums = node.nums;
                int numsSize = node.numsSize;

                if (numsSize == 0 || nums[0] != node.key) continue;

                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos >= 0) return;
                pos = -pos - 1;

                System.arraycopy(nums, pos, nums, pos + 1, numsSize - pos);
                nums[pos] = x;
                node.numsSize = numsSize + 1;

                if (node.numsSize == maxBucketSize) splitBucket(node);
                return;
            } finally {
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    public boolean delete(long x) {
        while (true) {
            XFastTrie.Node node = locateBucket(x);

            if (node == null) return false;

            long lock = node.bucketRw.writeLock();
            try {
                XFastTrie.Node nextNode = node.next;
                if (nextNode != null && x >= nextNode.key) continue;

                long[] nums = node.nums;
                int numsSize = node.numsSize;

                if (numsSize == 0 || nums[0] != node.key) continue;

                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos < 0) return false;

                System.arraycopy(nums, pos + 1, nums, pos, numsSize - pos - 1);
                node.numsSize = numsSize - 1;

                if (x == node.key) {
                    xfast.delete(x);
                    if (node.numsSize > 0) xfast.insert(nums[0], nums, node.numsSize);
                }
                return true;
            } finally {
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    private void splitBucket(XFastTrie.Node node) {
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        int half = numsSize / 2;
        int newNumsSize = numsSize - half;
        long[] newNums = new long[maxBucketSize];
        System.arraycopy(nums, half, newNums, 0, newNumsSize);

        node.numsSize = half;
        xfast.insert(newNums[0], newNums, newNumsSize);
    }
}
