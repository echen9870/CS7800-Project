package yFast;

import java.util.*;
import xFast.Node;
import xFast.XFastChecker;
import xFast.ConcurrentXFastTrie;

public class YFastCheckerV2 {

    public static void check(ConcurrentYFastTrieV2 trie) {
        ConcurrentXFastTrie xfast = trie.xfast;

        XFastChecker.check(xfast);
        checkLFL(xfast);

        Node cur = xfast.headLeaf;
        Node prev = null;
        Set<Long> seen = new HashSet<>();

        while (cur != null) {
            long key = cur.key;
            long[] data = cur.bucket.data;
            int size = cur.bucket.size;

            assert size > 0
                    : "node key=" + key + " has size=0";

            assert data[0] == key
                    : "node key=" + key + " but data[0]=" + data[0];

            for (int i = 1; i < size; i++) {
                assert data[i] > data[i - 1]
                        : "node key=" + key + " not increasing at " + i
                        + ": " + data[i - 1] + " -> " + data[i];
            }

            for (int i = 0; i < size; i++) {
                assert seen.add(data[i])
                        : "duplicate element " + data[i] + " in node key=" + key;
            }

            for (int i = 0; i < size; i++) {
                assert data[i] >= key
                        : "node key=" + key + " has element " + data[i] + " < key";
            }

            Node next = cur.next;
            if (next != null) {
                assert data[size - 1] < next.key
                        : "node key=" + key + " last element " + data[size - 1]
                        + " >= next key=" + next.key;
            }

            assert cur.prev == prev
                    : "prev pointer broken at key=" + key;

            prev = cur;
            cur = next;
        }
    }

    private static void checkLFL(ConcurrentXFastTrie xfast) {
        int lfl = xfast.lowestFullLevel;
        int maxLFL = xfast.maxLFL;

        assert lfl >= 0
                : "lfl=" + lfl + " is negative";
        assert lfl <= maxLFL
                : "lfl=" + lfl + " but maxLFL=" + maxLFL;

        int expectedLocks = 1 << lfl;
        assert xfast.locks.length == expectedLocks
                : "locks.length=" + xfast.locks.length + " but expected 2^lfl=" + expectedLocks
                + " (lfl=" + lfl + ")";
    }
}
