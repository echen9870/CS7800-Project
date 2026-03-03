import java.util.*;

public class YFastTree {
    public int bits = 0;

    public XFastTree xfast;

    public YFastTree(int b) {
        this.bits = b;
        this.xfast = new XFastTree(b);
    }

    public boolean query(long x) {
        // get rep, return null if rep is null
        Long rep = xfast.predecessor(x);
        if (rep == null)
            return false;
        XFastTree.Node node = xfast.queryNode(rep);

        long[] nums = node.nums;
        int numsSize = node.numsSize;

        // binary search inside bucket
        int pos = Arrays.binarySearch(nums, 0, numsSize, x);

        return (pos >= 0);
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        // get rep
        Long rep = xfast.predecessor(x);
        XFastTree.Node node = (rep == null) ? xfast.headLeaf : xfast.queryNode(rep);

        // get node
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        long last = nums[numsSize - 1];

        if (x <= last) {
            int idx = Arrays.binarySearch(nums, 0, numsSize, x);
            if (idx >= 0)
                return nums[idx];
            idx = -idx - 1;
            return (idx < numsSize) ? nums[idx] : null;
        }

        // If our last element is less than x, we have to go to next node (check null)
        return (node.next == null) ? null : node.next.nums[0];
    }

    public void insert(long x) {
        // max bucket size
        int maxSize = 16 * bits;

        // find bucket rep
        Long rep = xfast.predecessor(x);

        // x is smaller than smallest rep
        if (rep == null) {
            long[] nums = new long[maxSize];
            nums[0] = x;
            xfast.insert(x, nums, 1);
            return;
        }

        // Get representative
        XFastTree.Node node = xfast.queryNode(rep);
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        // sorted insert into tree
        int pos = Arrays.binarySearch(nums, 0, numsSize, x);
        if (pos >= 0)
            return;
        pos = -pos - 1;
        System.arraycopy(nums, pos, nums, pos + 1, numsSize - pos);
        nums[pos] = x;
        node.numsSize = numsSize + 1;

        // split if too big
        if (maxSize == node.numsSize) {
            splitList(rep);
        }
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        Long rep = xfast.predecessor(x);

        // No predecessor exists
        if (rep == null)
            return null;

        // Find predecessor bucket
        XFastTree.Node node = xfast.queryNode(rep);
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        // Return position
        int pos = Arrays.binarySearch(nums, 0, numsSize, x);
        if (pos >= 0)
            return nums[pos];
        pos = -pos - 1;
        return (pos > 0) ? nums[pos - 1] : null;
    }

    public boolean delete(long x) {
        Long rep = xfast.predecessor(x);
        if (rep == null)
            return false;

        XFastTree.Node node = xfast.queryNode(rep);
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        int pos = Arrays.binarySearch(nums, 0, numsSize, x);

        // x not in tree
        if (pos < 0)
            return false;

        // Remove x from nums array
        System.arraycopy(nums, pos + 1, nums, pos, numsSize - pos - 1);
        node.numsSize = numsSize - 1;

        // x was the bucket representative — update XFast
        if (x == rep) {
            xfast.delete(x);
            if (node.numsSize > 0) {
                long newRep = nums[0];
                xfast.insert(newRep, nums, node.numsSize);
            }
        }
        return true;
    }

    public void splitList(long rep) {
        // query node and nums
        XFastTree.Node node = xfast.queryNode(rep);
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        // split in half and create new list
        int half = numsSize / 2;
        int newNumsSize = numsSize - half;
        long[] newNums = new long[16 * bits];
        System.arraycopy(nums, half, newNums, 0, newNumsSize);
        node.numsSize = half;

        // We need to clear the right half of the old array
        Arrays.fill(nums, half, numsSize, 0L);

        // register new bucket + rep
        xfast.insert(newNums[0], newNums, newNumsSize);
    }
}
