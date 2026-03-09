package xFast;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

import yFast.PrimitiveArray;

public class ConcurrentXFastTrie {

    // universe = 2**bits
    public final int bits;
    public ConcurrentHashMap<Long, InternalNode>[] level;

    // start/end of leaf linked list
    public volatile Node headLeaf;
    public volatile Node tailLeaf;

    public final AtomicLong size = new AtomicLong(0);

    // lowestFullLevel: deepest level d where all 2^d nodes exist.
    // locks[]: one StampedLock per node at the LFL
    // levelLock: global lock — readLock held during ops, writeLock only during LFL transitions.
    // maxLFL: maximum LFL to cap partition lock count at ~3*maxThreads.
    public volatile int lowestFullLevel;
    public volatile StampedLock[] locks;
    public final StampedLock levelLock = new StampedLock();
    public final int maxLFL;

    @SuppressWarnings("unchecked")
    public ConcurrentXFastTrie(int bits) {
        this(bits, 1);
    }

    @SuppressWarnings("unchecked")
    public ConcurrentXFastTrie(int bits, int maxThreads) {
        this.bits = bits;

        // maxLFL: cap at 2^maxLFL >= 3 * maxThreads partition locks
        int maxLevel = 0;
        while ((1 << maxLevel) < 3 * maxThreads) maxLevel++;
        this.maxLFL = Math.min(maxLevel, bits);

        this.level = new ConcurrentHashMap[bits + 1];
        for (int i = 0; i <= bits; i++) this.level[i] = new ConcurrentHashMap<>();
        this.level[0].put(0L, new InternalNode(null, null));

        this.headLeaf = null;
        this.tailLeaf = null;

        this.lowestFullLevel = 0;
        this.locks = new StampedLock[]{ new StampedLock() };
    }

    // Single volatile read of locks[] — derives lfl from length to avoid TOCTOU
    // with lowestFullLevel. locks.length = 2^lfl, so lfl = numberOfTrailingZeros(length).
    public StampedLock getLock(long x) {
        StampedLock[] snap = locks;
        int lfl = Integer.numberOfTrailingZeros(snap.length);
        return snap[(int)(x >>> (bits - lfl))];
    }

    // Caller must hold writeLock
    private void linkBetweenNoLock(Node leaf, Node predNode, Node succNode) {
        leaf.prev = predNode;
        leaf.next = succNode;
        if (predNode != null) predNode.next = leaf;
        else this.headLeaf = leaf;
        if (succNode != null) succNode.prev = leaf;
        else this.tailLeaf = leaf;
    }

    int longestPrefixLenNoLock(long x) {
        int low = this.lowestFullLevel;
        int high = this.bits - 1;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (this.level[mid].containsKey(x >>> (bits - mid))) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    // Note: Don't call public functions that use locks inside of each other, as it
    // might lead to deadlocks
    public boolean query(long x) {
        while (true) {
            StampedLock fgl = getLock(x);
            long stamp = fgl.tryOptimisticRead();
            boolean result = queryNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    boolean queryNoLock(long x) {
        return this.level[this.bits].containsKey(x);
    }

    public Node queryNode(long x) {
        while (true) {
            StampedLock fgl = getLock(x);
            long stamp = fgl.tryOptimisticRead();
            Node result = queryNodeNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    public Node queryNodeNoLock(long x) {
        return (Node) this.level[this.bits].get(x);
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        while (true) {
            StampedLock fgl = getLock(x);
            long stamp = fgl.tryOptimisticRead();
            Long result = successorNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    // Assumes x is NOT in the tree. Returns the successor Node, or null if none.
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

        int bitAtDepth = ((byte)(x >>> (this.bits - prefixLen - 1)) & 1);
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

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        while (true) {
            StampedLock fgl = getLock(x);
            long stamp = fgl.tryOptimisticRead();
            Long result = predecessorNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    // Returns the predecessor Node of x, or x's own Node if x is in the tree.
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

    public Node predecessorNode(long x) {
        while (true) {
            StampedLock fgl = getLock(x);
            long stamp = fgl.tryOptimisticRead();
            Node result = predecessorNodeNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    public Long predecessorNoLock(long x) {
        Node n = predecessorNodeNoLock(x);
        return n != null ? n.key : null;
    }

    public boolean delete(long x) {
        long levelStamp = levelLock.readLock();
        boolean deleted;
        try {
            Node leaf = queryNodeNoLock(x);
            if (leaf == null) return false;

            Node prevLeaf = leaf.prev;
            Node nextLeaf = leaf.next;

            // 3-lock in key order: prev.key < x < next.key.
            // prevLock needed when nextLeaf==null (deleting tail) to protect tailLeaf update
            // against concurrent new-max insertions that lock L(tailLeaf).
            StampedLock prevLock = (prevLeaf != null) ? getLock(prevLeaf.key) : null;
            StampedLock xLock   = getLock(x);
            StampedLock succLock = (nextLeaf != null) ? getLock(nextLeaf.key) : null;

            long prevStamp = (prevLock != null && prevLock != xLock) ? prevLock.writeLock() : -1;
            long xStamp    = xLock.writeLock();
            long succStamp = (succLock != null && succLock != xLock && succLock != prevLock) ? succLock.writeLock() : -1;
            try {
                deleted = deleteNoLock(x);
            } finally {
                if (succStamp != -1) succLock.unlockWrite(succStamp);
                xLock.unlockWrite(xStamp);
                if (prevStamp != -1) prevLock.unlockWrite(prevStamp);
            }
        } finally {
            levelLock.unlockRead(levelStamp);
        }
        // Check if we have to decrement level
        if (deleted) {
            int lfl = this.lowestFullLevel;
            if (lfl > 0 && this.level[lfl].size() < (1L << lfl)) {
                long stamp = this.levelLock.writeLock();
                lfl = this.lowestFullLevel;
                if (lfl > 0 && this.level[lfl].size() < (1L << lfl)) retreatLFL();
                this.levelLock.unlockWrite(stamp);
            }
        }
        return deleted;
    }

    // Caller must hold levelLock.readLock() and LFL write locks for prev, x, and next.
    public boolean deleteNoLock(long x) {
        Node leaf = (Node) this.level[this.bits].remove(x);
        if (leaf == null) return false;
        size.decrementAndGet();

        Node prevLeaf = leaf.prev;
        Node nextLeaf = leaf.next;

        // Unlink from doubly linked list
        if (prevLeaf != null) prevLeaf.next = nextLeaf;
        else this.headLeaf = nextLeaf;
        if (nextLeaf != null) nextLeaf.prev = prevLeaf;
        else this.tailLeaf = prevLeaf;

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
                InternalNode node = this.level[depth].get(prefix);
                Node newMin = (leftChild != null) ? leftChild.minLeaf : rightChild.minLeaf;
                Node newMax = (rightChild != null) ? rightChild.maxLeaf : leftChild.maxLeaf;
                if (node.minLeaf == newMin && node.maxLeaf == newMax) break;
                node.minLeaf = newMin;
                node.maxLeaf = newMax;
            }
        }
        return true;
    }

    public boolean insert(long x) { return insert(x, null); }

    public boolean insert(long x, PrimitiveArray bucket) {
        long levelStamp = levelLock.readLock();
        boolean inserted;
        try {
            Node succNode = successorNodeNoLock(x);
            // predNode used only to determine predLock — NOT passed into insertNoLock.
            // When succNode==null (new max), predNode=tailLeaf; locking L(tailLeaf) serializes
            // concurrent new-max insertions that would otherwise race on tailLeaf and tail.next.
            Node predNode = (succNode != null) ? succNode.prev : this.tailLeaf;

            // 3-lock in key order: pred.key < x < succ.key.
            StampedLock predLock = (predNode != null) ? getLock(predNode.key) : null;
            StampedLock xLock   = getLock(x);
            StampedLock succLock = (succNode != null) ? getLock(succNode.key) : null;

            long predStamp = (predLock != null && predLock != xLock) ? predLock.writeLock() : -1;
            long xStamp    = xLock.writeLock();
            long succStamp = (succLock != null && succLock != xLock && succLock != predLock) ? succLock.writeLock() : -1;
            try {
                inserted = insertNoLock(x, bucket);
            } finally {
                if (succStamp != -1) succLock.unlockWrite(succStamp);
                xLock.unlockWrite(xStamp);
                if (predStamp != -1) predLock.unlockWrite(predStamp);
            }
        } finally {
            levelLock.unlockRead(levelStamp);
        }
        // Check if we can increment level
        if (inserted) {
            int lfl = this.lowestFullLevel;
            if (lfl < this.maxLFL && this.level[lfl + 1].size() == (1L << (lfl + 1))) {
                long stamp = this.levelLock.writeLock();
                lfl = this.lowestFullLevel;
                if (lfl < this.maxLFL && this.level[lfl + 1].size() == (1L << (lfl + 1))) advanceLFL();
                this.levelLock.unlockWrite(stamp);
            }
        }
        return inserted;
    }

    // Caller must hold levelLock.readLock() and LFL write locks for pred, x, and succ.
    public boolean insertNoLock(long x, PrimitiveArray bucket) {
        if (queryNoLock(x)) return false;
        Node leaf = new Node(x, bucket);
        Node succNode = successorNodeNoLock(x);
        Node predNode = (succNode != null) ? succNode.prev : this.tailLeaf;
        insertLeafNoLock(leaf, predNode, succNode);
        return true;
    }

    // Variant that skips the redundant successor search — caller provides pred/succ directly
    public boolean insertNoLockBetween(long x, PrimitiveArray bucket, Node predNode, Node succNode) {
        if (queryNoLock(x)) return false;
        insertLeafNoLock(new Node(x, bucket), predNode, succNode);
        return true;
    }

    // Re-insert an existing leaf node under its current key.
    // Caller must have already called deleteNoLock for the old key and updated node.key.
    public void reinsertNodeNoLock(Node leaf, Node predNode, Node succNode) {
        insertLeafNoLock(leaf, predNode, succNode);
    }

    // Shared leaf insertion: link, add to levels, increment size
    private void insertLeafNoLock(Node leaf, Node predNode, Node succNode) {
        linkBetweenNoLock(leaf, predNode, succNode);
        this.level[this.bits].put(leaf.key, leaf);
        for (int depth = this.bits - 1; depth >= 0; depth--) {
            long prefix = leaf.key >>> (bits - depth);
            InternalNode node = this.level[depth].get(prefix);
            if (node == null) {
                this.level[depth].put(prefix, new InternalNode(leaf, leaf));
            } else {
                boolean changed = false;
                if (node.minLeaf == null || leaf.key < node.minLeaf.key) { node.minLeaf = leaf; changed = true; }
                if (node.maxLeaf == null || leaf.key > node.maxLeaf.key) { node.maxLeaf = leaf; changed = true; }
                if (!changed) break;
            }
        }
        size.incrementAndGet();
    }

    // --------------------
    // LFL TRANSITIONS
    // --------------------

    // Advance LFL by one if the next level is fully populated.
    // Will not advance above maxLFL to cap partition lock count.
    public void advanceLFL() {
        if (this.lowestFullLevel >= this.maxLFL) return;
        StampedLock[] newLocks = new StampedLock[(int) 1L << (this.lowestFullLevel + 1)];
        for (int i = 0; i < newLocks.length; i++) newLocks[i] = new StampedLock();
        this.locks = newLocks;
        this.lowestFullLevel += 1;
    }

    // Retreat LFL by one if the current level is no longer fully populated.
    public void retreatLFL() {
        if (this.lowestFullLevel <= 0) return;
        StampedLock[] newLocks = new StampedLock[(int) 1L << (this.lowestFullLevel - 1)];
        for (int i = 0; i < newLocks.length; i++) newLocks[i] = new StampedLock();
        this.locks = newLocks;
        this.lowestFullLevel -= 1;
    }
}
