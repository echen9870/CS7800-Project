import os
import re
import math
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "raw.log")

BITS = 63

LINE = re.compile(
    r"V2_b(?P<b>\d+)_(?P<op>insert|query|successor|predecessor|delete)_\d+t"
    r"\s+\S+\s+ns/op\s+(?P<mops>\d+\.\d+)"
)

OP_STYLE = {
    "insert":      ("Insert",      "#fbbf24", "o"),
    "delete":      ("Delete",      "#ef4444", "s"),
    "query":       ("Query",       "#34d399", "D"),
    "successor":   ("Successor",   "#818cf8", "^"),
    "predecessor": ("Predecessor", "#f472b6", "v"),
}

MUTATIONS = ["insert", "delete"]
READS = ["query", "successor", "predecessor"]


def parse():
    d = {}
    with open(LOG) as f:
        for line in f:
            m = LINE.search(line)
            if not m:
                continue
            b = int(m["b"])
            d.setdefault(m["op"], {})[b] = float(m["mops"])
    return d


def set_theme():
    plt.rcParams.update({
        "figure.facecolor": "#0f172a",
        "axes.facecolor":   "#1e293b",
        "axes.edgecolor":   "#334155",
        "axes.labelcolor":  "#f1f5f9",
        "axes.titlecolor":  "#f1f5f9",
        "text.color":       "#f1f5f9",
        "xtick.color":      "#94a3b8",
        "ytick.color":      "#94a3b8",
        "grid.color":       "#334155",
        "grid.alpha":       0.6,
        "legend.facecolor": "#1e293b",
        "legend.edgecolor": "#334155",
        "legend.labelcolor": "#f1f5f9",
        "font.size": 12,
        "axes.spines.top":   False,
        "axes.spines.right": False,
        "savefig.dpi": 200,
        "savefig.bbox": "tight",
    })


def plot_ops(d, ops, title, out):
    buckets = sorted({b for op in ops for b in d.get(op, {})})
    mults = [b // BITS for b in buckets]
    fig, ax = plt.subplots(figsize=(10, 5.8))
    for op in ops:
        series = d.get(op, {})
        if not series:
            continue
        label, color, marker = OP_STYLE[op]
        xs = [b for b in buckets if b in series]
        ys = [series[b] for b in xs]
        ax.plot(xs, ys, color=color, marker=marker, label=label,
                lw=2.4, ms=8, markeredgecolor="#1e293b", markeredgewidth=1.2)

    ax.set_xscale("log", base=2)
    ax.set_xticks(buckets)
    ax.set_xticklabels([str(b) for b in buckets])
    ax.set_xlabel("Bucket size (longs)")
    ax.set_ylabel("Throughput (Mops/s)")
    ax.grid(True, which="both", linewidth=0.5)
    ax.legend(loc="best", frameon=True)
    ax.set_title(title, fontweight="bold", fontsize=15, pad=12)

    ax_top = ax.secondary_xaxis("top")
    ax_top.set_xscale("log", base=2)
    ax_top.set_xticks(buckets)
    ax_top.set_xticklabels([f"{m}x" for m in mults], fontsize=10)
    ax_top.set_xlabel(f"Multiplier of bits (bits={BITS})", labelpad=8)

    fig.tight_layout()
    fig.savefig(os.path.join(HERE, out))
    plt.close(fig)
    print(f"  {out}")


if __name__ == "__main__":
    set_theme()
    d = parse()
    print(f"parsed {sum(len(v) for v in d.values())} op/bucket entries from {LOG}")
    plot_ops(d, MUTATIONS, "Bucket Size Tradeoff (Mutations)", "mutations.png")
    plot_ops(d, READS,     "Bucket Size Tradeoff (Reads)",     "reads.png")
    plot_ops(d, MUTATIONS + READS, "Bucket Size Tradeoff (All Ops)", "all_ops.png")
