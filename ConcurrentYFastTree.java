import java.util.*;

public class ConcurrentYFastTree {
    public int bits = 0;

    public XFastTree xfast;

    public ConcurrentYFastTree(int b) {
        this.bits = b;
        this.xfast = new XFastTree(b);
    }

    public boolean query(int x) {
        // get rep
        Integer rep = xfast.predecessor(x);
        XFastTree.Node node = (rep == null) ? xfast.headLeaf : xfast.queryNode(rep);
        if (node == null) return false;

        node.bucketReadLock.lock();
        try {
            int[] nums = node.nums;
            int numsSize = node.numsSize;
            if (nums == null || numsSize == 0) return false;

            // binary search inside bucket
            int pos = Arrays.binarySearch(nums, 0, numsSize, x);

            return (pos >= 0);
        } finally {
            node.bucketReadLock.unlock();
        }
    }

    // smallest key >= x, or null if none
    public Integer successor(int x) {
            // get rep
            Integer rep = xfast.predecessor(x);
            XFastTree.Node node = (rep == null) ? xfast.headLeaf : xfast.queryNode(rep);
            if (node == null) return null;

            node.bucketReadLock.lock();
            try {
                // get node
                int[] nums = node.nums;
                int numsSize = node.numsSize;
                if (nums == null || numsSize == 0) return null;

                int last = nums[numsSize - 1];

                if (x <= last) {
                    int idx = Arrays.binarySearch(nums, 0, numsSize, x);
                    if (idx >= 0) return nums[idx];
                    idx = -idx - 1;
                    return (idx < numsSize) ? nums[idx] : null;
                }
            } finally {
                node.bucketReadLock.unlock();
            }

            // If our last element is less than x, we have to go to next node
            XFastTree.Node nextNode = node.next;
            if (nextNode == null) return null;

            nextNode.bucketReadLock.lock();
            try {
                int[] nums = nextNode.nums;
                int numsSize = nextNode.numsSize;
                if (nums == null || numsSize == 0) return null;

                if (x <= nums[0]) return nums[0];

                int idx = Arrays.binarySearch(nums, 0, numsSize, x);
                if (idx >= 0) return nums[idx];
                idx = -idx - 1;
                return (idx < numsSize) ? nums[idx] : null;
            } finally {
                nextNode.bucketReadLock.unlock();
            }
    }

    public void insert(int x) {
        // max bucket size
        int maxSize = 16 * bits;
        if (maxSize < 2) maxSize = 2;

        while (true) {
            // first insert
            if (xfast.size == 0) {
                int[] nums = new int[2];
                nums[0] = x;
                xfast.insert(x, nums, 1);
                return;
            }

            // find bucket rep
            Integer rep = xfast.predecessor(x);

            // x is smaller than smallest rep
            if (rep == null) {
                int[] nums = new int[2];
                nums[0] = x;
                xfast.insert(x, nums, 1);
                return;
            }

            XFastTree.Node node = xfast.queryNode(rep);
            if (node == null) continue;

            node.bucketWriteLock.lock();
            try {
                int repKey = node.key;
                Integer nextRep = xfast.successor(repKey + 1);
                if (nextRep != null && x >= nextRep) {
                    continue;
                }

                int[] nums = node.nums;
                int numsSize = node.numsSize;
                if (nums == null) return;

                // insert sorted
                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos >= 0) return;
                pos = -pos - 1;

                if (numsSize >= nums.length) {
                    int newCapacity = nums.length == 0 ? 2 : (nums.length * 2);
                    int[] newNums = new int[newCapacity];
                    System.arraycopy(nums, 0, newNums, 0, numsSize);
                    nums = newNums;
                    node.nums = nums;
                }

                if (pos < numsSize) {
                    System.arraycopy(nums, pos, nums, pos + 1, numsSize - pos);
                }
                nums[pos] = x;
                node.numsSize = numsSize + 1;

                // split if too big
                if (node.numsSize > maxSize) {
                    splitListLocked(node);
                }
                return;
            } finally {
                node.bucketWriteLock.unlock();
            }
        }
    }

    // gets corresponding rep for a number (the predecessor of x)
    public Integer bucketRep(int x) {
        return xfast.predecessor(x);
    }

    private void splitListLocked(XFastTree.Node node) {
        int[] nums = node.nums;
        int numsSize = node.numsSize;
        if (nums == null) return;

        // split in half
        int half = numsSize / 2;
        if (half <= 0 || half >= numsSize) return;

        int newNumsSize = numsSize - half;
        int[] newNums = new int[Math.max(2, newNumsSize)];
        System.arraycopy(nums, half, newNums, 0, newNumsSize);

        node.numsSize = half;

        // register new bucket + rep
        xfast.insert(newNums[0], newNums, newNumsSize);
    }
}