package xFast;

import java.util.concurrent.locks.StampedLock;

import yFast.PrimitiveArray;

// Leaf nodes carry bucket data and linked-list pointers
public class Node extends InternalNode {

    // Doubly linked list for the leaf nodes
    public volatile Node prev;
    public volatile Node next;

    // The sub universe for the leaf node X-Fast trie in a Y-Fast Trie
    public PrimitiveArray bucket;

    // Val
    public long key;

    // Concurrency control
    public StampedLock bucketRw;

    public Node(long key, PrimitiveArray bucket) {
        super(null, null);
        this.key = key;
        this.minLeaf = this;
        this.maxLeaf = this;
        this.bucket = bucket;
        this.bucketRw = new StampedLock();
    }
}
