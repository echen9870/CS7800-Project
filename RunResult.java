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
        return "RunResult{" +
                "name='" + name + '\'' +
                ", n=" + n +
                ", universe=" + universe +
                ", insert=" + insertTimeSeconds +
                ", query=" + queryTimeSeconds +
                ", successor=" + successorTimeSeconds +
                '}';
    }
}