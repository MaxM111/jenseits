from pathlib import Path

import pandas as pd
from PyQt6.QtCore import QObject, pyqtProperty, pyqtSignal, pyqtSlot


REPRESENTATION_COLORS = {
    "approach0": "#d31313",
    "approach1": "#1f77b4",
    "approach2": "#2ca02c",
}


class PlotController(QObject):
    plotsChanged = pyqtSignal()
    controlsChanged = pyqtSignal()
    representationsChanged = pyqtSignal()

    def __init__(self, csv_path: Path) -> None:
        super().__init__()
        self.csv_path = csv_path

        self.df = pd.read_csv(self.csv_path)
        self.sparsity_values = sorted(self.df["sparsity"].unique().tolist())
        self.representation_values = self.df["Approach"].drop_duplicates().tolist()
        self.selected_representations = set()
        self.sparsity_index = 1 if len(self.sparsity_values) > 1 else 0
        self.query_series = []
        self.size_series = []
        self.update_plots()

    @pyqtProperty(int, notify=controlsChanged)
    def attributeIndex(self) -> int:
        return self.attribute_index

    @pyqtProperty(int, notify=controlsChanged)
    def sparsityIndex(self) -> int:
        return self.sparsity_index

    @pyqtProperty(int, constant=True)
    def sparsityCount(self) -> int:
        return len(self.sparsity_values)

    @pyqtProperty(str, notify=controlsChanged)
    def sparsityLabel(self) -> str:
        return str(self.sparsity_values[self.sparsity_index])

    @pyqtProperty("QVariantList", notify=plotsChanged)
    def querySeries(self) -> list[dict[str, object]]:
        return self.query_series

    @pyqtProperty("QVariantList", notify=plotsChanged)
    def sizeSeries(self) -> list[dict[str, object]]:
        return self.size_series

    @pyqtProperty("QVariantList", constant=True)
    def representationNames(self) -> list[str]:
        return self.representation_values

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
        sparsity = self.sparsity_values[self.sparsity_index]
        return self.df[
            (self.df["sparsity"] == sparsity)
            & (self.df["Approach"].isin(self.selected_representations))
        ]

    def selected_representation_order(self) -> list[str]:
        return [
            representation
            for representation in self.representation_values
            if representation in self.selected_representations
        ]

    def update_plots(self) -> None:
        data = self.filtered_data()
        self.query_series = self.build_query_series(data)
        self.plotsChanged.emit()

    def build_query_series(self, data: pd.DataFrame) -> list[dict[str, object]]:
        series = []
        for representation in self.selected_representation_order():
            representation_data = data[data["Approach"] == representation]
            series.append(
                self.series_from_rows(
                    name=f"{representation}",
                    representation=representation,
                    line_type="Q1",
                    color=REPRESENTATION_COLORS[representation],
                    rows=representation_data,
                    value_column="queryCount",
                    dashed=False,
                )
            )
        return series

    def series_from_rows(
        self,
        name: str,
        representation: str,
        line_type: str,
        color: str,
        rows: pd.DataFrame,
        value_column: str,
        dashed: bool,
    ) -> dict[str, object]:
        points = [
            {
                "x": float(row["matrixLength"]),
                "y": float(row[value_column]),
            }
            for _, row in rows.sort_values("matrixLength").iterrows()
        ]
        return {
            "name": name,
            "Approach": representation,
            "lineType": line_type,
            "color": color,
            "dashed": dashed,
            "points": points,
        }
