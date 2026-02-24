import java.util.Random;
import java.util.TreeSet;
import java.util.Objects;

public class TestCorrectness {

    public static void runXFastCorrectness(int bits, int universe, int insertCount, int checkCount) {
        XFastTree xFast = new XFastTree(bits);
        YFastTree yFast = new YFastTree(bits);
        TreeSet<Integer> bst = new TreeSet<>();


        // Insert random values into both trees
        Random rng = new Random();
        for (int i = 0; i < insertCount; i++) {
            int value = rng.nextInt(universe);
            xFast.insert(value);
            yFast.insert(value);
            bst.add(value);
        }

        // Query + successor checks
        for (int i = 0; i < checkCount; i++) {
            int key = rng.nextInt(universe);

            // Query mismatch
            if (bst.contains(key) != xFast.query(key)) {
                throw new RuntimeException("X Fast Query mismatch for key=" + key +" expected=" + bst.contains(key) + " got=" + xFast.query(key));
            }
            if (bst.contains(key) != yFast.query(key)) {
                throw new RuntimeException("y Fast Query mismatch for key=" + key +" expected=" + bst.contains(key) + " got=" + yFast.query(key));
            }

            // Successor mismatch
            if (!Objects.equals(bst.ceiling(key), xFast.successor(key))) {
                throw new RuntimeException("X Fast Successor mismatch for key=" + key +" expected=" + bst.ceiling(key) + " got=" + xFast.successor(key));
            }
            if (!Objects.equals(bst.ceiling(key), yFast.successor(key))) {
                throw new RuntimeException("Y FastSuccessor mismatch for key=" + key +" expected=" + bst.ceiling(key) + " got=" + yFast.successor(key));
            }
        }
    }

    public static void main(String[] args) {

        int universe = 1 << 16;
        int insertCount = 1 << 12;
        int checkCount = 1 << 16;

        try {
            runXFastCorrectness(16, universe, insertCount, checkCount);
            System.out.println("Correctness OK: bits=" + 16 +
                    " inserted=" + insertCount +
                    " checks=" + checkCount);
        } catch (RuntimeException e) {
            System.out.println("Correctness FAILED: " + e.getMessage());
            throw e;
        }
    }
}