package xFast;

import java.util.*;

public class XFastChecker {

    @SuppressWarnings("unchecked")
    public static void check(XFastTrie trie) {
        Map<Long, InternalNode>[] levels = (Map<Long, InternalNode>[]) (Map[]) trie.level;
        checkInternal(trie.bits, levels, trie.headLeaf, trie.tailLeaf);
    }

    @SuppressWarnings("unchecked")
    public static void check(ConcurrentXFastTrie trie) {
        Map<Long, InternalNode>[] levels = (Map<Long, InternalNode>[]) (Map[]) trie.level;
        checkInternal(trie.bits, levels, trie.headLeaf, trie.tailLeaf);
    }

    private static void checkInternal(int bits,
                                      Map<Long, InternalNode>[] level,
                                      Node headLeaf,
                                      Node tailLeaf) {

        // --- collect leaves from linked list ---
        List<Node> chain = new ArrayList<>();
        {
            Node cur = headLeaf;
            while (cur != null) {
                chain.add(cur);
                cur = cur.next;
            }
        }

        // --- linked list integrity ---
        if (!chain.isEmpty()) {
            assert headLeaf.prev == null
                    : "headLeaf.prev != null (key=" + headLeaf.key + ")";
            assert tailLeaf != null
                    : "tailLeaf is null but headLeaf exists";
            assert chain.get(chain.size() - 1) == tailLeaf
                    : "tailLeaf mismatch: walk ended at key=" + chain.get(chain.size() - 1).key
                    + " but tailLeaf.key=" + (tailLeaf == null ? "null" : tailLeaf.key);
            assert tailLeaf.next == null
                    : "tailLeaf.next != null (key=" + tailLeaf.key + ")";

            for (int i = 0; i < chain.size(); i++) {
                Node n = chain.get(i);
                if (i > 0) {
                    assert chain.get(i - 1).key < n.key
                            : "linked list not increasing at " + i
                            + ": " + chain.get(i - 1).key + " -> " + n.key;
                    assert n.prev == chain.get(i - 1)
                            : "prev pointer broken at key=" + n.key
                            + ": expected=" + chain.get(i - 1).key
                            + " got=" + (n.prev == null ? "null" : n.prev.key);
                }
            }
        } else {
            assert headLeaf == null : "headLeaf should be null for empty trie";
            assert tailLeaf == null : "tailLeaf should be null for empty trie";
        }

        // --- leaf level matches linked list ---
        Map<Long, InternalNode> leafLevel = level[bits];
        assert leafLevel.size() == chain.size()
                : "leaf level size=" + leafLevel.size() + " but linked list size=" + chain.size();

        for (Node n : chain) {
            InternalNode inMap = leafLevel.get(n.key);
            assert inMap == n
                    : "leaf level entry for key=" + n.key + " is wrong object (or missing)";
        }

        // --- check prefix sets and min/max per (depth, prefix) ---
        for (int depth = 0; depth < bits; depth++) {
            Map<Long, Node> expectedMin = new LinkedHashMap<>();
            Map<Long, Node> expectedMax = new LinkedHashMap<>();

            for (Node leaf : chain) {
                long prefix = leaf.key >>> (bits - depth);
                if (!expectedMin.containsKey(prefix))
                    expectedMin.put(prefix, leaf);
                expectedMax.put(prefix, leaf);
            }

            // root (depth=0, prefix=0) always exists
            if (depth == 0 && chain.isEmpty()) {
                InternalNode root = level[0].get(0L);
                assert root != null : "root (level[0], prefix=0) missing";
                assert root.minLeaf == null : "empty trie root.minLeaf should be null";
                assert root.maxLeaf == null : "empty trie root.maxLeaf should be null";
                continue;
            }

            for (Map.Entry<Long, Node> e : expectedMin.entrySet()) {
                long prefix = e.getKey();
                Node expMin = e.getValue();
                Node expMax = expectedMax.get(prefix);

                InternalNode node = level[depth].get(prefix);
                assert node != null
                        : "missing node at depth=" + depth + " prefix=" + prefix;

                assert node.minLeaf == expMin
                        : "minLeaf wrong at depth=" + depth + " prefix=" + prefix
                        + ": expected=" + expMin.key
                        + " got=" + (node.minLeaf == null ? "null" : node.minLeaf.key);

                assert node.maxLeaf == expMax
                        : "maxLeaf wrong at depth=" + depth + " prefix=" + prefix
                        + ": expected=" + expMax.key
                        + " got=" + (node.maxLeaf == null ? "null" : node.maxLeaf.key);
            }

            // no extra nodes at this level
            int expectedSize = expectedMin.size();
            int actualSize = level[depth].size();
            assert actualSize == expectedSize
                    : "level[" + depth + "] has " + actualSize
                    + " nodes but expected " + expectedSize;
        }
    }
}
