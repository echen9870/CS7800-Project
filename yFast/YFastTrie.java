package yFast;

import java.util.*;
import xFast.Node;
import xFast.XFastTrie;

public class YFastTrie {
    public int bits = 0;

    public XFastTrie xfast;

    public YFastTrie(int b) {
        this.bits = b;
        this.xfast = new XFastTrie(b);
    }

    public boolean query(long x) {
        Long rep = xfast.predecessor(x);
        if (rep == null)
            return false;
        Node node = xfast.queryNode(rep);
        PrimitiveArray bucket = node.bucket;

        int pos = Arrays.binarySearch(bucket.data, 0, bucket.size, x);

        return (pos >= 0);
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        Long rep = xfast.predecessor(x);
        Node node = (rep == null) ? xfast.headLeaf : xfast.queryNode(rep);

        PrimitiveArray bucket = node.bucket;
        long last = bucket.data[bucket.size - 1];

        if (x <= last) {
            int idx = Arrays.binarySearch(bucket.data, 0, bucket.size, x);
            if (idx >= 0)
                return bucket.data[idx];
            idx = -idx - 1;
            return (idx < bucket.size) ? bucket.data[idx] : null;
        }

        // If our last element is less than x, we have to go to next node (check null)
        return (node.next == null) ? null : node.next.bucket.data[0];
    }

    public void insert(long x) {
        // max bucket size
        int maxSize = 32 * bits;

        // find bucket rep
        Long rep = xfast.predecessor(x);

        // x is smaller than smallest rep
        if (rep == null) {
            PrimitiveArray bucket = new PrimitiveArray(new long[maxSize], 1);
            bucket.data[0] = x;
            xfast.insert(x, bucket);
            return;
        }

        // Get representative
        Node node = xfast.queryNode(rep);

        if (node.bucket.sortedInsert(x) < 0) return; // duplicate

        // split if too big
        if (node.bucket.size == maxSize) {
            splitList(rep);
        }
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        Long rep = xfast.predecessor(x);

        // No predecessor exists
        if (rep == null)
            return null;

        // Find predecessor bucket
        Node node = xfast.queryNode(rep);
        PrimitiveArray bucket = node.bucket;

        // Return position
        int pos = Arrays.binarySearch(bucket.data, 0, bucket.size, x);
        if (pos >= 0)
            return bucket.data[pos];
        pos = -pos - 1;
        return (pos > 0) ? bucket.data[pos - 1] : null;
    }

    public boolean delete(long x) {
        Long rep = xfast.predecessor(x);
        if (rep == null)
            return false;

        Node node = xfast.queryNode(rep);
        PrimitiveArray bucket = node.bucket;

        int pos = Arrays.binarySearch(bucket.data, 0, bucket.size, x);

        // x not in tree
        if (pos < 0)
            return false;

        // Remove x from bucket
        System.arraycopy(bucket.data, pos + 1, bucket.data, pos, bucket.size - pos - 1);
        bucket.size--;

        // x was the bucket representative — update XFast
        if (x == rep) {
            xfast.delete(x);
            if (bucket.size > 0) {
                xfast.insert(bucket.data[0], bucket);
            }
        }
        return true;
    }

    public void splitList(long rep) {
        Node node = xfast.queryNode(rep);
        PrimitiveArray newBucket = node.bucket.split(16 * bits);
        xfast.insert(newBucket.data[0], newBucket);
    }
}
