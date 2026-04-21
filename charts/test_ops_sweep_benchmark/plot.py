import os
import re
import math
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "raw.log")

LINE = re.compile(
    r"(?P<s>V2bounded|V2unbounded|SkipList)_(?P<n>\d+)_(?P<op>insert|query)_\d+t"
    r"\s+\S+\s+ns/op\s+(?P<mops>\d+\.\d+)"
)

OPS = ["insert", "query"]
STRUCTS = [
    ("SkipList",    "ConcurrentSkipListSet",        "#818cf8", "s"),
    ("V2bounded",   "Y-Fast V2 (bounded LFL)",      "#34d399", "o"),
    ("V2unbounded", "Y-Fast V2 (unbounded LFL)",    "#f472b6", "D"),
]


def parse():
    d = {}
    with open(LOG) as f:
        for line in f:
            m = LINE.search(line)
            if not m:
                continue
            d.setdefault(m["s"], {}).setdefault(m["op"], {})[int(m["n"])] = float(m["mops"])
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


def approx_millions_label(n):
    m = n / 1_000_000
    if m < 10:
        return f"~{m:.1f}M"
    return f"~{int(round(m))}M"


def plot_op(d, op, title, out):
    ns = sorted({n for s in STRUCTS for n in d.get(s[0], {}).get(op, {})})
    fig, ax = plt.subplots(figsize=(10, 5.8))
    for k, label, c, m in STRUCTS:
        series = d.get(k, {}).get(op, {})
        if not series:
            continue
        xs = [n for n in ns if n in series]
        ys = [series[n] for n in xs]
        ax.plot(xs, ys, color=c, marker=m, label=label,
                lw=2.4, ms=7, markeredgecolor="#1e293b", markeredgewidth=1.2)
    ax.set_xscale("log", base=2)
    ax.set_xticks(ns)
    ax.set_xticklabels([f"$2^{{{int(math.log2(n))}}}$" for n in ns])
    ax.set_xlabel("Operations (N, powers of two)")
    ax.set_ylabel("Throughput (Mops/s)")
    ax.grid(True, which="both", linewidth=0.5)
    ax.legend(loc="best", frameon=True)
    ax.set_title(title, fontweight="bold", fontsize=15, pad=12)

    # Show a light-weight top axis for approximate operation counts in millions.
    ax_top = ax.secondary_xaxis("top")
    ax_top.set_xscale("log", base=2)
    ax_top.set_xticks(ns)
    ax_top.set_xticklabels([approx_millions_label(n) for n in ns], fontsize=10)
    ax_top.set_xlabel("Approx. operations", labelpad=8)

    fig.tight_layout()
    fig.savefig(os.path.join(HERE, out))
    plt.close(fig)
    print(f"  {out}")


if __name__ == "__main__":
    set_theme()
    d = parse()
    print(f"parsed {sum(len(v) for v in d.values())} structure/op series from {LOG}")
    plot_op(d, "insert", "Ops Scalability", "insert.png")
    plot_op(d, "query",  "Ops Scalability", "query.png")
