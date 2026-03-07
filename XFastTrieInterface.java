public interface XFastTrieInterface {
    XFastTrie.Node getHeadLeaf();
    // Thread-safe predecessor lookup — each implementation uses its own locking.
    XFastTrie.Node predecessorNode(long x);
    boolean insert(long x, long[] list, int listSize);
    boolean delete(long x);
}
