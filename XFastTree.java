import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class XFastTree {
    public static class Node {

    // Doubly linked list for the leaf nodes
    public Node prev;
    public Node next;

    // The sub universe for the leaf node X-Fast trie in a Y-Fast Trie
    public int[] nums;
    public int numsSize;

    // Val
    public int key;

    // Smallest/largest descendant leaf node
    public Node minLeaf;
    public Node maxLeaf;

    // Concurrency control
    public ReentrantReadWriteLock bucketRw = new ReentrantReadWriteLock();
    public Lock bucketReadLock = bucketRw.readLock();
    public Lock bucketWriteLock = bucketRw.writeLock();

    public boolean isLeaf;

    public Node() {
        this.prev = null;
        this.next = null;
        this.key = 0;
        this.minLeaf = null;
        this.maxLeaf = null;
        this.isLeaf = false;
        this.nums = null;
        this.numsSize = 0;
        this.bucketRw = new ReentrantReadWriteLock();
        this.bucketReadLock = bucketRw.readLock();
        this.bucketWriteLock = bucketRw.writeLock();
    }
}

    // universe = 2**bits
    public int bits;
    public HashMap<Integer, Node>[] level;
    public int[] shiftAtDepth;

    public Node root;
    // start/end of leaf linked list
    public Node headLeaf;
    public Node tailLeaf;

    public int size;

    // One write at a time, multiple (query, successor, predecessor) reads at a time
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final Lock readLock = rw.readLock();
    private final Lock writeLock = rw.writeLock();

    @SuppressWarnings("unchecked")
    public XFastTree(int bits) {
        this.bits = bits;

        this.level = (HashMap<Integer, Node>[]) new HashMap[bits + 1];
        for (int i = 0; i <= bits; i++) {
            this.level[i] = new HashMap<>();
        }

        this.shiftAtDepth = new int[bits + 1];
        for (int depth = 0; depth <= bits; depth++) {
            this.shiftAtDepth[depth] = bits - depth;
        }

        this.root = new Node();
        this.level[0].put(0, this.root);

        this.headLeaf = null;
        this.tailLeaf = null;

        this.size = 0;
    }

    public int prefixAtDepth(int x, int depth) {
        if (depth == 0) {
            return 0;
        }
        return x >>> this.shiftAtDepth[depth];
    }

    public int bitAtDepth(int x, int depth) {
        int shift = this.bits - depth - 1;
        return (x >>> shift) & 1;
    }

    // Note: Don't call public functions that use locks inside of each other, as it might lead to deadlocks

    public boolean query(int x) {
        readLock.lock();
        try {
            return queryNoLock(x);
        } finally {
            readLock.unlock();
        }
    }

    private boolean queryNoLock(int x) {
        return this.level[this.bits].containsKey(x);
    }

    public Node queryNode(int x) {
        readLock.lock();
        try {
            return queryNodeNoLock(x);
        } finally {
            readLock.unlock();
        }
    }

    private Node queryNodeNoLock(int x) {
        return this.level[this.bits].get(x);
    }

    public int longestPrefixLen(int x) {
        readLock.lock();
        try {
            return longestPrefixLenNoLock(x);
        } finally {
            readLock.unlock();
        }
    }

    private int longestPrefixLenNoLock(int x) {
        int low = 0;
        int high = this.bits - 1;

        while (low < high) {
            int mid = (low + high + 1) / 2;
            int prefix = (mid == 0) ? 0 : (x >>> this.shiftAtDepth[mid]);

            if (this.level[mid].containsKey(prefix)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    // smallest key >= x, or null if none
    public Integer successor(int x) {
        readLock.lock();
        try {
            return successorNoLock(x);
        } finally {
            readLock.unlock();
        }
    }

    private Integer successorNoLock(int x) {
        if (this.headLeaf == null) {
            return null;
        }

        if (x <= this.headLeaf.key) {
            return this.headLeaf.key;
        }
        if (x > this.tailLeaf.key) {
            return null;
        }

        Node existingLeaf = queryNodeNoLock(x);
        if (existingLeaf != null) {
            return x;
        }

        int prefixLen = longestPrefixLenNoLock(x);
        int prefix = (prefixLen == 0) ? 0 : (x >>> this.shiftAtDepth[prefixLen]);
        Node node = this.level[prefixLen].get(prefix);

        int nextBit = bitAtDepth(x, prefixLen);

        int nextDepth = prefixLen + 1;
        if (nextDepth > this.bits) {
            return null;
        }

        if (nextBit == 0) {
            int rightChildPrefix = (prefix << 1) | 1;
            Node rightChild = this.level[nextDepth].get(rightChildPrefix);

            if (rightChild != null && rightChild.minLeaf != null) {
                return rightChild.minLeaf.key;
            }

            if (node == null || node.maxLeaf == null || node.maxLeaf.next == null) {
                return null;
            }
            return node.maxLeaf.next.key;
        }

        if (node == null || node.maxLeaf == null || node.maxLeaf.next == null) {
            return null;
        }
        return node.maxLeaf.next.key;
    }

    // largest key <= x, or null if none
    public Integer predecessor(int x) {
        readLock.lock();
        try {
            return predecessorNoLock(x);
        } finally {
            readLock.unlock();
        }
    }

    private Integer predecessorNoLock(int x) {
        if (this.headLeaf == null) {
            return null;
        }

        if (x < this.headLeaf.key) {
            return null;
        }
        if (x >= this.tailLeaf.key) {
            return this.tailLeaf.key;
        }

        Node existingLeaf = queryNodeNoLock(x);
        if (existingLeaf != null) {
            return x;
        }

        Integer successorKey = successorNoLock(x);
        if (successorKey == null) {
            return this.tailLeaf.key;
        }

        Node successorLeaf = queryNodeNoLock(successorKey);
        if (successorLeaf == null || successorLeaf.prev == null) {
            return null;
        }

        return successorLeaf.prev.key;
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

    public boolean insert(int x) {
        return insert(x, null, 0);
    }

    public boolean insert(int x, int[] list, int listSize) {
        writeLock.lock();
        try {
            if (queryNoLock(x)) {
                return false;
            }

            Node leaf = new Node();
            leaf.isLeaf = true;
            leaf.key = x;
            leaf.minLeaf = leaf;
            leaf.maxLeaf = leaf;
            leaf.nums = list;
            leaf.numsSize = listSize;

            Integer successorKey = successorNoLock(x);
            Node successorNode = (successorKey != null) ? queryNodeNoLock(successorKey) : null;
            Node predecessorNode = (successorNode != null) ? successorNode.prev : this.tailLeaf;

            linkBetweenNoLock(leaf, predecessorNode, successorNode);
            this.level[this.bits].put(x, leaf);

            for (int depth = 0; depth < this.bits; depth++) {
                int prefix = (depth == 0) ? 0 : (x >>> this.shiftAtDepth[depth]);
                Node node = this.level[depth].get(prefix);

                if (node == null) {
                    node = new Node();
                    this.level[depth].put(prefix, node);
                }

                if (node.minLeaf == null || leaf.key < node.minLeaf.key) {
                    node.minLeaf = leaf;
                }
                if (node.maxLeaf == null || leaf.key > node.maxLeaf.key) {
                    node.maxLeaf = leaf;
                }
            }

            if (this.root.minLeaf == null || leaf.key < this.root.minLeaf.key) {
                this.root.minLeaf = leaf;
            }
            if (this.root.maxLeaf == null || leaf.key > this.root.maxLeaf.key) {
                this.root.maxLeaf = leaf;
            }

            this.size += 1;
            return true;
        } finally {
            writeLock.unlock();
        }
    }
}