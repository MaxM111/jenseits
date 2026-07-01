#!/usr/bin/env python3
import csv
import math
from collections import defaultdict
from pathlib import Path
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1]
CSV_PATH = ROOT / "logs" / "phase3_benchmark.csv"
OUT_DIR = ROOT / "plots"

COLORS = {
    "edge": "#4C78A8",
    "xpath_accel": "#F58518",
    "xpath_reduced": "#54A24B",
    "xpath_one_axis": "#B279A2",
}

LABELS = {
    "edge": "EDGE model",
    "xpath_accel": "XPath accelerator",
    "xpath_reduced": "Reduced window",
    "xpath_one_axis": "One-axis accelerator",
    "ancestor": "Ancestor",
    "descendant": "Descendant",
    "P/F-sibling": "P/F siblings",
}


def read_rows():
    with CSV_PATH.open(newline="", encoding="utf-8") as fh:
        rows = []
        for row in csv.DictReader(fh):
            row["venue_count"] = int(row["venue_count"])
            row["node_count"] = int(row["node_count"])
            row["edge_count"] = int(row["edge_count"])
            row["avg_ms"] = float(row["avg_ms"])
            rows.append(row)
        return rows


def nice_ticks_log(min_value, max_value):
    start = math.floor(math.log10(max(min_value, 0.001)))
    end = math.ceil(math.log10(max_value))
    ticks = []
    for power in range(start, end + 1):
        for multiplier in (1, 2, 5):
            value = multiplier * (10 ** power)
            if min_value <= value <= max_value:
                ticks.append(value)
    return ticks


def fmt_ms(value):
    if value < 1:
        return f"{value:.2f}"
    if value < 10:
        return f"{value:.1f}"
    return f"{value:.0f}"


def svg_text(x, y, text, size=13, anchor="middle", weight="normal", color="#1F2933", rotate=None):
    transform = f' transform="rotate({rotate} {x} {y})"' if rotate is not None else ""
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" font-family="Arial, sans-serif" '
        f'font-size="{size}" font-weight="{weight}" fill="{color}" '
        f'text-anchor="{anchor}"{transform}>{escape(str(text))}</text>'
    )


def line_chart(rows, axis, approaches, title, filename, y_log=True):
    data = defaultdict(list)
    for row in rows:
        if row["axis"] == axis and row["approach"] in approaches:
            data[row["approach"]].append(row)

    for values in data.values():
        values.sort(key=lambda item: item["node_count"])

    width, height = 980, 560
    left, right, top, bottom = 90, 260, 70, 105
    plot_w = width - left - right
    plot_h = height - top - bottom

    all_points = [row for values in data.values() for row in values]
    min_x = min(row["node_count"] for row in all_points)
    max_x = max(row["node_count"] for row in all_points)
    min_y = min(row["avg_ms"] for row in all_points)
    max_y = max(row["avg_ms"] for row in all_points)
    if y_log:
        min_y = 0.5 * 10 ** math.floor(math.log10(max(min_y, 0.001)))
        max_y = 2 * 10 ** math.ceil(math.log10(max_y))
    else:
        min_y = 0
        max_y *= 1.15

    def x_pos(value):
        return left + ((value - min_x) / (max_x - min_x)) * plot_w

    def y_pos(value):
        if y_log:
            lo = math.log10(min_y)
            hi = math.log10(max_y)
            return top + (hi - math.log10(value)) / (hi - lo) * plot_h
        return top + (max_y - value) / (max_y - min_y) * plot_h

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#FFFFFF"/>',
        svg_text(width / 2, 32, title, size=22, weight="700"),
        svg_text(width / 2, 54, "Average runtime over 100 measured runs", size=13, color="#52606D"),
        f'<rect x="{left}" y="{top}" width="{plot_w}" height="{plot_h}" fill="#FBFCFD" stroke="#D9E2EC"/>',
    ]

    x_ticks = sorted({row["node_count"] for row in all_points})
    for value in x_ticks:
        x = x_pos(value)
        parts.append(f'<line x1="{x:.1f}" y1="{top}" x2="{x:.1f}" y2="{top + plot_h}" stroke="#E5EAF0"/>')
        parts.append(svg_text(
            x - 2,
            top + plot_h + 24,
            f'{value / 1_000_000:.2f}M',
            size=12,
            anchor="end",
            rotate=-35,
        ))

    y_ticks = nice_ticks_log(min_y, max_y) if y_log else []
    for value in y_ticks:
        y = y_pos(value)
        parts.append(f'<line x1="{left}" y1="{y:.1f}" x2="{left + plot_w}" y2="{y:.1f}" stroke="#E5EAF0"/>')
        parts.append(svg_text(left - 12, y + 4, fmt_ms(value), size=12, anchor="end"))

    parts.append(f'<line x1="{left}" y1="{top + plot_h}" x2="{left + plot_w}" y2="{top + plot_h}" stroke="#334E68"/>')
    parts.append(f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top + plot_h}" stroke="#334E68"/>')
    parts.append(svg_text(left + plot_w / 2, height - 24, "Dataset size (nodes)", size=13))
    parts.append(svg_text(25, top + plot_h / 2, "Average runtime (ms, log scale)", size=13, rotate=-90))

    for approach in approaches:
        values = data.get(approach, [])
        if not values:
            continue
        points = [(x_pos(row["node_count"]), y_pos(row["avg_ms"])) for row in values]
        point_str = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
        color = COLORS[approach]
        parts.append(f'<polyline points="{point_str}" fill="none" stroke="{color}" stroke-width="3"/>')
        for row, (x, y) in zip(values, points):
            parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="4.5" fill="{color}" stroke="#FFFFFF" stroke-width="1.5"/>')
            parts.append(svg_text(x, y - 10, fmt_ms(row["avg_ms"]), size=10, color=color))

    legend_x = left + plot_w + 34
    legend_y = top + 16
    parts.append(svg_text(legend_x, legend_y - 16, "Approach", size=13, anchor="start", weight="700"))
    for idx, approach in enumerate(approaches):
        if approach not in data:
            continue
        y = legend_y + idx * 28
        color = COLORS[approach]
        parts.append(f'<line x1="{legend_x}" y1="{y}" x2="{legend_x + 24}" y2="{y}" stroke="{color}" stroke-width="4"/>')
        parts.append(f'<circle cx="{legend_x + 12}" cy="{y}" r="4.5" fill="{color}" stroke="#FFFFFF" stroke-width="1.5"/>')
        parts.append(svg_text(legend_x + 34, y + 4, LABELS[approach], size=12, anchor="start"))

    parts.append("</svg>")
    (OUT_DIR / filename).write_text("\n".join(parts), encoding="utf-8")


def small_multiples(rows):
    axes = ["ancestor", "descendant", "P/F-sibling"]
    approaches = ["edge", "xpath_accel", "xpath_reduced", "xpath_one_axis"]
    width, height = 1120, 760
    margin_x, gap = 78, 34
    top, bottom = 92, 125
    panel_w = (width - 2 * margin_x - 2 * gap) / 3
    panel_h = height - top - bottom

    all_avg = [row["avg_ms"] for row in rows]
    min_y = 0.5 * 10 ** math.floor(math.log10(max(min(all_avg), 0.001)))
    max_y = 2 * 10 ** math.ceil(math.log10(max(all_avg)))

    x_values = sorted({row["node_count"] for row in rows})
    min_x, max_x = min(x_values), max(x_values)

    def y_pos(value):
        lo = math.log10(min_y)
        hi = math.log10(max_y)
        return top + (hi - math.log10(value)) / (hi - lo) * panel_h

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#FFFFFF"/>',
        svg_text(width / 2, 34, "XPath Axis Benchmark Overview", size=23, weight="700"),
        svg_text(width / 2, 58, "Average runtime in milliseconds; shared log-scale y-axis", size=13, color="#52606D"),
    ]

    ticks = nice_ticks_log(min_y, max_y)
    for panel_index, axis in enumerate(axes):
        left = margin_x + panel_index * (panel_w + gap)

        def x_pos(value):
            return left + ((value - min_x) / (max_x - min_x)) * panel_w

        parts.append(f'<rect x="{left:.1f}" y="{top}" width="{panel_w:.1f}" height="{panel_h}" fill="#FBFCFD" stroke="#D9E2EC"/>')
        parts.append(svg_text(left + panel_w / 2, top - 18, LABELS[axis], size=16, weight="700"))

        for value in x_values:
            x = x_pos(value)
            parts.append(f'<line x1="{x:.1f}" y1="{top}" x2="{x:.1f}" y2="{top + panel_h}" stroke="#EDF2F7"/>')
            if panel_index == 1:
                parts.append(svg_text(
                    x - 2,
                    top + panel_h + 24,
                    f'{value / 1_000_000:.2f}M',
                    size=11,
                    anchor="end",
                    rotate=-35,
                ))

        for value in ticks:
            y = y_pos(value)
            parts.append(f'<line x1="{left:.1f}" y1="{y:.1f}" x2="{left + panel_w:.1f}" y2="{y:.1f}" stroke="#E5EAF0"/>')
            if panel_index == 0:
                parts.append(svg_text(left - 10, y + 4, fmt_ms(value), size=11, anchor="end"))

        panel_rows = [row for row in rows if row["axis"] == axis]
        by_approach = defaultdict(list)
        for row in panel_rows:
            by_approach[row["approach"]].append(row)
        for approach in approaches:
            values = sorted(by_approach.get(approach, []), key=lambda row: row["node_count"])
            if not values:
                continue
            point_str = " ".join(f'{x_pos(row["node_count"]):.1f},{y_pos(row["avg_ms"]):.1f}' for row in values)
            color = COLORS[approach]
            parts.append(f'<polyline points="{point_str}" fill="none" stroke="{color}" stroke-width="2.8"/>')
            for row in values:
                parts.append(f'<circle cx="{x_pos(row["node_count"]):.1f}" cy="{y_pos(row["avg_ms"]):.1f}" r="3.8" fill="{color}" stroke="#FFFFFF" stroke-width="1"/>')

    parts.append(svg_text(width / 2, height - 32, "Dataset size (nodes)", size=13))
    parts.append(svg_text(23, top + panel_h / 2, "Average runtime (ms)", size=13, rotate=-90))

    legend_x = margin_x
    legend_y = height - 62
    for idx, approach in enumerate(approaches):
        x = legend_x + idx * 245
        color = COLORS[approach]
        parts.append(f'<line x1="{x}" y1="{legend_y}" x2="{x + 28}" y2="{legend_y}" stroke="{color}" stroke-width="4"/>')
        parts.append(f'<circle cx="{x + 14}" cy="{legend_y}" r="4.5" fill="{color}" stroke="#FFFFFF" stroke-width="1"/>')
        parts.append(svg_text(x + 38, legend_y + 4, LABELS[approach], size=12, anchor="start"))

    parts.append("</svg>")
    (OUT_DIR / "benchmark_all_axes.svg").write_text("\n".join(parts), encoding="utf-8")


def dataset_size_chart(rows):
    by_venue = {}
    for row in rows:
        by_venue[row["venue_count"]] = row["node_count"]
    items = sorted(by_venue.items())
    width, height = 760, 430
    left, right, top, bottom = 88, 44, 66, 78
    plot_w = width - left - right
    plot_h = height - top - bottom
    max_nodes = max(value for _, value in items) * 1.1
    bar_w = plot_w / len(items) * 0.55

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#FFFFFF"/>',
        svg_text(width / 2, 32, "Benchmark Dataset Sizes", size=22, weight="700"),
        svg_text(width / 2, 54, "Parsed DBLP subset size by number of venue rules", size=13, color="#52606D"),
        f'<rect x="{left}" y="{top}" width="{plot_w}" height="{plot_h}" fill="#FBFCFD" stroke="#D9E2EC"/>',
    ]

    for tick in range(0, 3_000_000, 500_000):
        if tick > max_nodes:
            continue
        y = top + (max_nodes - tick) / max_nodes * plot_h
        parts.append(f'<line x1="{left}" y1="{y:.1f}" x2="{left + plot_w}" y2="{y:.1f}" stroke="#E5EAF0"/>')
        parts.append(svg_text(left - 10, y + 4, f"{tick / 1_000_000:.1f}M", size=11, anchor="end"))

    for idx, (venue_count, node_count) in enumerate(items):
        cx = left + (idx + 0.5) * plot_w / len(items)
        bar_h = node_count / max_nodes * plot_h
        y = top + plot_h - bar_h
        parts.append(f'<rect x="{cx - bar_w / 2:.1f}" y="{y:.1f}" width="{bar_w:.1f}" height="{bar_h:.1f}" fill="#4C78A8"/>')
        parts.append(svg_text(cx, y - 8, f"{node_count / 1_000_000:.2f}M", size=11, color="#334E68"))
        parts.append(svg_text(cx, top + plot_h + 24, str(venue_count), size=12))

    parts.append(svg_text(width / 2, height - 24, "Venue rules", size=13))
    parts.append(svg_text(25, top + plot_h / 2, "Nodes", size=13, rotate=-90))
    parts.append("</svg>")
    (OUT_DIR / "benchmark_dataset_sizes.svg").write_text("\n".join(parts), encoding="utf-8")


def export_pdfs():
    try:
        import cairosvg
    except ImportError as error:
        raise SystemExit(
            "PDF export requires CairoSVG. Install it with: "
            "python3 -m pip install -r scripts/requirements.txt"
        ) from error

    for svg_path in sorted(OUT_DIR.glob("benchmark_*.svg")):
        pdf_path = svg_path.with_suffix(".pdf")
        cairosvg.svg2pdf(
            url=str(svg_path),
            write_to=str(pdf_path),
        )
        print(f"Wrote {pdf_path.relative_to(ROOT)}")


def main():
    OUT_DIR.mkdir(exist_ok=True)
    rows = read_rows()

    line_chart(
        rows,
        "descendant",
        ["edge", "xpath_accel", "xpath_reduced", "xpath_one_axis"],
        "Descendant Axis Runtime",
        "benchmark_descendant.svg",
    )
    line_chart(
        rows,
        "ancestor",
        ["edge", "xpath_accel", "xpath_reduced"],
        "Ancestor Axis Runtime",
        "benchmark_ancestor.svg",
    )
    line_chart(
        rows,
        "P/F-sibling",
        ["edge", "xpath_accel", "xpath_reduced"],
        "Preceding/Following-Sibling Runtime",
        "benchmark_following_sibling.svg",
    )
    small_multiples(rows)
    dataset_size_chart(rows)
    export_pdfs()


if __name__ == "__main__":
    main()
