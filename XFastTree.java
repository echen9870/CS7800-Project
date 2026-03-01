import java.util.HashMap;
import java.util.concurrent.locks.StampedLock;

public class XFastTree {
    // Internal trie nodes only need min/max leaf pointers
    public static class InternalNode {
        public Node minLeaf;
        public Node maxLeaf;
    }

    // Leaf nodes carry bucket data and linked-list pointers
    public static class Node extends InternalNode {

        // Doubly linked list for the leaf nodes
        public Node prev;
        public Node next;

        // The sub universe for the leaf node X-Fast trie in a Y-Fast Trie
        public long[] nums;
        public int numsSize;

        // Val
        public long key;

        // Concurrency control
        public StampedLock bucketRw;

        public Node() {
            this.prev = null;
            this.next = null;
            this.key = 0L;
            this.nums = null;
            this.numsSize = 0;
            this.bucketRw = null;
        }
    }

    // universe = 2**bits
    public int bits;
    public HashMap<Long, InternalNode>[] level;
    public int[] shiftAtDepth;

    public InternalNode root;
    // start/end of leaf linked list
    public Node headLeaf;
    public Node tailLeaf;

    public int size;

    // One write at a time, multiple (query, successor, predecessor) reads at a time
    public final StampedLock rw = new StampedLock();

    @SuppressWarnings("unchecked")
    public XFastTree(int bits) {
        this.bits = bits;

        this.level = new HashMap[bits + 1];
        for (int i = 0; i <= bits; i++) {
            this.level[i] = new HashMap<>();
        }

        this.shiftAtDepth = new int[bits + 1];
        for (int depth = 0; depth <= bits; depth++) {
            this.shiftAtDepth[depth] = bits - depth;
        }

        this.root = new InternalNode();
        this.level[0].put(0L, this.root);

        this.headLeaf = null;
        this.tailLeaf = null;

        this.size = 0;
    }

    public long prefixAtDepth(long x, int depth) {
        if (depth == 0) {
            return 0L;
        }
        return x >>> this.shiftAtDepth[depth];
    }

    public int bitAtDepth(long x, int depth) {
        int shift = this.bits - depth - 1;
        return (int) ((x >>> shift) & 1);
    }

    // Note: Don't call public functions that use locks inside of each other, as it might lead to deadlocks
    public boolean query(long x) {
        long stamp = rw.tryOptimisticRead();
        boolean result = queryNoLock(x);
        if (!rw.validate(stamp)) {
            stamp = rw.readLock();
            try {
                result = queryNoLock(x);
            } finally {
                rw.unlockRead(stamp);
            }
        }
        return result;
    }

    boolean queryNoLock(long x) {
        return this.level[this.bits].containsKey(x);
    }

    public Node queryNode(long x) {
        long stamp = rw.tryOptimisticRead();
        Node result = queryNodeNoLock(x);
        if (!rw.validate(stamp)) {
            stamp = rw.readLock();
            try {
                result = queryNodeNoLock(x);
            } finally {
                rw.unlockRead(stamp);
            }
        }
        return result;
    }

    Node queryNodeNoLock(long x) {
        return (Node) this.level[this.bits].get(x);
    }

    public int longestPrefixLen(long x) {
        long stamp = rw.tryOptimisticRead();
        int result = longestPrefixLenNoLock(x);
        if (!rw.validate(stamp)) {
            stamp = rw.readLock();
            try {
                result = longestPrefixLenNoLock(x);
            } finally {
                rw.unlockRead(stamp);
            }
        }
        return result;
    }

    int longestPrefixLenNoLock(long x) {
        int low = 1; // level 0 (root) always exists, skip it
        int high = this.bits - 1;

        while (low < high) {
            int mid = (low + high + 1) / 2;
            long prefix = x >>> this.shiftAtDepth[mid];

            if (this.level[mid].containsKey(prefix)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    Long successorNoLock(long x) {
        // If element exists, return itself
        if (queryNoLock(x)) return x;

        // If tree is empty or x > largest element return null
        if (this.headLeaf == null || x > this.tailLeaf.key) return null;

        // If x <= smallest element return the smallest element
        if (x <= this.headLeaf.key) return this.headLeaf.key;

        // Find the node located at the longest prefix length
        int prefixLen = longestPrefixLenNoLock(x);
        long prefix = x >>> this.shiftAtDepth[prefixLen];
        InternalNode node = this.level[prefixLen].get(prefix);

        int nextBit = bitAtDepth(x, prefixLen);

        // If our next bit is 0
        if (nextBit == 0) {
            // If right child exists, return the smallest descendant in the right child, else just return the largest key in this leaf
            long rightChildPrefix = (prefix << 1) | 1;
            InternalNode rightChild = this.level[prefixLen + 1].get(rightChildPrefix);
            if (rightChild != null) return rightChild.minLeaf.key;
        }
        return node.maxLeaf.next.key;
    }

    // callers holding rw lock should use this directly
    Long predecessorNoLock(long x) {
        // If element exists, return itself
        if (queryNoLock(x)) return x;

        // If tree is empty or x < smallest element
        if (this.headLeaf == null || x < this.headLeaf.key) return null;

        // If x >= largest element in tree
        if (x >= this.tailLeaf.key) return this.tailLeaf.key;

        // Find successor and return the prev
        Long successorKey = successorNoLock(x);
        Node successorLeaf = queryNodeNoLock(successorKey);
        return successorLeaf.prev.key;
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        long stamp = rw.tryOptimisticRead();
        Long result = null;
        try {
            result = successorNoLock(x);
        } catch (NullPointerException e) {
            stamp = 0;
        }
        if (!rw.validate(stamp)) {
            stamp = rw.readLock();
            try {
                result = successorNoLock(x);
            } finally {
                rw.unlockRead(stamp);
            }
        }
        return result;
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        long stamp = rw.tryOptimisticRead();
        Long result = null;
        try {
            result = predecessorNoLock(x);
        } catch (NullPointerException e) {
            stamp = 0;
        }
        if (!rw.validate(stamp)) {
            stamp = rw.readLock();
            try {
                result = predecessorNoLock(x);
            } finally {
                rw.unlockRead(stamp);
            }
        }
        return result;
    }

    // ----------------
    // WRITE HELPERS
    // ----------------

    // Caller must hold writeLock
    private void linkBetweenNoLock(Node leaf, Node predecessorNode, Node successorNode) {
        leaf.prev = predecessorNode;
        leaf.next = successorNode;

        if (predecessorNode != null) {
            predecessorNode.next = leaf;
        } else {
            this.headLeaf = leaf;
        }

        if (successorNode != null) {
            successorNode.prev = leaf;
        } else {
            this.tailLeaf = leaf;
        }
    }

    // ----------------
    // WRITE
    // ----------------

    public boolean insert(long x) {
        return insert(x, null, 0);
    }

    public boolean insert(long x, long[] list, int listSize) {
        long stamp = rw.writeLock();
        try {
            return insertNoLock(x, list, listSize);
        } finally {
            rw.unlockWrite(stamp);
        }
    }

    // callers already holding rw writeLock should use this directly
    boolean insertNoLock(long x, long[] list, int listSize) {
        if (queryNoLock(x)) return false;

        // Create the leaf and link it in the linked list
        Node leaf = new Node();
        leaf.key = x;
        leaf.minLeaf = leaf;
        leaf.maxLeaf = leaf;
        leaf.nums = list;
        leaf.numsSize = listSize;
        leaf.bucketRw = new StampedLock();

        Long successorKey = successorNoLock(x);
        Node successorNode = (successorKey != null) ? queryNodeNoLock(successorKey) : null;
        Node predecessorNode = (successorNode != null) ? successorNode.prev : this.tailLeaf;

        linkBetweenNoLock(leaf, predecessorNode, successorNode);
        this.level[this.bits].put(x, leaf);

        // Insert upwards
        for (int depth = 0; depth < this.bits; depth++) {
            long prefix = (depth == 0) ? 0L : (x >>> this.shiftAtDepth[depth]);
            InternalNode node = this.level[depth].get(prefix);
            // Initialize node is not initialized
            if (node == null) {
                node = new InternalNode();
                this.level[depth].put(prefix, node);
            }

            // Set min and max
            if (node.minLeaf == null || leaf.key < node.minLeaf.key) node.minLeaf = leaf;
            if (node.maxLeaf == null || leaf.key > node.maxLeaf.key) node.maxLeaf = leaf;
        }
        this.size += 1;
        return true;
    }
}
