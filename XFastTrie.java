import java.util.HashMap;
import java.util.concurrent.locks.StampedLock;

public class XFastTrie implements XFastTrieInterface {
    // Internal trie nodes only need min/max leaf pointers
    public static class InternalNode {
        public volatile Node minLeaf;
        public volatile Node maxLeaf;

        public InternalNode(Node minLeaf, Node maxLeaf) {
            this.minLeaf = minLeaf;
            this.maxLeaf = maxLeaf;
        }
    }

    // Leaf nodes carry bucket data and linked-list pointers
    public static class Node extends InternalNode {

        // Doubly linked list for the leaf nodes
        public volatile Node prev;
        public volatile Node next;

        // The sub universe for the leaf node X-Fast trie in a Y-Fast Trie
        public long[] nums;
        public volatile int numsSize;

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
    public volatile Node headLeaf;
    public volatile Node tailLeaf;

    public long size;

    // One write at a time, multiple (query, successor, predecessor) reads at a time
    public final StampedLock rw = new StampedLock();

    @SuppressWarnings("unchecked")
    public XFastTrie(int bits) {
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

    public StampedLock getLock(long x) { return rw; }

    public Node getHeadLeaf() { 
        return this.headLeaf; 
    }

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

        int longestPrefixLenNoLock(long x) {
        int low = 1; // level 0 (root) always exists, skip it
        int high = this.bits - 1;

        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (this.level[mid].containsKey(x >>> (bits - mid))) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    // Note: Don't call public functions that use locks inside of each other, as it
    // might lead to deadlocks
    public boolean query(long x) {
        while (true) {
            long stamp = rw.tryOptimisticRead();
            boolean result = queryNoLock(x);
            if (rw.validate(stamp)) return result;
        }
    }

    boolean queryNoLock(long x) {
        return this.level[this.bits].containsKey(x);
    }

    public Node queryNode(long x) {
        while (true) {
            long stamp = rw.tryOptimisticRead();
            Node result = queryNodeNoLock(x);
            if (rw.validate(stamp)) return result;
        }
    }

    Node queryNodeNoLock(long x) {
        return (Node) this.level[this.bits].get(x);
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        while (true) {
            long stamp = rw.tryOptimisticRead();
            Long result = successorNoLock(x);
            if (rw.validate(stamp)) return result;
        }
    }

    // Assumes x is NOT in the tree. Returns the successor Node, or null if none.
    // prefix = x >>> (bits - depth) is 0 at depth=0 for any valid key (x < 2^bits).
    private Node successorNodeNoLock(long x) {
        Node head = this.headLeaf;
        if (head == null) return null;
        Node tail = this.tailLeaf;
        if (x > tail.key) return null;
        if (x <= head.key) return head;

        int prefixLen = longestPrefixLenNoLock(x);
        long prefix = x >>> (bits - prefixLen);
        InternalNode node = this.level[prefixLen].get(prefix);
        if (node == null) return null;

        int bitAtDepth = ((byte) (x >>> (this.bits - prefixLen - 1)) & 1);
        if (bitAtDepth == 0) {
            InternalNode rightChild = this.level[prefixLen + 1].get((prefix << 1) | 1);
            if (rightChild != null) {
                Node minLeaf = rightChild.minLeaf;
                if (minLeaf != null) return minLeaf;
            }
        }
        Node maxLeaf = node.maxLeaf;
        return maxLeaf != null ? maxLeaf.next : null;
    }

    Long successorNoLock(long x) {
        if (queryNoLock(x)) return x;
        Node n = successorNodeNoLock(x);
        return n != null ? n.key : null;
    }

    public Node predecessorNode(long x) {
        while (true) {
            long stamp = rw.tryOptimisticRead();
            Node result = predecessorNodeNoLock(x);
            if (rw.validate(stamp)) return result;
        }
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        while (true) {
            long stamp = rw.tryOptimisticRead();
            Long result = predecessorNoLock(x);
            if (rw.validate(stamp)) return result;
        }
    }

    // Returns the predecessor Node of x, or x's own Node if x is in the tree.
    // We just piggyback on top of the actual successor function
    public Node predecessorNodeNoLock(long x) {
        Node existing = queryNodeNoLock(x);
        if (existing != null) return existing;
        Node head = this.headLeaf;
        if (head == null || x < head.key) return null;
        Node tail = this.tailLeaf;
        if (x >= tail.key) return tail;
        Node succ = successorNodeNoLock(x);
        return succ != null ? succ.prev : null;
    }

    // callers holding rw lock should use this directly
    public Long predecessorNoLock(long x) {
        Node n = predecessorNodeNoLock(x);
        return n != null ? n.key : null;
    }

    public boolean delete(long x) {
        long stamp = rw.writeLock();
        try {
            return deleteNoLock(x);
        } finally {
            rw.unlockWrite(stamp);
        }
    }

    // Caller must hold writeLock
    public boolean deleteNoLock(long x) {
        Node leaf = (Node) this.level[this.bits].remove(x);
        if (leaf == null) return false;

        this.size--;

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

        // Walk up the trie: remove or update ancestor nodes.
        // Break early once a node's min/max are unchanged — ancestors are unaffected.
        for (int depth = this.bits - 1; depth >= 0; depth--) {
            long prefix = x >>> (bits - depth);

            InternalNode leftChild  = this.level[depth + 1].get(prefix << 1);
            InternalNode rightChild = this.level[depth + 1].get((prefix << 1) | 1);

            if (leftChild == null && rightChild == null) {
                if (depth == 0) {
                    // root stays — always at key 0, just clear its pointers
                    InternalNode root = this.level[0].get(0L);
                    root.minLeaf = null;
                    root.maxLeaf = null;
                } else {
                    this.level[depth].remove(prefix);
                }
            } else {
                // only look up the parent when we actually need to update it
                InternalNode node = this.level[depth].get(prefix);
                Node newMin = (leftChild != null) ? leftChild.minLeaf : rightChild.minLeaf;
                Node newMax = (rightChild != null) ? rightChild.maxLeaf : leftChild.maxLeaf;
                // Early break
                if (node.minLeaf == newMin && node.maxLeaf == newMax)
                    break;
                node.minLeaf = newMin;
                node.maxLeaf = newMax;
            }
        }

        return true;
    }

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
    public boolean insertNoLock(long x, long[] list, int listSize) {
        if (queryNoLock(x)) return false;

        Node leaf = new Node(x, list, listSize);

        Node successorNode = successorNodeNoLock(x);
        Node predecessorNode = (successorNode != null) ? successorNode.prev : this.tailLeaf;

        linkBetweenNoLock(leaf, predecessorNode, successorNode);
        this.level[this.bits].put(x, leaf);

        // Walk bottom-up (bits-1 → 0): create or update ancestor nodes.
        // Breaking early if node exists and min max doesn't change
        for (int depth = this.bits - 1; depth >= 0; depth--) {
            long prefix = x >>> (bits - depth);
            InternalNode node = this.level[depth].get(prefix);
            if (node == null) {
                this.level[depth].put(prefix, new InternalNode(leaf, leaf));
            } else {
                boolean canBreak = false;
                if (node.minLeaf == null || leaf.key < node.minLeaf.key) {
                    node.minLeaf = leaf;
                    canBreak = true;
                }
                if (node.maxLeaf == null || leaf.key > node.maxLeaf.key) {
                    node.maxLeaf = leaf;
                    canBreak = true;
                }
                if (!canBreak) break;
            }
        }
        this.size += 1;
        return true;
    }
}
