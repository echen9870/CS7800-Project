import java.util.Random;

public class OpsGenerator {
    public enum OpType {
        INSERT,
        QUERY,
        SUCCESSOR
    }

    public static class Op {
        public OpType type;
        public int key;

        public Op(OpType type, int key) {
            this.type = type;
            this.key = key;
        }
    }

    public int universe;
    public int size;
    public long seed;

    public int insertPercent;
    public int queryPercent;
    public int successorPercent;

    public Op[] ops;

    public OpsGenerator(int universe, int size) {
        this(universe, size, 1234567L, 50, 25, 25);
    }

    public OpsGenerator(int universe, int size, long seed, int insertPercent, int queryPercent, int successorPercent) {
        this.universe = universe;
        this.size = size;
        this.seed = seed;

        this.insertPercent = insertPercent;
        this.queryPercent = queryPercent;
        this.successorPercent = successorPercent;

        this.ops = new Op[size];
        this.generate();
    }

    public void generate() {
        Random rng = new Random(this.seed);

        for (int i = 0; i < this.size; i++) {
            int key = rng.nextInt(this.universe);
            int r = rng.nextInt(100);

            OpType type;
            if (r < this.insertPercent) {
                type = OpType.INSERT;
            } else if (r < this.insertPercent + this.queryPercent) {
                type = OpType.QUERY;
            } else {
                type = OpType.SUCCESSOR;
            }

            this.ops[i] = new Op(type, key);
        }
    }

    public Op get(int i) {
        return this.ops[i];
    }
}