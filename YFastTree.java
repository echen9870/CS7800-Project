import java.util.*;

public class YFastTree {
    public int bits = 0;

    public XFastTree xfast;

    public YFastTree(int b) {
        this.bits = b;
        this.xfast = new XFastTree(b);
    }

    public boolean query(int x) {
        // get rep
        Integer rep = xfast.predecessor(x);
        XFastTree.Node node = (rep == null) ? xfast.headLeaf : xfast.queryNode(rep);
        if (node == null) return false;

        int[] nums = node.nums;
        int numsSize = node.numsSize;

        // binary search inside bucket
        int pos = Arrays.binarySearch(nums, 0, numsSize, x);

        return (pos >= 0);
    }

    // smallest key >= x, or null if none
    public Integer successor(int x) {
        // get rep
        Integer rep = xfast.predecessor(x);
        XFastTree.Node node = (rep == null) ? xfast.headLeaf : xfast.queryNode(rep);
        if (node == null) return null;

        // get node
        int[] nums = node.nums;
        int numsSize = node.numsSize;

        int last = nums[numsSize - 1];

        if (x <= last) {
            int idx = Arrays.binarySearch(nums, 0, numsSize, x);
            if (idx >= 0) return nums[idx];
            idx = -idx - 1;
            return (idx < numsSize) ? nums[idx] : null;
        }

        // If our last element is less than x, we have to go to next node
        XFastTree.Node nextNode = node.next;
        if (nextNode == null) return null;

        nums = nextNode.nums;
        numsSize = nextNode.numsSize;
        if (nums == null || numsSize == 0) return null;
        return nums[0];
    }

    public void insert(int x) {
        // max bucket size
        int maxSize = 16 * bits;

        // find bucket rep
        Integer rep = xfast.predecessor(x);

        // x is smaller than smallest rep
        if (rep == null) {
            int[] nums = new int[maxSize];
            nums[0] = x;
            xfast.insert(x, nums, 1);
            return;
        }

        // Get representative
        XFastTree.Node node = xfast.queryNode(rep);
        int[] nums = node.nums;
        int numsSize = node.numsSize;

        // insert sorted
        int pos = Arrays.binarySearch(nums, 0, numsSize, x);
        if (pos >= 0) return;
        pos = -pos - 1;

        if (pos < numsSize) {
            System.arraycopy(nums, pos, nums, pos + 1, numsSize - pos);
        }
        nums[pos] = x;
        node.numsSize = numsSize + 1;

        // split if too big
        if (maxSize == node.numsSize) {
            splitList(rep);
        }
        return;
    }

    public void splitList(int rep) {
        XFastTree.Node node = xfast.queryNode(rep);
        if (node == null) return;

        int[] nums = node.nums;
        int numsSize = node.numsSize;
        if (nums == null) return;

        // split in half
        int half = numsSize / 2;
        if (half <= 0 || half >= numsSize) return;

        int newNumsSize = numsSize - half;
        int[] newNums = new int[16 * bits];
        System.arraycopy(nums, half, newNums, 0, newNumsSize);

        node.numsSize = half;

        // register new bucket + rep
        xfast.insert(newNums[0], newNums, newNumsSize);
    }
}