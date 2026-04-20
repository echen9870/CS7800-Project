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
        # Test 1: Thread Scalability on YFastV1 and YFastV2 with parameter
        # bits : number of bits in the universe
        # ops : number of operations
        # Usage: ./run.sh test1 <bits> <ops>
        BITS=${2:?'test1 requires a bits argument (e.g. ./run.sh test1 32 1000000)'}
        OPS=${3:?'test1 requires a ops argument (e.g. ./run.sh test1 32 1000000)'}
        compile
        java -Xmx4g -cp out Main 1 "$BITS" "$OPS"
        ;;
    test2)
        BITS=${2:?'test2 requires a bits argument (e.g. ./run.sh test2 32 1048576)'}
        OPS=${3:?'test2 requires an ops argument (e.g. ./run.sh test2 32 1048576)'}
        compile
        java -Xmx4g -cp out Main 2 "$BITS" "$OPS"
        ;;
    test3)
        BITS=${2:?'test3 requires a bits argument (e.g. ./run.sh test3 32 1048576)'}
        OPS=${3:?'test3 requires an ops argument (e.g. ./run.sh test3 32 1048576)'}
        compile
        java -Xmx4g -cp out Main 3 "$BITS" "$OPS"
        ;;
    test4)
        BITS=${2:-63}
        compile
        java -Xmx8g -cp out Main 4 "$BITS"
        ;;
    test5)
        BITS=${2:-63}
        compile
        java -Xmx8g -cp out Main 5 "$BITS"
        ;;
    test6)
        BITS=${2:-63}
        compile
        java -Xmx8g -cp out Main 6 "$BITS"
        ;;
    test7)
        BITS=${2:-63}
        compile
        java -Xmx16g -cp out Main 7 "$BITS"
        ;;
    test8)
        BITS=${2:-63}
        compile
        java -Xmx16g -cp out Main 8 "$BITS"
        ;;
    test9)
        BITS=${2:-63}
        OPS=${3:?'test9 requires an ops argument (e.g. ./run.sh test9 32 1048576)'}
        compile
        java -Xmx16g -cp out Main 9 "$BITS" "$OPS"
        ;;
    *)
        echo "Usage: ./run.sh <command> [args]"
        echo ""
        echo "  compile          Compile all source files to out/"
        echo "  clean            Remove out/"
        echo "  check-xfast      Run XFast concurrency correctness test"
        echo "  check-yfast      Run YFast concurrency correctness test"
        echo "  test1 <bits> <ops>  Thread scalability: YFastV1 vs YFastV2 (1-64 threads)"
        echo "  test2 <bits> <ops>  Thread scalability: YFastV1 vs YFastV2 vs ConcurrentSkipList(1-64 threads)"
        echo "  test3 <bits> <ops>  LFL Bounded vs Unbounded: YFastV2 (1-64 threads)"
        echo "  test4 [bits]        YFastV2 insert sweep: 2^20..2^26 at 64 threads"
        echo "  test5 [bits]        SkipList Ops Sweeping (64 threads)"
        echo "  test6 [bits]        YFastV2 Bounded LFL Ops Sweeping (64 threads)"
        echo "  test7 [bits]        Unified Ops Sweep (64 threads)"
        echo "  test8 [bits]        Unified Thread Sweep at 2^24 ops (1-64 threads)"
        exit 1
        ;;
esac
