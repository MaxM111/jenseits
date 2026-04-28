import os
from pathlib import Path

os.environ.setdefault(
    "MPLCONFIGDIR", str(Path(__file__).resolve().parent / ".matplotlib")
)

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from PyQt6.QtCore import QObject, QUrl, pyqtProperty, pyqtSignal, pyqtSlot


REPRESENTATION_COLORS = {
    "Horizontal": "#d31313",
    "Vertical": "#1f77b4",
    "Vertical Optimized": "#ff7fae",
    "Vertical Functions": "#2ca02c",
    "Vertical Functions (Hash Index)": "#ff7f0e",
    "Vertical Functions (Batch)": "#8a2b91",
}


class PlotController(QObject):
    plotsChanged = pyqtSignal()
    controlsChanged = pyqtSignal()
    representationsChanged = pyqtSignal()

    def __init__(self, csv_path: Path, output_dir: Path) -> None:
        super().__init__()
        self.csv_path = csv_path
        self.output_dir = output_dir
        self.output_dir.mkdir(exist_ok=True)

        self.df = pd.read_csv(self.csv_path)
        self.attribute_values = sorted(self.df["attributeCount"].unique().tolist())
        self.sparsity_values = sorted(self.df["sparsity"].unique().tolist())
        self.representation_values = self.df["representation"].drop_duplicates().tolist()
        self.selected_representations = set()
        self.attribute_index = 1 if len(self.attribute_values) > 1 else 0
        self.sparsity_index = 1 if len(self.sparsity_values) > 1 else 0
        self.version = 0
        self.query_plot_url = ""
        self.size_plot_url = ""

        sns.set_theme(style="whitegrid")
        self.update_plots()

    @pyqtProperty(int, notify=controlsChanged)
    def attributeIndex(self) -> int:
        return self.attribute_index

    @pyqtProperty(int, constant=True)
    def attributeCount(self) -> int:
        return len(self.attribute_values)

    @pyqtProperty(str, notify=controlsChanged)
    def attributeLabel(self) -> str:
        return str(self.attribute_values[self.attribute_index])

    @pyqtProperty(int, notify=controlsChanged)
    def sparsityIndex(self) -> int:
        return self.sparsity_index

    @pyqtProperty(int, constant=True)
    def sparsityCount(self) -> int:
        return len(self.sparsity_values)

    @pyqtProperty(str, notify=controlsChanged)
    def sparsityLabel(self) -> str:
        return str(self.sparsity_values[self.sparsity_index])

    @pyqtProperty(str, notify=plotsChanged)
    def queryPlotUrl(self) -> str:
        return self.query_plot_url

    @pyqtProperty(str, notify=plotsChanged)
    def sizePlotUrl(self) -> str:
        return self.size_plot_url

    @pyqtProperty("QVariantList", constant=True)
    def representationNames(self) -> list[str]:
        return self.representation_values

    @pyqtSlot(int)
    def setAttributeIndex(self, index: int) -> None:
        index = self.clamp_index(index, self.attribute_values)
        if index == self.attribute_index:
            return
        self.attribute_index = index
        self.controlsChanged.emit()
        self.update_plots()

    @pyqtSlot(str, result=bool)
    def isRepresentationSelected(self, representation: str) -> bool:
        return representation in self.selected_representations

    @pyqtSlot(str, bool)
    def setRepresentationSelected(self, representation: str, selected: bool) -> None:
        if representation not in self.representation_values:
            return
        if selected:
            self.selected_representations.add(representation)
        else:
            self.selected_representations.discard(representation)
        self.representationsChanged.emit()
        self.update_plots()

    @pyqtSlot(int)
    def setSparsityIndex(self, index: int) -> None:
        index = self.clamp_index(index, self.sparsity_values)
        if index == self.sparsity_index:
            return
        self.sparsity_index = index
        self.controlsChanged.emit()
        self.update_plots()

    def clamp_index(self, index: int, values: list[object]) -> int:
        return max(0, min(index, len(values) - 1))

    def filtered_data(self) -> pd.DataFrame:
        attribute_count = self.attribute_values[self.attribute_index]
        sparsity = self.sparsity_values[self.sparsity_index]
        return self.df[
            (self.df["attributeCount"] == attribute_count)
            & (self.df["sparsity"] == sparsity)
            & (self.df["representation"].isin(self.selected_representations))
        ]

    def selected_representation_order(self) -> list[str]:
        return [
            representation
            for representation in self.representation_values
            if representation in self.selected_representations
        ]

    def update_plots(self) -> None:
        data = self.filtered_data()
        self.version += 1

        query_path = self.output_dir / "query_count_vs_tuple_count.png"
        size_path = self.output_dir / "table_size_vs_tuple_count.png"

        self.write_query_plot(data, query_path)
        self.write_size_plot(data, size_path)

        self.query_plot_url = self.cache_busted_url(query_path)
        self.size_plot_url = self.cache_busted_url(size_path)
        self.plotsChanged.emit()

    def cache_busted_url(self, path: Path) -> str:
        return f"{QUrl.fromLocalFile(str(path)).toString()}?v={self.version}"

    def write_query_plot(self, data: pd.DataFrame, path: Path) -> None:
        if data.empty:
            self.write_empty_plot(path, "Query Count vs Tuple Count")
            return

        data_long = data.melt(
            id_vars=["representation", "tupleCount"],
            value_vars=["queryCount1", "queryCount2"],
            var_name="query",
            value_name="queryCount",
        )
        data_long["query"] = data_long["query"].map(
            {
                "queryCount1": "Query 1",
                "queryCount2": "Query 2",
            }
        )

        fig, ax = plt.subplots(figsize=(7.2, 4.4))
        sns.lineplot(
            ax=ax,
            data=data_long,
            x="tupleCount",
            y="queryCount",
            hue="representation",
            hue_order=self.selected_representation_order(),
            style="query",
            markers=True,
            dashes=True,
            palette=REPRESENTATION_COLORS,
            errorbar=None,
        )
        ax.set_title(self.title("Query Count vs Tuple Count"))
        ax.set_xlabel("Tuple Count")
        ax.set_ylabel("Query Count")
        ax.set_xticks(sorted(data["tupleCount"].unique().tolist()))
        self.clean_legend(ax)
        fig.tight_layout()
        fig.savefig(path, dpi=140)
        plt.close(fig)

    def write_size_plot(self, data: pd.DataFrame, path: Path) -> None:
        if data.empty:
            self.write_empty_plot(path, "Table Size vs Tuple Count")
            return

        data = data.copy()
        data["tableSizeMb"] = data["tableSize"] / 1_000_000

        fig, ax = plt.subplots(figsize=(7.2, 4.4))
        sns.lineplot(
            ax=ax,
            data=data,
            x="tupleCount",
            y="tableSizeMb",
            hue="representation",
            hue_order=self.selected_representation_order(),
            marker="o",
            palette=REPRESENTATION_COLORS,
            errorbar=None,
        )
        ax.set_title(self.title("Table Size vs Tuple Count"))
        ax.set_xlabel("Tuple Count")
        ax.set_ylabel("Table Size (MB)")
        ax.set_xticks(sorted(data["tupleCount"].unique().tolist()))
        self.clean_legend(ax)
        fig.tight_layout()
        fig.savefig(path, dpi=140)
        plt.close(fig)

    def clean_legend(self, ax) -> None:
        handles, labels = ax.get_legend_handles_labels()
        legend_items = [
            (handle, label)
            for handle, label in zip(handles, labels)
            if label not in {"representation", "query"}
        ]
        if not legend_items:
            return
        clean_handles, clean_labels = zip(*legend_items)
        ax.legend(clean_handles, clean_labels, fontsize=7, loc="best", title=None)

    def write_empty_plot(self, path: Path, title: str) -> None:
        fig, ax = plt.subplots(figsize=(7.2, 4.4))
        ax.set_title(self.title(title))
        ax.text(
            0.5,
            0.5,
            "No representations selected",
            ha="center",
            va="center",
            transform=ax.transAxes,
        )
        ax.set_axis_off()
        fig.tight_layout()
        fig.savefig(path, dpi=140)
        plt.close(fig)

    def title(self, base: str) -> str:
        return (
            f"{base}\n"
            f"attributeCount = {self.attributeLabel}, sparsity = {self.sparsityLabel}"
        )
