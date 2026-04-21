# CS7800 — Concurrent Y-Fast Trie

A Y-Fast Trie implementation for 64-bit integer keys with two concurrency
strategies, benchmarked against `java.util.concurrent.ConcurrentSkipListSet`.

## What's in here

- **Y-Fast V1** — single global `StampedLock` on the X-Fast trie. One
  writer, many optimistic readers. Simple; bottlenecks on writes.
- **Y-Fast V2** — partition locks at the X-Fast trie's *Lowest Full
  Level* (LFL). `2^LFL` locks total, growing with load. Writes to disjoint
  partitions run in parallel.
- **X-Fast Trie** — the internal index layer. Two implementations: a
  single-lock version (`XFastTrie.java`) for V1, and a fine-grained
  partitioned version (`ConcurrentXFastTrie.java`) for V2.

All three support the standard ordered-set API: `insert`, `query`,
`successor`, `predecessor`, `delete`.

## Layout

```
xFast/
  XFastTrie.java             single-lock; backs V1
  ConcurrentXFastTrie.java   LFL-partitioned; backs V2
  Node.java, InternalNode.java
  XFastChecker.java, TestXFastQueryConcurrencyCorrectness.java
yFast/
  ConcurrentYFastTrieV1.java global-lock Y-Fast
  ConcurrentYFastTrieV2.java partition-lock Y-Fast
  PrimitiveArray.java        sorted long[] for buckets
  YFastChecker.java, YFastCheckerV2.java
  TestYFastQueryConcurrencyCorrectness.java
benchmark/
  BenchmarkFramework.java    thread harness + timing
  BenchmarkSuite.java        nine benchmark configurations
  RunResult.java             ns/op + Mops/s formatter
charts/
  test1/, test1_16M/         V1 vs V2 thread sweeps (N=2^23, 2^24)
  test2/                     V1 vs V2 vs SkipList thread sweep
  test_ops_sweep_benchmark/  V2 vs SkipList, N sweep at 64 threads
  test_thread_sweep/,
  test_thread_sweep_128M/    V2 vs SkipList thread sweeps at fixed N
  test_bucket_sweep/         bucket-size tradeoff sweep
Main.java                    entry point
run.sh                       compile + run helper
```

## Build and run

```
./run.sh compile           # javac into out/
./run.sh check-xfast       # X-Fast correctness suite
./run.sh check-yfast       # Y-Fast correctness suite

./run.sh test1 <bits> <ops>   # V1 vs V2, 1..64 threads
./run.sh test2 <bits> <ops>   # V1 vs V2 vs SkipList, 1..64 threads
./run.sh test3 <bits> <ops>   # V2 bounded vs unbounded LFL, 1..64 threads
./run.sh test4 [bits]         # V2 unbounded LFL, N sweep 2^20..2^29, 64 threads
./run.sh test5 [bits]         # SkipList N sweep, 64 threads
./run.sh test6 [bits]         # V2 bounded LFL N sweep, 64 threads
./run.sh test7 [bits]         # V2 (unbounded) vs SkipList N sweep, 64 threads
./run.sh test8 <bits> <ops>   # V2 (unbounded) vs SkipList thread sweep at fixed N
./run.sh test9 <bits> <ops>   # V2 bucket-size sweep, 16 threads
```

Typical report runs:
```
./run.sh test2 63 8388608    # bits=63, N=2^23  (main thread-scaling figure)
./run.sh test7 63            # N sweep 2^21..2^27
./run.sh test8 63 16777216   # thread sweep at N=2^24
./run.sh test9 63 16777216   # bucket-size tradeoff
```

## Concurrency design notes

### Optimistic reads
Every read path (`query`, `successor`, `predecessor`, `locateBucket`) uses
`StampedLock.tryOptimisticRead()`. Readers acquire no lock — they get a
stamp, do their work, and call `validate(stamp)` at the end. If a writer
appeared during the read window, validate fails and the read retries.
This keeps readers off the lock's cache line entirely in the common case.

### Lowest Full Level (V2)
In `ConcurrentXFastTrie`, the LFL is the deepest level `d` where all
`2^d` internal nodes exist. Partition locks live there: `locks.length
= 2^LFL`, and a key `x` maps to `locks[x >>> (bits - LFL)]`. As inserts
fill the next level, `advanceLFL()` doubles the lock array; deletes can
trigger `retreatLFL()`. `maxLFL` caps growth at roughly `log2(3 * threads)`
by default to keep the lock array bounded.

### Lock ordering (V2 writes)
Always acquired in this order to stay deadlock-free:
1. `bucketRw` — per-bucket `StampedLock`
2. Partition locks at the LFL — in ascending key order (pred → x → succ)
3. `levelLock.readLock` — held by every writer; only taken as
   `writeLock` during LFL transitions

## Known caveats
- `ConcurrentXFastTrie` is slower than the single-lock `XFastTrie` at 1
  thread due to `ConcurrentHashMap` overhead. The break-even is around
  4 threads.
- Bucket size defaults to `32 * bits`. Swept in test9; the constructor
  `ConcurrentYFastTrieV2(bits, xfast, bucketSize)` accepts a custom value.

## Benchmark output format
Each run prints lines like:
```
YFastV2_t16_perOp_insert_16t      101.37 ns/op    9.86 Mops/s  (0.850s)
```
Name encodes `<structure>_t<threads>_<kind>_<op>_<threads>{t,thread}`;
`random_Nthread` rows are a 1/3/1/3/1/3 mix of insert/query/successor
(preceded by a fill phase of N/4 inserts). Per-op rows are measured in
isolation, insert-first on a fresh structure.

## Charts
Raw logs live under `charts/testN/raw.log`. Run `python plot.py` in each
folder to regenerate PNGs from the log.

## Contributors
Alex Stevenson, Jenish Kothari, Eric Chen.
