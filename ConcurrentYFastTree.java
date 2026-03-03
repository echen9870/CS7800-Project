import java.util.Arrays;

public class ConcurrentYFastTree {

    public int bits = 0;

    public XFastTree xfast;

    public ConcurrentYFastTree(int b) {
        this.bits = b;
        this.xfast = new XFastTree(b);
    }

    public boolean query(long x) {
        // We have to do a loop here because the buckets might actually be modified by
        // another thread when we are trying to query it
        while (true) {
            // Locate owning bucket via xfast
            // Optimistic check
            long lock = xfast.rw.tryOptimisticRead();
            Long rep = null;
            XFastTree.Node node = null;
            try {
                rep = xfast.predecessorNoLock(x);
                node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
            } catch (NullPointerException e) {
                lock = 0; // force pessimistic fallback, this happens as we change the leaf pointers
            }

            // Pessimistic check
            if (!xfast.rw.validate(lock)) {
                lock = xfast.rw.readLock();
                try {
                    rep = xfast.predecessorNoLock(x);
                    node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
                } finally {
                    xfast.rw.unlockRead(lock);
                }
            }

            // If node doesn't exist
            if (node == null) 
                return false;

            // Read the bucket and node.next
            // node.next is only modified while holding the bucket writeLock
            // so the bucket stamp covers it
            // Optimistic check
            lock = node.bucketRw.tryOptimisticRead();
            long[] nums = node.nums;
            int numsSize = node.numsSize;
            XFastTree.Node freshNextNode = node.next;

            // Pessimistic check
            if (!node.bucketRw.validate(lock)) {
                lock = node.bucketRw.readLock();
                try {
                    nums = node.nums;
                    numsSize = node.numsSize;
                    freshNextNode = node.next;
                } finally {
                    node.bucketRw.unlockRead(lock);
                }
            }

            // Check insertion point, return early if exists
            int pos = Arrays.binarySearch(nums, 0, numsSize, x);
            if (pos >= 0) {
                return true;
            }

            int ins = -pos - 1;
            // Staleness check
            if (ins >= numsSize && freshNextNode != null && x >= freshNextNode.key) {
                continue;
            }

            return false;
        }
    }

    // smallest key >= x, or null if none
    public Long successor(long x) {
        // We have to do a loop here because the buckets might actually be modified by
        // another thread when we are trying to query it
        while (true) {
            // Locate owning bucket via xfast (optimistic first, then pessimistic)
            long lock = xfast.rw.tryOptimisticRead();
            Long rep = null;
            XFastTree.Node node = null;
            try {
                rep = xfast.predecessorNoLock(x);
                node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
            } catch (NullPointerException e) {
                lock = 0; // force pessimistic fallback
            }

            if (!xfast.rw.validate(lock)) {
                lock = xfast.rw.readLock();
                try {
                    rep = xfast.predecessorNoLock(x);
                    node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
                } finally {
                    xfast.rw.unlockRead(lock);
                }
            }

            // x is smaller than every existing key
            if (node == null) {
                return xfast.headLeaf == null ? null : xfast.headLeaf.key;
            }

            // Read the bucket and read node.next
            lock = node.bucketRw.tryOptimisticRead();
            long[] nums = node.nums;
            int numsSize = node.numsSize;
            XFastTree.Node freshNextNode = node.next;

            if (!node.bucketRw.validate(lock)) {
                lock = node.bucketRw.readLock();
                try {
                    nums = node.nums;
                    numsSize = node.numsSize;
                    freshNextNode = node.next;
                } finally {
                    node.bucketRw.unlockRead(lock);
                }
            }

            // Binary search within this bucket
            long last = nums[numsSize - 1];
            if (x <= last) {
                int idx = Arrays.binarySearch(nums, 0, numsSize, x);
                if (idx >= 0) {
                    return nums[idx];
                }
                idx = -idx - 1;
                return (idx < numsSize) ? nums[idx] : null;
            }

            // x is past every element in this bucket; successor is the first key of the
            // next bucket.
            if (freshNextNode == null) {
                return null;
            }

            // key is immutable once set and equals nums[0]
            if (freshNextNode.key >= x) {
                return freshNextNode.key;
            }
        }
    }

    public void insert(long x) {
        int maxSize = 128 * bits;
        // We have to do a loop here because the buckets might actually be modified by
        // another thread when we are trying to modify it
        while (true) {
            // locate owning bucket — optimistic first, then pessimistic
            long lock = xfast.rw.tryOptimisticRead();
            Long rep = null;
            XFastTree.Node node = null;
            try {
                rep = xfast.predecessorNoLock(x);
                node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
            } catch (NullPointerException e) {
                lock = 0; // force pessimistic fallback
            }

            if (!xfast.rw.validate(lock)) {
                lock = xfast.rw.readLock();
                try {
                    rep = xfast.predecessorNoLock(x);
                    node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
                } finally {
                    xfast.rw.unlockRead(lock);
                }
            }

            // x is smaller than every existing key — create a new head bucket
            if (rep == null) {
                lock = xfast.rw.writeLock();
                try {
                    if (xfast.predecessorNoLock(x) != null) {
                        continue;
                    }
                    long[] nums = new long[maxSize];
                    nums[0] = x;
                    xfast.insertNoLock(x, nums, 1);
                    return;
                } finally {
                    xfast.rw.unlockWrite(lock);
                }
            }

            // insert into the bucket
            lock = node.bucketRw.writeLock();
            try {
                // stale check
                Long currentNextRep = (node.next != null) ? node.next.key : null;
                if (currentNextRep != null && x >= currentNextRep) {
                    continue;
                }

                long[] nums = node.nums;
                int numsSize = node.numsSize;
                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos >= 0) {
                    return;
                }
                pos = -pos - 1;

                System.arraycopy(nums, pos, nums, pos + 1, numsSize - pos);
                nums[pos] = x;
                node.numsSize = numsSize + 1;

                // split the bucket if full
                if (node.numsSize == maxSize) {
                    long xLock = xfast.rw.writeLock();
                    try {
                        splitListLocked(node, maxSize);
                    } finally {
                        xfast.rw.unlockWrite(xLock);
                    }
                }
                return;
            } finally {
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    // largest key <= x, or null if none
    public Long predecessor(long x) {
        while (true) {
            long lock = xfast.rw.tryOptimisticRead();
            Long rep = null;
            XFastTree.Node node = null;
            try {
                rep = xfast.predecessorNoLock(x);
                node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
            } catch (NullPointerException e) {
                lock = 0;
            }

            if (!xfast.rw.validate(lock)) {
                lock = xfast.rw.readLock();
                try {
                    rep = xfast.predecessorNoLock(x);
                    node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
                } finally {
                    xfast.rw.unlockRead(lock);
                }
            }

            if (node == null) {
                return null;
            }

            lock = node.bucketRw.tryOptimisticRead();
            long[] nums = node.nums;
            int numsSize = node.numsSize;

            if (!node.bucketRw.validate(lock)) {
                lock = node.bucketRw.readLock();
                try {
                    nums = node.nums;
                    numsSize = node.numsSize;
                } finally {
                    node.bucketRw.unlockRead(lock);
                }
            }

            int pos = Arrays.binarySearch(nums, 0, numsSize, x);
            if (pos >= 0) {
                return nums[pos];
            }
            pos = -pos - 1; // insertion point
            if (pos > 0) {
                return nums[pos - 1];
            }
        }
    }

    public boolean delete(long x) {
        int maxSize = 128 * bits;
        while (true) {
            // Locate owning bucket optimistic first, then pessimistic
            long lock = xfast.rw.tryOptimisticRead();
            Long rep = null;
            XFastTree.Node node = null;
            try {
                rep = xfast.predecessorNoLock(x);
                node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
            } catch (NullPointerException e) {
                lock = 0;
            }

            if (!xfast.rw.validate(lock)) {
                lock = xfast.rw.readLock();
                try {
                    rep = xfast.predecessorNoLock(x);
                    node = (rep != null) ? xfast.queryNodeNoLock(rep) : null;
                } finally {
                    xfast.rw.unlockRead(lock);
                }
            }

            if (node == null) {
                return false;
            }

            // Lock the bucket
            lock = node.bucketRw.writeLock();
            try {
                // Stale check: if x belongs to a later bucket, retry
                Long currentNextRep = (node.next != null) ? node.next.key : null;
                if (currentNextRep != null && x >= currentNextRep) {
                    continue;
                }

                long[] nums = node.nums;
                int numsSize = node.numsSize;

                int pos = Arrays.binarySearch(nums, 0, numsSize, x);
                if (pos < 0) {
                    return false; // x not in this bucket
                }
                // Remove x from nums array
                System.arraycopy(nums, pos + 1, nums, pos, numsSize - pos - 1);
                node.numsSize = numsSize - 1;
                int newSize = node.numsSize;

                // Non-rep deletion: bucket representative unchanged
                if (x != rep) {
                    return true;
                }

                // x was the bucket rep — update XFast.
                // Acquire XFast write lock after bucket lock (same order as insert) to avoid
                // deadlock.
                long xLock = xfast.rw.writeLock();
                try {
                    xfast.deleteNoLock(x);
                    if (newSize > 0) {
                        long newRep = nums[0];
                        long[] newNums = new long[maxSize];
                        System.arraycopy(nums, 0, newNums, 0, newSize);
                        xfast.insertNoLock(newRep, newNums, newSize);
                    }
                } finally {
                    xfast.rw.unlockWrite(xLock);
                }
                return true;
            } finally {
                node.bucketRw.unlockWrite(lock);
            }
        }
    }

    private void splitListLocked(XFastTree.Node node, int bucketCapacity) {
        long[] nums = node.nums;
        int numsSize = node.numsSize;

        // split in half
        int half = numsSize / 2;

        int newNumsSize = numsSize - half;
        long[] newNums = new long[bucketCapacity];
        System.arraycopy(nums, half, newNums, 0, newNumsSize);

        node.numsSize = half;

        // Clear the right half of the old array
        Arrays.fill(nums, half, numsSize, 0L);

        // register new bucket + rep (caller already holds xfast writeLock)
        xfast.insertNoLock(newNums[0], newNums, newNumsSize);
    }
}