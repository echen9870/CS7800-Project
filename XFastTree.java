import java.util.HashMap;

public class XFastTree {
    public static class Node {
        public Node prev;
        public Node next;

        public Node aux; // kept for compatibility, not required
        public int key;

        public Node minLeaf;
        public Node maxLeaf;

        public boolean isLeaf;

        public Node() {
            this.prev = null;
            this.next = null;
            this.aux = null;
            this.key = 0;
            this.minLeaf = null;
            this.maxLeaf = null;
            this.isLeaf = false;
        }
    }

    public int bits;
    public HashMap<Integer, Node>[] level;
    public int[] shiftAtDepth;

    public Node root;
    public Node headLeaf;
    public Node tailLeaf;

    public int size;

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

    public boolean query(int x) {
        return this.level[this.bits].containsKey(x);
    }

    public Node queryNode(int x) {
        return this.level[this.bits].get(x);
    }

    public int longestPrefixLen(int x) {
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
        if (this.headLeaf == null) {
            return null;
        }

        if (x <= this.headLeaf.key) {
            return this.headLeaf.key;
        }
        if (x > this.tailLeaf.key) {
            return null;
        }

        Node existingLeaf = queryNode(x);
        if (existingLeaf != null) {
            return x;
        }

        int prefixLen = longestPrefixLen(x);
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
        if (this.headLeaf == null) {
            return null;
        }

        if (x < this.headLeaf.key) {
            return null;
        }
        if (x >= this.tailLeaf.key) {
            return this.tailLeaf.key;
        }

        Node existingLeaf = queryNode(x);
        if (existingLeaf != null) {
            return x;
        }

        Integer successorKey = successor(x);
        if (successorKey == null) {
            return this.tailLeaf.key;
        }

        Node successorLeaf = queryNode(successorKey);
        if (successorLeaf == null || successorLeaf.prev == null) {
            return null;
        }

        return successorLeaf.prev.key;
    }

    public void linkBetween(Node leaf, Node predecessorNode, Node successorNode) {
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

    public boolean insert(int x) {
        if (query(x)) {
            return false;
        }

        Node leaf = new Node();
        leaf.isLeaf = true;
        leaf.key = x;
        leaf.minLeaf = leaf;
        leaf.maxLeaf = leaf;

        Integer successorKey = successor(x);
        Node successorNode = (successorKey != null) ? queryNode(successorKey) : null;
        Node predecessorNode = (successorNode != null) ? successorNode.prev : this.tailLeaf;

        linkBetween(leaf, predecessorNode, successorNode);
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
    }

    public static void main(String[] args) {
        XFastTree tree = new XFastTree(16);

        int[] values = new int[]{10, 3, 7, 20, 15, 100};
        for (int v : values) {
            tree.insert(v);
        }

        System.out.println(tree.query(7) + " " + tree.query(8));
        System.out.println("succ(8) = " + tree.successor(8));
        System.out.println("pred(8) = " + tree.predecessor(8));
    }
}