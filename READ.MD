-- TODOs

- Concurrency
    - 1 -> 8 threads leads to approx a 50% reduction in total times for successor (everything else sucks though)
    
- Implementation
    - Implement RW locks for each universe in the Y-Fast trie
        - Should we use optimistic locks for reads? 
        - How to handle subuniverse splitting?
    - Optimise Y-Fast tries (How big should each sub universe be, what data structure we should use)
        - ArrayList is bad, too much bloat, use primitive list instead
            - This might not even be good later on bc our subuniverse should run concurrently too, so probably move to skip tries
        - BSTs, Concurrent SkipTries?
        - Size of Sub U
            - 16 * bits == 12 * bits> 4 * bits in terms of performance
    - Optimize memory
        - Figure this part out idk, 2 ** 26 can't run well (I gave 12 GBs of memory to start with -Xms12g flag, not enough)
        - Maybe store more keys per level for X-Fast (Instead of 0, 1 -> 00, 01, 10, 11)? = Less levels to traverse -> Fast pred queries -> Faster ops?
    
- Testing purposes
    - Create/Modify existing framework to generate and test a variety of queries at once (instead of inserting, then querying, then successor)
    - Pass flags so we only run a single data structure per execution of program (I think garbage collections messes with our insertion times, not entirely sure)(Also need to pass a seed so we generate the same randomness despite different runs)
