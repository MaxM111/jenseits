import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Item {
    id: chart
    property var series: []
    property string yLabel: ""
    property string emptyText: "No lines selected"
    property color ink: "#171712"
    property color muted: "#6f746b"
    property color grid: "#e2e5df"
    property color panel: "#fbfbf8"

    function allPoints() {
        var points = []
        for (var i = 0; i < series.length; i++) {
            for (var j = 0; j < series[i].points.length; j++) {
                points.push(series[i].points[j])
            }
        }
        return points
    }

    function bounds() {
        var points = allPoints()
        if (points.length === 0) {
            return null
        }

        var xMin = points[0].x
        var xMax = points[0].x
        var yMax = points[0].y
        for (var i = 0; i < points.length; i++) {
            xMin = Math.min(xMin, points[i].x)
            xMax = Math.max(xMax, points[i].x)
            yMax = Math.max(yMax, points[i].y)
        }
        if (xMin === xMax) {
            xMin -= 1
            xMax += 1
        }
        return {
            "xMin": xMin,
            "xMax": xMax,
            "yMin": 0,
            "yMax": yMax === 0 ? 1 : yMax * 1.1
        }
    }

    function plotArea() {
        return {
            "left": 74,
            "top": 48,
            "right": width - 28,
            "bottom": height - 58
        }
    }

    function xToPixel(value, area, limits) {
        return area.left + (value - limits.xMin) / (limits.xMax - limits.xMin) * (area.right - area.left)
    }

    function yToPixel(value, area, limits) {
        return area.bottom - (value - limits.yMin) / (limits.yMax - limits.yMin) * (area.bottom - area.top)
    }

    function formatNumber(value) {
        if (Math.abs(value) >= 1000) {
            return Math.round(value).toLocaleString(Qt.locale("en_US"))
        }
        return Number(value.toFixed(2)).toString()
    }

    function updateHover(mouseX, mouseY) {
        var limits = bounds()
        if (limits === null) {
            hoverPopup.visible = false
            return
        }

        var area = plotArea()
        var best = null
        var bestDistance = 18

        for (var i = 0; i < series.length; i++) {
            for (var j = 0; j < series[i].points.length; j++) {
                var point = series[i].points[j]
                var px = xToPixel(point.x, area, limits)
                var py = yToPixel(point.y, area, limits)
                var distance = Math.sqrt(Math.pow(mouseX - px, 2) + Math.pow(mouseY - py, 2))
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = {
                        "x": px,
                        "y": py,
                        "label": series[i].name + "\nTuple Count: " + point.x + "\n" + yLabel + ": " + formatNumber(point.y)
                    }
                }
            }
        }

        if (best === null) {
            hoverPopup.visible = false
            return
        }

        hoverLabel.text = best.label
        hoverPopup.x = Math.min(width - hoverPopup.width - 8, best.x + 12)
        hoverPopup.y = Math.max(8, best.y - hoverPopup.height - 12)
        hoverPopup.visible = true
        hoverMarker.x = best.x - hoverMarker.width / 2
        hoverMarker.y = best.y - hoverMarker.height / 2
        hoverMarker.visible = true
    }

    function representationLegend() {
        var entries = []
        var seen = {}
        for (var i = 0; i < series.length; i++) {
            var representation = series[i].representation || series[i].name
            if (!seen[representation]) {
                seen[representation] = true
                entries.push({
                    "name": representation,
                    "color": series[i].color
                })
            }
        }
        return entries
    }

    function hasDashedSeries() {
        for (var i = 0; i < series.length; i++) {
            if (series[i].dashed) {
                return true
            }
        }
        return false
    }

    onSeriesChanged: plotCanvas.requestPaint()
    onWidthChanged: plotCanvas.requestPaint()
    onHeightChanged: plotCanvas.requestPaint()

    Canvas {
        id: plotCanvas
        anchors.fill: parent
        antialiasing: true

        onPaint: {
            var ctx = getContext("2d")
            ctx.reset()
            ctx.clearRect(0, 0, width, height)

            var limits = chart.bounds()
            if (limits === null) {
                ctx.fillStyle = chart.muted
                ctx.font = "15px sans-serif"
                ctx.textAlign = "center"
                ctx.textBaseline = "middle"
                ctx.fillText(chart.emptyText, width / 2, height / 2)
                return
            }

            var area = chart.plotArea()
            ctx.strokeStyle = chart.grid
            ctx.lineWidth = 1
            ctx.fillStyle = chart.muted
            ctx.font = "12px sans-serif"
            ctx.textAlign = "right"
            ctx.textBaseline = "middle"

            var yTicks = 5
            for (var i = 0; i <= yTicks; i++) {
                var yValue = limits.yMin + (limits.yMax - limits.yMin) * i / yTicks
                var y = chart.yToPixel(yValue, area, limits)
                ctx.beginPath()
                ctx.moveTo(area.left, y)
                ctx.lineTo(area.right, y)
                ctx.stroke()
                ctx.fillText(chart.formatNumber(yValue), area.left - 10, y)
            }

            var xValues = []
            var seen = {}
            var points = chart.allPoints()
            for (i = 0; i < points.length; i++) {
                if (!seen[points[i].x]) {
                    seen[points[i].x] = true
                    xValues.push(points[i].x)
                }
            }
            xValues.sort(function(a, b) { return a - b })

            ctx.textAlign = "center"
            ctx.textBaseline = "top"
            for (i = 0; i < xValues.length; i++) {
                var x = chart.xToPixel(xValues[i], area, limits)
                ctx.beginPath()
                ctx.moveTo(x, area.top)
                ctx.lineTo(x, area.bottom)
                ctx.stroke()
                ctx.fillText(xValues[i], x, area.bottom + 12)
            }

            ctx.strokeStyle = chart.ink
            ctx.lineWidth = 1.5
            ctx.beginPath()
            ctx.moveTo(area.left, area.top)
            ctx.lineTo(area.left, area.bottom)
            ctx.lineTo(area.right, area.bottom)
            ctx.stroke()

            ctx.save()
            ctx.translate(16, (area.top + area.bottom) / 2)
            ctx.rotate(-Math.PI / 2)
            ctx.fillStyle = chart.ink
            ctx.font = "13px sans-serif"
            ctx.textAlign = "center"
            ctx.textBaseline = "middle"
            ctx.fillText(chart.yLabel, 0, 0)
            ctx.restore()

            ctx.fillStyle = chart.ink
            ctx.font = "13px sans-serif"
            ctx.textAlign = "center"
            ctx.textBaseline = "top"
            ctx.fillText("Matrix Size", (area.left + area.right) / 2, height - 24)

            for (i = 0; i < series.length; i++) {
                var line = series[i]
                if (line.points.length === 0) {
                    continue
                }
                ctx.strokeStyle = line.color
                ctx.lineWidth = 2.5
                if (ctx.setLineDash) {
                    ctx.setLineDash(line.dashed ? [8, 6] : [])
                }
                ctx.beginPath()
                for (var j = 0; j < line.points.length; j++) {
                    x = chart.xToPixel(line.points[j].x, area, limits)
                    y = chart.yToPixel(line.points[j].y, area, limits)
                    if (j === 0) {
                        ctx.moveTo(x, y)
                    } else {
                        ctx.lineTo(x, y)
                    }
                }
                ctx.stroke()
                if (ctx.setLineDash) {
                    ctx.setLineDash([])
                }

                ctx.fillStyle = line.color
                for (j = 0; j < line.points.length; j++) {
                    x = chart.xToPixel(line.points[j].x, area, limits)
                    y = chart.yToPixel(line.points[j].y, area, limits)
                    ctx.beginPath()
                    ctx.arc(x, y, 4, 0, Math.PI * 2)
                    ctx.fill()
                }
            }
        }
    }

    Column {
        anchors.top: parent.top
        anchors.right: parent.right
        anchors.topMargin: 10
        anchors.rightMargin: 16
        spacing: 7
        visible: chart.series.length > 0

        Flow {
            width: Math.min(520, chart.width - 120)
            spacing: 10

            Repeater {
                model: chart.representationLegend()

                Row {
                    spacing: 5

                    Rectangle {
                        width: 13
                        height: 13
                        radius: 3
                        anchors.verticalCenter: parent.verticalCenter
                        color: modelData.color
                    }

                    Text {
                        text: modelData.name
                        color: chart.ink
                        font.pixelSize: 11
                    }
                }
            }
        }

        Row {
            spacing: 14
            visible: chart.hasDashedSeries()

            Row {
                spacing: 5

                Rectangle {
                    width: 38
                    height: 4
                    radius: 2
                    anchors.verticalCenter: parent.verticalCenter
                    color: chart.ink
                }

                Text {
                    text: "Q1"
                    color: chart.ink
                    font.pixelSize: 11
                }
            }

            Row {
                spacing: 5

                Row {
                    width: 38
                    height: 12
                    spacing: 5
                    anchors.verticalCenter: parent.verticalCenter

                    Repeater {
                        model: 3
                        Rectangle {
                            width: 8
                            height: 4
                            radius: 2
                            anchors.verticalCenter: parent.verticalCenter
                            color: chart.ink
                        }
                    }
                }

                Text {
                    text: "Q2"
                    color: chart.ink
                    font.pixelSize: 11
                }
            }
        }
    }

    Rectangle {
        id: hoverMarker
        width: 12
        height: 12
        radius: 6
        color: "white"
        border.color: chart.ink
        border.width: 2
        visible: false
    }

    Rectangle {
        id: hoverPopup
        visible: false
        color: "#171712"
        radius: 6
        width: hoverLabel.implicitWidth + 18
        height: hoverLabel.implicitHeight + 14
        z: 10

        Text {
            id: hoverLabel
            anchors.centerIn: parent
            color: "white"
            font.pixelSize: 12
            lineHeight: 1.15
        }
    }

    MouseArea {
        anchors.fill: parent
        hoverEnabled: true
        onPositionChanged: chart.updateHover(mouse.x, mouse.y)
        onExited: {
            hoverPopup.visible = false
            hoverMarker.visible = false
        }
    }
}
