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
        return String.format(
            "RunResult{name='%s', n=%d, universe=%d, totalTime=%.6f, ns_per_op=%.2f, Mops_per_s=%.2f}",
            name, n, universe, timeSeconds, nsPerOp(), mopsPerSec());
    }
}
