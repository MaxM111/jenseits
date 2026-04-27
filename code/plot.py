"""
All variants run for 10s, on the same machine.

Comparison:
    Vertical vs Vertical Optimized
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries/s,
    once for size

    Vertical Function vs Vertical Functions Hash Index vs Vertical Functions Batch
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries/s,
    once for size

    Vertical Optimized vs Best Vertical Function
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries/s,
    once for size

    Best Vertical vs Horizontal
        - Tuple Count
        - Attribute Count
        - Sparsity
    once for queries/s,
    once for size
"""

import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

sns.set(style="whitegrid")


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


df = pd.read_csv("logs/log.csv")

partitions = partition_representation(df)


def plot_queries(df, x, title):
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

    sns.lineplot(
        data=df_long,
        x=x,
        y="count",
        hue="representation",  # color = Vertical vs Optimized
        style="query",  # solid vs dashed = Q1 vs Q2
        markers=True,
        dashes=True,
        errorbar=None,
    )

    plt.title(title)
    plt.ylabel("Query Count")
    plt.tight_layout()
    plt.show()


def plot_size(df, x, title):
    plt.figure(figsize=(6, 4))

    sns.lineplot(
        data=df,
        x=x,
        y="tableSize",
        hue="representation",
        marker=".",
        errorbar=None,
    )

    plt.title(title)
    plt.ylabel("Table Size")
    plt.tight_layout()
    plt.show()


# ---- Vertical vs Vertical Optimized ----

d = df[df["representation"].isin(["Vertical", "Vertical Optimized"])]

# Queries
plot_queries(d, "tupleCount", "Queries vs Tuple Count")
plot_queries(d, "attributeCount", "Queries vs Attribute Count")
plot_queries(d, "sparsity", "Queries vs Sparsity")

# Size
# NOTE: Consider dropping this, it is not very interesting ("hoho, bigger table leads to more size" - "duh")
plot_size(d, "tupleCount", "Size vs Tuple Count")
plot_size(d, "attributeCount", "Size vs Attribute Count")
plot_size(d, "sparsity", "Size vs Sparsity")
