from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from matplotlib.lines import Line2D


LOG_FILE = Path("logs/log.csv")
OUTPUT_DIR = Path("plots")

PALETTE = {
    "Vertical": "#1f77b4",
    "Vertical Optimized": "#ff7fae",
    "Vertical Functions": "#1f77b4",
    "Vertical Functions (Hash Index)": "#ff7f0e",
    "Vertical Functions (Batch)": "#8a2b91",
    "Horizontal": "#d31313",
}

XTICKS = {
    "tupleCount": [2000, 4000, 8000],
    "attributeCount": [5, 10, 15],
    "sparsity": [0.5, 0.75, 0.9375],
}

XLABELS = {
    "tupleCount": "Tuple Count",
    "attributeCount": "Attribute Count",
    "sparsity": "Sparsity",
}

FIGURES = [
    {
        "filename": "01_vertical_vs_optimized_queries.png",
        "title": "Vertical Optimization: Query Performance",
        "metric": "queries",
        "representations": ["Vertical", "Vertical Optimized"],
    },
    {
        "filename": "02_vertical_vs_optimized_size.png",
        "title": "Vertical Optimization: Storage Cost",
        "metric": "size",
        "representations": ["Vertical", "Vertical Optimized"],
    },
    {
        "filename": "03_vertical_function_variants_queries.png",
        "title": "Vertical Function Variants: Query Performance",
        "metric": "queries",
        "representations": [
            "Vertical Functions",
            "Vertical Functions (Hash Index)",
            "Vertical Functions (Batch)",
        ],
    },
    {
        "filename": "04_best_vertical_vs_horizontal_queries.png",
        "title": "Best Vertical Variant vs Horizontal: Query Performance",
        "metric": "queries",
        "representations": ["Horizontal", "Vertical Functions (Hash Index)"],
    },
    {
        "filename": "05_best_vertical_vs_horizontal_size.png",
        "title": "Best Vertical Variant vs Horizontal: Storage Cost",
        "metric": "size",
        "representations": ["Horizontal", "Vertical Functions (Hash Index)"],
    },
]


def set_common_axis_labels(ax, x: str, y: str) -> None:
    ax.set_xticks(XTICKS[x])
    ax.set_xlabel(XLABELS[x])
    ax.set_ylabel(y)


def save_current_plot(fig, filename: str) -> None:
    OUTPUT_DIR.mkdir(exist_ok=True)
    fig.tight_layout(rect=(0, 0, 1, 0.88))
    fig.savefig(OUTPUT_DIR / filename, dpi=200)
    plt.close()


def representation_legend(representations: list[str]) -> list[Line2D]:
    return [
        Line2D([0], [0], color=PALETTE[representation], lw=2, label=representation)
        for representation in representations
    ]


def plot_queries_panel(ax, df: pd.DataFrame, representations: list[str], x: str) -> None:
    data = df[df["representation"].isin(representations)]
    data_long = data.melt(
        id_vars=["representation", x],
        value_vars=["queryCount1", "queryCount2"],
        var_name="query",
        value_name="count",
    )
    data_long["query"] = data_long["query"].map(
        {
            "queryCount1": "Q1",
            "queryCount2": "Q2",
        }
    )

    sns.lineplot(
        ax=ax,
        data=data_long,
        x=x,
        y="count",
        hue="representation",
        style="query",
        markers=True,
        dashes=True,
        palette=PALETTE,
        errorbar=None,
        legend=None,
    )
    set_common_axis_labels(ax, x, "Query Count")
    ax.set_title(XLABELS[x])


def plot_size_panel(ax, df: pd.DataFrame, representations: list[str], x: str) -> None:
    data = df[df["representation"].isin(representations)]

    sns.lineplot(
        ax=ax,
        data=data,
        x=x,
        y="tableSize",
        hue="representation",
        marker="o",
        palette=PALETTE,
        errorbar=None,
        legend=None,
    )
    set_common_axis_labels(ax, x, "Table Size (bytes)")
    ax.set_title(XLABELS[x])


def plot_figure(df: pd.DataFrame, figure: dict[str, object]) -> None:
    representations = figure["representations"]
    if not isinstance(representations, list):
        raise TypeError("representations must be a list")

    fig, axes = plt.subplots(1, 3, figsize=(15, 4.8))

    for ax, x in zip(axes, XTICKS):
        if figure["metric"] == "queries":
            plot_queries_panel(ax, df, representations, x)
        else:
            plot_size_panel(ax, df, representations, x)

    handles = representation_legend(representations)
    if figure["metric"] == "queries":
        handles = [
            *handles,
            Line2D([0], [0], color="black", linestyle="-", label="Query 1"),
            Line2D([0], [0], color="black", linestyle="--", label="Query 2"),
        ]

    fig.suptitle(str(figure["title"]), fontsize=16)
    fig.legend(handles=handles, loc="upper center", ncol=min(len(handles), 5), frameon=False, bbox_to_anchor=(0.5, 0.86))
    save_current_plot(fig, str(figure["filename"]))


def main() -> None:
    sns.set_theme(style="whitegrid")
    df = pd.read_csv(LOG_FILE)

    for figure in FIGURES:
        plot_figure(df, figure)


if __name__ == "__main__":
    main()
