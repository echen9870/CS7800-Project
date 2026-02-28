public class RunResult {
    public String name;

    public double insertTimeSeconds;
    public double queryTimeSeconds;
    public double successorTimeSeconds;

    public int n;
    public int universe;

    public RunResult(String name, int n, int universe,
                     double insertTimeSeconds, double queryTimeSeconds, double successorTimeSeconds) {
        this.name = name;
        this.n = n;
        this.universe = universe;

        this.insertTimeSeconds = insertTimeSeconds;
        this.queryTimeSeconds = queryTimeSeconds;
        this.successorTimeSeconds = successorTimeSeconds;
    }

    @Override
    public String toString() {
        return String.format(
                "RunResult{name='%s', n=%d, universe=%d, insert=%.6f, query=%.6f, successor=%.6f}",
                name, n, universe, insertTimeSeconds, queryTimeSeconds, successorTimeSeconds
        );
    }
}