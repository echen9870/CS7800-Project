import java.util.HashMap;
import java.util.concurrent.locks.StampedLock;

public class XFastTree {
    // Internal trie nodes only need min/max leaf pointers
    public static class InternalNode {
        public Node minLeaf;
        public Node maxLeaf;

        public InternalNode(Node minLeaf, Node maxLeaf) {
            this.minLeaf = minLeaf;
            this.maxLeaf = maxLeaf;
        }
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

        public Node(long key, long[] nums, int numsSize) {
            super(null, null);
            this.key = key;
            this.minLeaf = this;
            this.maxLeaf = this;
            this.nums = nums;
            this.numsSize = numsSize;
            this.bucketRw = new StampedLock();
        }
    }

    // universe = 2**bits
    public int bits;
    public HashMap<Long, InternalNode>[] level;

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

        this.level[0].put(0L, new InternalNode(null, null));

        this.headLeaf = null;
        this.tailLeaf = null;

        this.size = 0;
    }

    public int bitAtDepth(long x, int depth) {
        int shift = this.bits - depth - 1;
        return (int) ((x >>> shift) & 1);
    }

    // Note: Don't call public functions that use locks inside of each other, as it
    // might lead to deadlocks
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

    int longestPrefixLenNoLock(long x) {
        int low = 1; // level 0 (root) always exists, skip it
        int high = this.bits - 1;

        while (low < high) {
            int mid = (low + high + 1) / 2;
            long prefix = x >>> (bits - mid);

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
        if (queryNoLock(x))
            return x;

        // If tree is empty or x > largest element return null
        if (this.headLeaf == null || x > this.tailLeaf.key)
            return null;

        // If x <= smallest element return the smallest element
        if (x <= this.headLeaf.key)
            return this.headLeaf.key;

        // Find the node located at the longest prefix length
        int prefixLen = longestPrefixLenNoLock(x);
        long prefix = x >>> (bits - prefixLen);
        InternalNode node = this.level[prefixLen].get(prefix);

        int nextBit = bitAtDepth(x, prefixLen);

        // If our next bit is 0
        if (nextBit == 0) {
            // If right child exists, return the smallest descendant in the right child,
            // else just return the largest key in this leaf
            long rightChildPrefix = (prefix << 1) | 1;
            InternalNode rightChild = this.level[prefixLen + 1].get(rightChildPrefix);
            if (rightChild != null)
                return rightChild.minLeaf.key;
        }
        return node.maxLeaf.next.key;
    }

    // callers holding rw lock should use this directly
    Long predecessorNoLock(long x) {
        // If element exists, return itself
        if (queryNoLock(x))
            return x;

        // If tree is empty or x < smallest element
        if (this.headLeaf == null || x < this.headLeaf.key)
            return null;

        // If x >= largest element in tree
        if (x >= this.tailLeaf.key)
            return this.tailLeaf.key;

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
    // DELETE
    // ----------------

    public boolean delete(long x) {
        long stamp = rw.writeLock();
        try {
            return deleteNoLock(x);
        } finally {
            rw.unlockWrite(stamp);
        }
    }

    // Caller must hold writeLock
    boolean deleteNoLock(long x) {
        Node leaf = (Node) this.level[this.bits].get(x);
        if (leaf == null)
            return false;

        Node prevLeaf = leaf.prev;
        Node nextLeaf = leaf.next;

        // Unlink from doubly linked list
        if (prevLeaf != null)
            prevLeaf.next = nextLeaf;
        else
            this.headLeaf = nextLeaf;

        if (nextLeaf != null)
            nextLeaf.prev = prevLeaf;
        else
            this.tailLeaf = prevLeaf;

        // Remove from leaf level
        this.level[this.bits].remove(x);
        this.size--;

        // Walk up the trie: remove or update ancestor nodes
        for (int depth = this.bits - 1; depth >= 0; depth--) {
            long prefix = (depth == 0) ? 0L : (x >>> (bits - depth));
            InternalNode node = this.level[depth].get(prefix);
            
            long leftPrefix = prefix << 1;
            long rightPrefix = (prefix << 1) | 1;
            InternalNode leftChild = this.level[depth + 1].get(leftPrefix);
            InternalNode rightChild = this.level[depth + 1].get(rightPrefix);

            if (leftChild == null && rightChild == null) {
                if (depth == 0) {
                    // Keep root but clear min/max
                    node.minLeaf = null;
                    node.maxLeaf = null;
                } else {
                    this.level[depth].remove(prefix);
                }
            } else {
                node.minLeaf = (leftChild != null) ? leftChild.minLeaf : rightChild.minLeaf;
                node.maxLeaf = (rightChild != null) ? rightChild.maxLeaf : leftChild.maxLeaf;
            }
        }

        return true;
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
        if (queryNoLock(x))
            return false;

        // Create the leaf and link it in the linked list
        Node leaf = new Node(x, list, listSize);

        Long successorKey = successorNoLock(x);
        Node successorNode = (successorKey != null) ? queryNodeNoLock(successorKey) : null;
        Node predecessorNode = (successorNode != null) ? successorNode.prev : this.tailLeaf;

        linkBetweenNoLock(leaf, predecessorNode, successorNode);
        this.level[this.bits].put(x, leaf);

        // Insert upwards
        for (int depth = 0; depth < this.bits; depth++) {
            long prefix = (depth == 0) ? 0L : (x >>> (bits - depth));
            InternalNode node = this.level[depth].get(prefix);
            // Initialize node if not initialized
            if (node == null) {
                node = new InternalNode(leaf, leaf);
                this.level[depth].put(prefix, node);
            } else {
                // Set min and max
                if (node.minLeaf == null || leaf.key < node.minLeaf.key)
                    node.minLeaf = leaf;
                if (node.maxLeaf == null || leaf.key > node.maxLeaf.key)
                    node.maxLeaf = leaf;
            }            
        }
        this.size += 1;
        return true;
    }
}
