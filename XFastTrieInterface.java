import java.util.concurrent.locks.StampedLock;

public interface XFastTrieInterface {
    StampedLock getLock(long x);
    XFastTrie.Node getHeadLeaf();

    XFastTrie.Node predecessorNode(long x);
    XFastTrie.Node predecessorNodeNoLock(long x);
    Long predecessorNoLock(long x);

    boolean insert(long x, long[] list, int listSize);
    boolean insertNoLock(long x, long[] list, int listSize);

    boolean delete(long x);
    boolean deleteNoLock(long x);
}
