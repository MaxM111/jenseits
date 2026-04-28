# QML Plot App

Plain PyQt6/QML app for exploring the benchmark CSV.

Run from this directory:

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python main.py
```

The QML layout is in `main.qml`. The app reads `../logs/log.csv` and writes temporary plot PNGs to `generated_plots/`.

`main.qml` includes a small preview fallback so it can be opened without the Python backend in QML-aware design tools. Classic Qt Designer usually opens `.ui` files; for QML use Qt Creator, Qt Design Studio, or `qmlscene` when available.
