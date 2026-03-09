package xFast;

public class InternalNode {
    public volatile Node minLeaf;
    public volatile Node maxLeaf;

    public InternalNode(Node minLeaf, Node maxLeaf) {
        this.minLeaf = minLeaf;
        this.maxLeaf = maxLeaf;
    }
}
