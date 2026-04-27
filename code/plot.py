"""
All variants run for 10s, on the same machine.

Comparison:
    Vertical vs Vertical Optimized
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries,
    once for size

    Vertical Function vs Vertical Functions Hash Index vs Vertical Functions Batch
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries,
    once for size

    Vertical Optimized vs Best Vertical Function
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries,
    once for size

    Best Vertical vs Horizontal
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries,
    once for size
"""

import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D

sns.set_theme(style="whitegrid")


def partition_representation(df: pd.DataFrame) -> dict[str, pd.DataFrame]:
    representations = [
        "Horizontal",
        "Vertical",
        "Vertical Optimized",
        "Vertical Functions",
        "Vertical Functions (Hash Index)",
        "Vertical Functions (Batch)",
    ]

    return {
        # type: ignore
        representation: df[df["representation"] == representation]
        for representation in representations
    }


palette = {
    "Vertical": "#1f77b4",  # blue
    "Vertical Optimized": "#ff7f0e",  # orange
}


df = pd.read_csv("logs/log.csv")

partitions = partition_representation(df)


def plot_queries_2(df, x, title):
    plt.figure(figsize=(6, 4))

    # reshape to long format
    df_long = df.melt(
        id_vars=["representation", x],
        value_vars=["queryCount1", "queryCount2"],
        var_name="query",
        value_name="count",
    )

    # nicer labels
    df_long["query"] = df_long["query"].map(
        {
            "queryCount1": "Q1",
            "queryCount2": "Q2",
        }
    )

    ax = sns.lineplot(
        data=df_long,
        x=x,
        y="count",
        hue="representation",  # color = Vertical vs Optimized
        style="query",  # solid vs dashed = Q1 vs Q2
        markers=True,
        dashes=True,
        errorbar=None,
        legend=None,  # type: ignore
    )

    legend_elements = [
        Line2D([0], [0], color=palette["Vertical"], lw=2, label="Vertical"),
        Line2D(
            [0],
            [0],
            color=palette["Vertical Optimized"],
            lw=2,
            label="Vertical Optimized",
        ),
        Line2D([0], [0], color="black", linestyle="-", label="Query 1"),
        Line2D([0], [0], color="black", linestyle="--", label="Query 2"),
    ]
    ax.legend(handles=legend_elements, title=None)

    plt.title(title)
    plt.ylabel("Query Count")
    plt.tight_layout()
    plt.show()


def plot_size_1(df, x, title):
    plt.figure(figsize=(6, 4))

    ax = sns.lineplot(
        data=df,
        x=x,
        y="tableSize",
        hue="representation",
        palette=palette,
        marker="o",
        errorbar=None,
        legend=False,
    )

    legend_elements = [
        Line2D([0], [0], color=palette["Vertical"], lw=2, label="Vertical"),
        Line2D(
            [0],
            [0],
            color=palette["Vertical Optimized"],
            lw=2,
            label="Vertical Optimized",
        ),
    ]

    ax.legend(handles=legend_elements, title=None)

    plt.title(title)
    plt.ylabel("Table Size")
    plt.tight_layout()
    plt.show()


# ---- Vertical vs Vertical Optimized ----

d = df[df["representation"].isin(["Vertical", "Vertical Optimized"])]

# Queries
plot_queries_2(d, "tupleCount", "Queries vs Tuple Count")
plot_queries_2(d, "attributeCount", "Queries vs Attribute Count")
plot_queries_2(d, "sparsity", "Queries vs Sparsity")

# Size
# NOTE: Consider dropping this, it is not very interesting ("hoho, bigger table leads to more size")
plot_size_1(d, "tupleCount", "Size vs Tuple Count")
plot_size_1(d, "attributeCount", "Size vs Attribute Count")
plot_size_1(d, "sparsity", "Size vs Sparsity")

