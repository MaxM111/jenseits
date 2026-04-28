import sys
from pathlib import Path

from PyQt6.QtCore import QUrl
from PyQt6.QtGui import QGuiApplication
from PyQt6.QtQml import QQmlApplicationEngine

from plot_controller import PlotController


def main() -> int:
    app = QGuiApplication(sys.argv)

    app_dir = Path(__file__).resolve().parent
    controller = PlotController(
        csv_path=app_dir.parent / "logs" / "log.csv",
        output_dir=app_dir / "generated_plots",
    )

    engine = QQmlApplicationEngine()
    engine.rootContext().setContextProperty("plotController", controller)
    engine.load(QUrl.fromLocalFile(str(app_dir / "main.qml")))

    if not engine.rootObjects():
        return 1

    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
