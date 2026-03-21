#!/bin/bash

set -e

compile() {
    mkdir -p out
    javac -d out xFast/*.java yFast/*.java benchmark/*.java Main.java
}

case "$1" in
    compile)
        echo "Compiling all files to out/"
        compile
        echo "Done."
        ;;
    clean)
        echo "Removing out/"
        rm -rf out
        echo "Done."
        ;;
    check-xfast)
        compile
        echo "Running XFastChecker..."
        java -cp out xFast.TestXFastQueryConcurrencyCorrectness
        ;;
    check-yfast)
        compile
        echo "Running YFastChecker..."
        java -cp out yFast.TestYFastQueryConcurrencyCorrectness
        ;;
    test1)
        # Test 1: sweep universe/op counts {2^14, 2^16, 2^18}
        # BST, SkipList, XFast, ConcurrentXFast, YFastV1, YFastV2
        compile
        java -Xmx4g -cp out Main 1
        ;;
    test2)
        # Test 2: universe=2^32, BST vs YFastV1 vs YFastV2 (16 threads)
        compile
        java -Xmx4g -cp out Main 2
        ;;
    test3)
        # Test 3: large universe, bounded vs unbounded LFL YFastV2
        # Usage: ./run.sh test3 <bits>   (bits > 32, e.g. 40)
        BITS=${2:?'test3 requires a bits argument (e.g. ./run.sh test3 40)'}
        compile
        java -Xmx4g -cp out Main 3 "$BITS"
        ;;
    test4)
        # Test 4: bucket-size sweep (1x..128x bits), universe=2^32
        compile
        java -Xmx4g -cp out Main 4
        ;;
    test5)
        # Test 5: thread-count sweep (1..nCPU), universe=2^32
        compile
        java -Xmx4g -cp out Main 5
        ;;
    all)
        # Run all tests; pass bits for test3 as second arg (default 40)
        BITS=${2:-40}
        compile
        java -Xmx4g -cp out Main 1
        java -Xmx4g -cp out Main 2
        java -Xmx4g -cp out Main 3 "$BITS"
        java -Xmx4g -cp out Main 4
        java -Xmx4g -cp out Main 5
        ;;
    *)
        echo "Usage: ./run.sh <command> [args]"
        echo ""
        echo "  compile          Compile all source files to out/"
        echo "  clean            Remove out/"
        echo "  check-xfast      Run XFast concurrency correctness test"
        echo "  check-yfast      Run YFast concurrency correctness test"
        echo "  test1            Sweep ops {2^14,2^16,2^18}: BST/SkipList/XFast/YFastV1/YFastV2"
        echo "  test2            bits=32, 16 threads: BST vs YFastV1 vs YFastV2"
        echo "  test3 <bits>     bits=<bits> (>32): bounded vs unbounded LFL YFastV2"
        echo "  test4            bits=32: bucket-size sweep 1x..128x"
        echo "  test5            bits=32: thread-count sweep 1..nCPU"
        echo "  all [bits]       Run all tests (test3 uses bits, default 40)"
        exit 1
        ;;
esac
