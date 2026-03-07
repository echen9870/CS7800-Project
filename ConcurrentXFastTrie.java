import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

public class ConcurrentXFastTrie implements XFastTrieInterface {

    // universe = 2**bits
    public int bits;
    public ConcurrentHashMap<Long, XFastTrie.InternalNode>[] level;

    // start/end of leaf linked list
    public volatile XFastTrie.Node heafLeaf;
    public volatile XFastTrie.Node tailLeaf;

    public final AtomicLong size = new AtomicLong(0);

    // lowestFullLevel: deepest level d where all 2^d nodes exist.
    // locks[]: one StampedLock per node at the LFL 
    // levelLock: global lock — readLock held during ops, writeLock only during LFL transitions.
    public volatile int lowestFullLevel;
    public volatile StampedLock[] locks;
    public final StampedLock levelLock = new StampedLock();

    @SuppressWarnings("unchecked")
    public ConcurrentXFastTrie(int bits) {
        this.bits = bits;

        this.level = new ConcurrentHashMap[bits + 1];
        for (int i = 0; i <= bits; i++) {
            this.level[i] = new ConcurrentHashMap<>();
        }

        this.level[0].put(0L, new XFastTrie.InternalNode(null, null));

        this.heafLeaf = null;
        this.tailLeaf = null;

        this.lowestFullLevel = 0;
        this.locks = new StampedLock[]{ new StampedLock() };
    }

    // Single volatile read of locks[] — derives lfl from length to avoid TOCTOU
    // with lowestFullLevel. locks.length = 2^lfl, so lfl = numberOfTrailingZeros(length).
    // Used internally by ConcurrentXFastTrie's own public insert/delete/query.
    public StampedLock getRwLock(long x) {
        StampedLock[] snap = locks;
        int lfl = Integer.numberOfTrailingZeros(snap.length);
        return snap[(int)(x >>> (bits - lfl))];
    }

    public XFastTrie.Node getHeadLeaf() {
        return this.heafLeaf;
    }

    // Caller must hold writeLock
    private void linkBetweenNoLock(XFastTrie.Node leaf, XFastTrie.Node predecessorNode, XFastTrie.Node successorNode) {
        leaf.prev = predecessorNode;
        leaf.next = successorNode;

        if (predecessorNode != null) {
            predecessorNode.next = leaf;
        } else {
            this.heafLeaf = leaf;
        }

        if (successorNode != null) {
            successorNode.prev = leaf;
        } else {
            this.tailLeaf = leaf;
        }
    }

    int longestPrefixLenNoLock(long x) {
        int low = this.lowestFullLevel;
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
            StampedLock fgl = getRwLock(x);
            long stamp = fgl.tryOptimisticRead();
            boolean result = queryNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    boolean queryNoLock(long x) {
        return this.level[this.bits].containsKey(x);
    }

    public XFastTrie.Node queryNode(long x) {
        while (true) {
            StampedLock fgl = getRwLock(x);
            long stamp = fgl.tryOptimisticRead();
            XFastTrie.Node result = queryNodeNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    XFastTrie.Node queryNodeNoLock(long x) {
        return (XFastTrie.Node) this.level[this.bits].get(x);
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        while (true) {
            StampedLock fgl = getRwLock(x);
            long stamp = fgl.tryOptimisticRead();
            Long result = successorNoLock(x);
            if (fgl.validate(stamp))
                return result;
        }
    }

    // Assumes x is NOT in the tree. Returns the successor Node, or null if none.
    private XFastTrie.Node successorNodeNoLock(long x) {
        XFastTrie.Node head = this.heafLeaf;
        if (head == null) return null;
        XFastTrie.Node tail = this.tailLeaf;
        if (x > tail.key) return null;
        if (x <= head.key) return head;

        int prefixLen = longestPrefixLenNoLock(x);
        long prefix = x >>> (bits - prefixLen);
        XFastTrie.InternalNode node = this.level[prefixLen].get(prefix);
        if (node == null) return null;

        int bitAtDepth = ((byte)(x >>> (this.bits - prefixLen - 1)) & 1);
        if (bitAtDepth == 0) {
            XFastTrie.InternalNode rightChild = this.level[prefixLen + 1].get((prefix << 1) | 1);
            if (rightChild != null) {
                XFastTrie.Node minLeaf = rightChild.minLeaf;
                if (minLeaf != null) return minLeaf;
            }
        }
        XFastTrie.Node maxLeaf = node.maxLeaf;
        return maxLeaf != null ? maxLeaf.next : null;
    }

    Long successorNoLock(long x) {
        if (queryNoLock(x)) return x;
        XFastTrie.Node n = successorNodeNoLock(x);
        return n != null ? n.key : null;
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        while (true) {
            StampedLock fgl = getRwLock(x);
            long stamp = fgl.tryOptimisticRead();
            Long result = predecessorNoLock(x);
            if (fgl.validate(stamp))
                return result;
        }
    }

    // Returns the predecessor Node of x, or x's own Node if x is in the tree.
    public XFastTrie.Node predecessorNodeNoLock(long x) {
        XFastTrie.Node existing = queryNodeNoLock(x);
        if (existing != null) return existing;
        XFastTrie.Node head = this.heafLeaf;
        if (head == null || x < head.key) return null;
        XFastTrie.Node tail = this.tailLeaf;
        if (x >= tail.key) return tail;
        XFastTrie.Node succ = successorNodeNoLock(x);
        return succ != null ? succ.prev : null;
    }

    public XFastTrie.Node predecessorNode(long x) {
        while (true) {
            StampedLock fgl = getRwLock(x);
            long stamp = fgl.tryOptimisticRead();
            XFastTrie.Node result = predecessorNodeNoLock(x);
            if (fgl.validate(stamp)) return result;
        }
    }

    Long predecessorNoLock(long x) {
        XFastTrie.Node n = predecessorNodeNoLock(x);
        return n != null ? n.key : null;
    }

    public boolean delete(long x) {
        long levelStamp = levelLock.readLock();
        boolean deleted;
        try {
            XFastTrie.Node leaf = queryNodeNoLock(x);
            if (leaf == null) return false;

            XFastTrie.Node prevLeaf = leaf.prev;
            XFastTrie.Node nextLeaf = leaf.next;

            // 3-lock in key order: prev.key < x < next.key.
            // prevLock needed when nextLeaf==null (deleting tail) to protect tailLeaf update
            // against concurrent new-max insertions that lock L(tailLeaf).
            StampedLock prevLock = (prevLeaf != null) ? getRwLock(prevLeaf.key) : null;
            StampedLock xLock   = getRwLock(x);
            StampedLock succLock = (nextLeaf != null) ? getRwLock(nextLeaf.key) : null;

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
            long stamp = this.levelLock.writeLock();
            int lfl = lowestFullLevel;
            if (this.level[lfl].size() < (1L << lfl)) retreatLFL();
            this.levelLock.unlockWrite(stamp);
        }
        return deleted;
    }

    // Caller must hold levelLock.readLock() and LFL write locks for prev, x, and next.
    public boolean deleteNoLock(long x) {
        XFastTrie.Node leaf = (XFastTrie.Node) this.level[this.bits].remove(x);
        if (leaf == null) return false;

        size.decrementAndGet();

        XFastTrie.Node prevLeaf = leaf.prev;
        XFastTrie.Node nextLeaf = leaf.next;

        // Unlink from doubly linked list
        if (prevLeaf != null)
            prevLeaf.next = nextLeaf;
        else
            this.heafLeaf = nextLeaf;

        if (nextLeaf != null)
            nextLeaf.prev = prevLeaf;
        else
            this.tailLeaf = prevLeaf;

        // Walk up the trie: remove or update ancestor nodes.
        // Break early once a node's min/max are unchanged — ancestors are unaffected.
        for (int depth = this.bits - 1; depth >= 0; depth--) {
            long prefix = x >>> (bits - depth);

            XFastTrie.InternalNode leftChild  = this.level[depth + 1].get(prefix << 1);
            XFastTrie.InternalNode rightChild = this.level[depth + 1].get((prefix << 1) | 1);

            if (leftChild == null && rightChild == null) {
                if (depth == 0) {
                    // root stays — always at key 0, just clear its pointers
                    XFastTrie.InternalNode root = this.level[0].get(0L);
                    root.minLeaf = null;
                    root.maxLeaf = null;
                } else {
                    this.level[depth].remove(prefix);
                }
            } else {
                // only look up the parent when we actually need to update it
                XFastTrie.InternalNode node = this.level[depth].get(prefix);
                XFastTrie.Node newMin = (leftChild != null) ? leftChild.minLeaf : rightChild.minLeaf;
                XFastTrie.Node newMax = (rightChild != null) ? rightChild.maxLeaf : leftChild.maxLeaf;
                // Early break
                if (node.minLeaf == newMin && node.maxLeaf == newMax) break;
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
        long levelStamp = levelLock.readLock();
        boolean inserted;
        try {
            XFastTrie.Node succNode = successorNodeNoLock(x);
            // predNode used only to determine predLock — NOT passed into insertNoLock.
            // When succNode==null (new max), predNode=tailLeaf; locking L(tailLeaf) serializes
            // concurrent new-max insertions that would otherwise race on tailLeaf and tail.next.
            XFastTrie.Node predNode = (succNode != null) ? succNode.prev : this.tailLeaf;

            // 3-lock in key order: pred.key < x < succ.key.
            StampedLock predLock = (predNode != null) ? getRwLock(predNode.key) : null;
            StampedLock xLock   = getRwLock(x);
            StampedLock succLock = (succNode != null) ? getRwLock(succNode.key) : null;

            long predStamp = (predLock != null && predLock != xLock) ? predLock.writeLock() : -1;
            long xStamp    = xLock.writeLock();
            long succStamp = (succLock != null && succLock != xLock && succLock != predLock) ? succLock.writeLock() : -1;
            try {
                inserted = insertNoLock(x, list, listSize);
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
            long stamp = this.levelLock.writeLock();
            if (this.level[this.lowestFullLevel + 1].size() == (1L << (this.lowestFullLevel + 1))) advanceLFL();
            this.levelLock.unlockWrite(stamp);
        }
        return inserted;
    }

    // Caller must hold levelLock.readLock() and LFL write locks for predecessor, x, and successor.
    public boolean insertNoLock(long x, long[] list, int listSize) {
        if (queryNoLock(x)) return false;

        XFastTrie.Node leaf = new XFastTrie.Node(x, list, listSize);

        XFastTrie.Node successorNode = successorNodeNoLock(x);
        XFastTrie.Node predecessorNode = (successorNode != null) ? successorNode.prev : this.tailLeaf;

        linkBetweenNoLock(leaf, predecessorNode, successorNode);
        this.level[this.bits].put(x, leaf);

        // Walk bottom-up (bits-1 → 0): create or update ancestor nodes.
        // Breaking early if node exists and min max doesn't change
        for (int depth = this.bits - 1; depth >= 0; depth--) {
            long prefix = x >>> (bits - depth);
            XFastTrie.InternalNode node = this.level[depth].get(prefix);
            if (node == null) {
                this.level[depth].put(prefix, new XFastTrie.InternalNode(leaf, leaf));
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
        size.incrementAndGet();
        return true;
    }

    // --------------------
    // LFL TRANSITIONS
    // --------------------

    // Advance LFL by one if the next level is fully populated.
    // Caller should pre-check the condition before calling to avoid unnecessary lock contention.
    // Immediately acquires the global write lock — no preliminary check outside the lock.
    public void advanceLFL() {
        StampedLock[] newLocks = new StampedLock[(int) 1L << (this.lowestFullLevel + 1)];
        for (int i = 0; i < newLocks.length; i++) {
            newLocks[i] = new StampedLock();
        }
        this.locks = newLocks;
        this.lowestFullLevel += 1;
    }

    // Retreat LFL by one if the current level is no longer fully populated.
    // Caller should pre-check the condition before calling to avoid unnecessary lock contention.
    // Immediately acquires the global write lock — no preliminary check outside the lock.
    public void retreatLFL() {
        StampedLock[] newLocks = new StampedLock[(int) 1L << (this.lowestFullLevel - 1)];
        for (int i = 0; i < newLocks.length; i++) {
            newLocks[i] = new StampedLock();
        }
        this.locks = newLocks;
        this.lowestFullLevel -= 1;
    }
}
