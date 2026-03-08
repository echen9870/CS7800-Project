public interface YFastTrieInterface {
    Long predecessor(long x);
    Long successor(long x);
    boolean query(long x);
    boolean insert(long x, long[] list, int listSize);
    boolean delete(long x);
}