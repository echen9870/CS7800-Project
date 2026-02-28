-- TODOs

- Concurrency
    - We have to retest this, been a while
        - 1 -> 8 threads leads to approx a 50% reduction in total times for successor (everything else sucks though)
    - Implement more sophisticated locking state, have an idea, but still needs to be worked out
- Implementation
    - Implement RW locks for each universe in the Y-Fast trie
        - Should we use optimistic locks for reads? Yes, much faster
    - Optimise Y-Fast tries (How big should each sub universe be, what data structure we should use)
        - ArrayList is bad, too much bloat, use primitive list instead
            - This might not even be good later on bc our subuniverse should run concurrently too, so probably move to skip tries
        - BSTs, Concurrent SkipTries? 
            - ConcurrentSkipTries suck for some reason
        - Size of Sub U
            - Still some improvements at 128 * bits, which is strange, but 128 to 16 is honestly negligible
            - 16 * bits == 12 * bits> 4 * bits in terms of performance

    - Optimize memory
        - We are able to do 28 bits now
        - Maybe store more keys per level for X-Fast (Instead of 0, 1 -> 00, 01, 10, 11)? = Less levels to traverse -> Fast pred queries -> Faster ops?
    
- Testing purposes
    - Build a program that actually test memory usage (Java gc messes with things so have to look into this)
