package benchmark;

public class RunResult {
    public String name;
    public long n;
    public long universe;
    public double timeSeconds;

    public RunResult(String name, long n, long universe, double timeSeconds) {
        this.name = name;
        this.n = n;
        this.universe = universe;
        this.timeSeconds = timeSeconds;
    }

    public double nsPerOp() {
        return timeSeconds * 1e9 / n;
    }

    public double mopsPerSec() {
        return n / timeSeconds / 1e6;
    }

    @Override
    public String toString() {
        return String.format("  %-40s %10.2f ns/op  %8.2f Mops/s  (%.3fs)",
            name, nsPerOp(), mopsPerSec(), timeSeconds);
    }
}
