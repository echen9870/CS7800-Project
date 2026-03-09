package yFast;

import java.util.Arrays;

public class PrimitiveArray {
    public long[] data;
    public volatile int size;

    public PrimitiveArray(long[] data, int size) {
        this.data = data;
        this.size = size;
    }

    // Insert x in sorted order. Returns insertion index (>= 0), or -1 if x already exists.
    public int sortedInsert(long x) {
        int pos = Arrays.binarySearch(data, 0, size, x);
        if (pos >= 0) return -1;
        pos = -pos - 1;
        System.arraycopy(data, pos, data, pos + 1, size - pos);
        data[pos] = x;
        size++;
        return pos;
    }

    // Delete
    public boolean delete(long x) {
        int pos = Arrays.binarySearch(this.data, 0, this.size, x);
        if (pos < 0) return false;

        System.arraycopy(this.data, pos + 1, this.data, pos, this.size - pos - 1);
        this.size--;
        return true;
    }

    // Split keeps first half in this array, returns new PrimitiveArray with second half.
    public PrimitiveArray split(int capacity) {
        int half = size / 2;
        int newSize = size - half;
        PrimitiveArray right = new PrimitiveArray(new long[capacity], newSize);
        System.arraycopy(data, half, right.data, 0, newSize);
        size = half;
        return right;
    }

    // Predecessor largest element <= x, or -1 if none
    public long predecessor(long x) {
        int pos = Arrays.binarySearch(this.data, 0, this.size, x);
        int ip = pos >= 0 ? pos : -pos - 1;
        return pos >= 0 ? this.data[pos] : (ip > 0 ? this.data[ip - 1] : -1);
    }

    // Successor smallest element >= x, or -1 if none
    public long successor(long x) {
        int idx = Arrays.binarySearch(this.data, 0, this.size, x);
        return idx >= 0 ? this.data[idx] : ((-idx - 1) < this.size ? this.data[-idx - 1] : -1);
    }
}
