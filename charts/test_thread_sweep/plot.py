import os
import re
import math
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "raw.log")

LINE = re.compile(
    r"(?P<s>V2bounded|V2unbounded|SkipList)_(?P<n>\d+)_(?P<op>insert|query)_(?P<t>\d+)t"
    r"\s+\S+\s+ns/op\s+(?P<mops>\d+\.\d+)"
)

OPS = ["insert", "query"]
STRUCTS = [
    ("SkipList", "ConcurrentSkipListSet", "#818cf8", "s"),
    ("V2bounded", "Y-Fast V2 (bounded LFL)", "#34d399", "o"),
    ("V2unbounded", "Y-Fast V2 (unbounded LFL)", "#f472b6", "D"),
]


def parse():
    d = {}
    n_value = None
    with open(LOG) as f:
        for line in f:
            m = LINE.search(line)
            if not m:
                continue
            n_value = int(m["n"])
            s = m["s"]
            op = m["op"]
            t = int(m["t"])
            d.setdefault(s, {}).setdefault(op, {})[t] = float(m["mops"])
    return d, n_value


def set_theme():
    plt.rcParams.update({
        "figure.facecolor": "#0f172a",
        "axes.facecolor": "#1e293b",
        "axes.edgecolor": "#334155",
        "axes.labelcolor": "#f1f5f9",
        "axes.titlecolor": "#f1f5f9",
        "text.color": "#f1f5f9",
        "xtick.color": "#94a3b8",
        "ytick.color": "#94a3b8",
        "grid.color": "#334155",
        "grid.alpha": 0.6,
        "legend.facecolor": "#1e293b",
        "legend.edgecolor": "#334155",
        "legend.labelcolor": "#f1f5f9",
        "font.size": 12,
        "axes.spines.top": False,
        "axes.spines.right": False,
        "savefig.dpi": 200,
        "savefig.bbox": "tight",
    })


def plot_op(d, n_value, op, out):
    ts = sorted({t for s in STRUCTS for t in d.get(s[0], {}).get(op, {})})
    fig, ax = plt.subplots(figsize=(10, 5.8))
    for k, label, c, m in STRUCTS:
        series = d.get(k, {}).get(op, {})
        if not series:
            continue
        ys = [series[t] for t in ts if t in series]
        xs = [t for t in ts if t in series]
        ax.plot(
            xs,
            ys,
            color=c,
            marker=m,
            label=label,
            lw=2.4,
            ms=7,
            markeredgecolor="#1e293b",
            markeredgewidth=1.2,
        )

    ax.set_xscale("log", base=2)
    ax.set_xticks(ts)
    ax.set_xticklabels([f"$2^{{{int(math.log2(t))}}}$" for t in ts])
    ax.set_xlabel("Threads (powers of two)")
    ax.set_ylabel("Throughput (Mops/s)")
    ax.grid(True, which="both", linewidth=0.5)
    ax.legend(loc="best", frameon=True)

    ax_top = ax.secondary_xaxis("top")
    ax_top.set_xscale("log", base=2)
    ax_top.set_xticks(ts)
    ax_top.set_xticklabels([f"{t}t" for t in ts], fontsize=10)
    ax_top.set_xlabel("Approx. thread count", labelpad=8)

    title_n = f"2^{int(math.log2(n_value))}" if n_value else "N"
    ax.set_title(
        f"Thread Sweep ({op.capitalize()})  -  N={title_n}, bits=63",
        fontweight="bold",
        fontsize=15,
        pad=12,
    )
    fig.tight_layout()
    fig.savefig(os.path.join(HERE, out))
    plt.close(fig)
    print(f"  {out}")


if __name__ == "__main__":
    set_theme()
    d, n_value = parse()
    print(f"parsed {sum(len(v) for v in d.values())} structure/op series from {LOG}")
    plot_op(d, n_value, "insert", "insert.png")
    plot_op(d, n_value, "query", "query.png")
