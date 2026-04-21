import os
import re
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.ticker as mtick

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "raw.log")

LINE = re.compile(
    r"(?P<s>SkipList|YFastV1|YFastV2)_t\d+_"
    r"(?:random_(?P<rt>\d+)thread|perOp_(?P<op>\w+)_(?P<ot>\d+)t)"
    r"\s+\S+\s+ns/op\s+(?P<mops>\d+\.\d+)"
)

THREADS = [1, 2, 4, 8, 16, 32, 64]
OPS = ["random", "insert", "query", "successor", "predecessor", "delete"]
TITLES = {
    "random": "Random Mix (insert / query / successor, 1/3 each)",
    "insert": "Insert",
    "query": "Query (Membership)",
    "successor": "Successor",
    "predecessor": "Predecessor",
    "delete": "Delete",
}
STRUCTS = [
    ("SkipList", "ConcurrentSkipListSet", "#818cf8", "s"),
    ("YFastV1",  "Y-Fast V1 (global lock)", "#fbbf24", "^"),
    ("YFastV2",  "Y-Fast V2 (partitioned LFL)", "#34d399", "o"),
]


def parse():
    d = {}
    with open(LOG) as f:
        for line in f:
            m = LINE.search(line)
            if not m:
                continue
            s = m["s"]
            op = "random" if m["rt"] else m["op"]
            t = int(m["rt"] or m["ot"])
            d.setdefault(s, {}).setdefault(op, {})[t] = float(m["mops"])
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


def series(d, struct, op):
    return [d[struct][op][t] for t in THREADS]


def plot_op(d, op, ax, legend=True):
    for k, label, c, m in STRUCTS:
        ax.plot(THREADS, series(d, k, op), color=c, marker=m, label=label,
                lw=2.4, ms=7, markeredgecolor="#1e293b", markeredgewidth=1.2)
    ax.set_xscale("log", base=2)
    ax.set_xticks(THREADS)
    ax.xaxis.set_major_formatter(mtick.ScalarFormatter())
    ax.set_xlabel("Threads")
    ax.set_ylabel("Throughput (Mops/s)")
    ax.grid(True, which="both", linewidth=0.5)
    if legend:
        ax.legend(loc="upper left", frameon=True)


def write_per_op(d):
    for op in OPS:
        fig, ax = plt.subplots(figsize=(10, 5.8))
        plot_op(d, op, ax)
        ax.set_title(f"{TITLES[op]}  —  U=2^63, N=2^23", fontweight="bold", fontsize=15, pad=12)
        fig.tight_layout()
        fig.savefig(os.path.join(HERE, f"{op}.png"))
        plt.close(fig)
        print(f"  {op}.png")


def write_grid(d):
    fig, axes = plt.subplots(2, 3, figsize=(19, 10.5))
    for ax, op in zip(axes.flat, OPS):
        plot_op(d, op, ax, legend=False)
        ax.set_title(TITLES[op], fontweight="bold", fontsize=13, pad=8)
    h, l = axes[0, 0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=3, bbox_to_anchor=(0.5, -0.01),
               frameon=True, fontsize=12)
    fig.suptitle("test2: Thread Scalability  —  U=2^63, N=2^23",
                 fontsize=18, fontweight="bold", y=1.01)
    fig.tight_layout(rect=[0, 0.03, 1, 0.98])
    fig.savefig(os.path.join(HERE, "all_ops.png"))
    plt.close(fig)
    print("  all_ops.png")


def write_speedup(d):
    ops = ["insert", "query", "successor", "predecessor", "delete"]
    x = np.arange(len(ops))
    w = 0.35
    v1 = [d["YFastV1"][o][64] / d["SkipList"][o][64] for o in ops]
    v2 = [d["YFastV2"][o][64] / d["SkipList"][o][64] for o in ops]
    fig, ax = plt.subplots(figsize=(11, 6))
    b1 = ax.bar(x - w/2, v1, w, color="#fbbf24", label="Y-Fast V1", edgecolor="#1e293b")
    b2 = ax.bar(x + w/2, v2, w, color="#34d399", label="Y-Fast V2", edgecolor="#1e293b")
    ax.axhline(1.0, color="#818cf8", ls="--", lw=1.4, label="SkipList baseline")
    ax.set_xticks(x)
    ax.set_xticklabels([o.capitalize() for o in ops])
    ax.set_ylabel("Speedup vs ConcurrentSkipListSet")
    ax.set_title("Speedup at 64 threads  —  U=2^63, N=2^23",
                 fontweight="bold", fontsize=15, pad=12)
    ax.grid(axis="y", linewidth=0.5)
    ax.legend(loc="upper left", frameon=True)
    for bars, c in [(b1, "#fbbf24"), (b2, "#34d399")]:
        for bar in bars:
            h = bar.get_height()
            ax.text(bar.get_x() + bar.get_width()/2, h + 0.05, f"{h:.2f}x",
                    ha="center", fontsize=9.5, fontweight="bold", color=c)
    fig.tight_layout()
    fig.savefig(os.path.join(HERE, "speedup_64t.png"))
    plt.close(fig)
    print("  speedup_64t.png")


if __name__ == "__main__":
    set_theme()
    d = parse()
    print(f"parsed {sum(len(v) for v in d.values())} structure/op series from {LOG}")
    write_per_op(d)
    write_grid(d)
    write_speedup(d)
