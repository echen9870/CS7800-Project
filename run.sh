#!/bin/bash

set -e

case "$1" in
    compile)
        echo "Compiling all files to out/"
        mkdir -p out
        javac -d out xFast/*.java yFast/*.java benchmark/*.java Main.java
        echo "Done."
        ;;
    clean)
        echo "Removing out/"
        rm -rf out
        echo "Done."
        ;;
    check-xfast)
        mkdir -p out
        javac -d out xFast/*.java yFast/*.java benchmark/*.java Main.java
        echo "Running XFastChecker..."
        java -cp out xFast.TestXFastQueryConcurrencyCorrectness
        ;;
    check-yfast)
        mkdir -p out
        javac -d out xFast/*.java yFast/*.java benchmark/*.java Main.java
        echo "Running YFastChecker..."
        java -cp out yFast.TestYFastQueryConcurrencyCorrectness
        ;;
    benchmark)
        BITS=${2:-20}
        mkdir -p out
        javac -d out xFast/*.java yFast/*.java benchmark/*.java Main.java
        echo "Running benchmark with bits=$BITS..."
        java -cp out Main "$BITS"
        ;;
    *)
        echo "Usage: ./run.sh {compile|clean|check-xfast|check-yfast|benchmark [bits]}"
        echo ""
        echo "  compile       - Compile all files to out/"
        echo "  clean         - Remove out/"
        echo "  check-xfast   - Run XFast concurrency correctness test"
        echo "  check-yfast   - Run YFast concurrency correctness test"
        echo "  benchmark [b] - Run benchmarks (default bits=20)"
        exit 1
        ;;
esac
