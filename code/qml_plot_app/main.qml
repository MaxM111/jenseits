import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ApplicationWindow {
    id: root
    width: 1280
    height: 820
    visible: true
    title: "Benchmark Plot Explorer"
    color: "#eef0ed"
    property var backend: typeof plotController === "undefined" ? previewController : plotController
    readonly property color ink: "#171712"
    readonly property color muted: "#6f746b"
    readonly property color panel: "#fbfbf8"
    readonly property color line: "#d6d8d1"
    readonly property color accent: "#2563eb"

    QtObject {
        id: previewController
        property int attributeIndex: 1
        property int attributeCount: 3
        property string attributeLabel: "10"
        property int sparsityIndex: 1
        property int sparsityCount: 3
        property string sparsityLabel: "0.75"
        property var querySeries: []
        property var sizeSeries: []
        property var representationNames: [
            "Horizontal",
            "Vertical",
            "Vertical Optimized",
            "Vertical Functions",
            "Vertical Functions (Hash Index)",
            "Vertical Functions (Batch)"
        ]
        property var selectedRepresentations: ({
            "Horizontal": false,
            "Vertical": false,
            "Vertical Optimized": false,
            "Vertical Functions": false,
            "Vertical Functions (Hash Index)": false,
            "Vertical Functions (Batch)": false
        })

        function setAttributeIndex(index) {
            attributeIndex = index
        }

        function setSparsityIndex(index) {
            sparsityIndex = index
        }

        function isRepresentationSelected(representation) {
            return selectedRepresentations[representation] === true
        }

        function setRepresentationSelected(representation, selected) {
            selectedRepresentations[representation] = selected
        }
    }

    RowLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 18

        Rectangle {
            Layout.preferredWidth: 340
            Layout.fillHeight: true
            color: root.panel
            radius: 8
            border.color: root.line
            border.width: 1

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 18
                spacing: 18

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 8

                        Label {
                            Layout.fillWidth: true
                            text: "Attribute Count"
                            color: root.ink
                            font.pixelSize: 14
                            font.weight: Font.DemiBold
                        }

                        Rectangle {
                            color: root.ink
                            radius: 5
                            implicitWidth: 52
                            implicitHeight: 28

                            Label {
                                anchors.centerIn: parent
                                text: root.backend.attributeLabel
                                color: "white"
                                font.pixelSize: 15
                                font.weight: Font.Bold
                            }
                        }
                    }

                    Slider {
                        id: attributeSlider
                        Layout.fillWidth: true
                        from: 0
                        to: root.backend.attributeCount - 1
                        stepSize: 1
                        snapMode: Slider.SnapAlways
                        value: root.backend.attributeIndex
                        onMoved: root.backend.setAttributeIndex(Math.round(value))
                        background: Rectangle {
                            x: attributeSlider.leftPadding
                            y: attributeSlider.topPadding + attributeSlider.availableHeight / 2 - height / 2
                            width: attributeSlider.availableWidth
                            height: 6
                            radius: 3
                            color: "#d8ddd4"

                            Rectangle {
                                width: attributeSlider.visualPosition * parent.width
                                height: parent.height
                                radius: 3
                                color: root.accent
                            }
                        }
                        handle: Rectangle {
                            x: attributeSlider.leftPadding + attributeSlider.visualPosition * (attributeSlider.availableWidth - width)
                            y: attributeSlider.topPadding + attributeSlider.availableHeight / 2 - height / 2
                            implicitWidth: 20
                            implicitHeight: 20
                            radius: 10
                            color: "white"
                            border.color: root.accent
                            border.width: 3
                        }
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 8

                        Label {
                            Layout.fillWidth: true
                            text: "Sparsity"
                            color: root.ink
                            font.pixelSize: 14
                            font.weight: Font.DemiBold
                        }

                        Rectangle {
                            color: root.ink
                            radius: 5
                            implicitWidth: 76
                            implicitHeight: 28

                            Label {
                                anchors.centerIn: parent
                                text: root.backend.sparsityLabel
                                color: "white"
                                font.pixelSize: 15
                                font.weight: Font.Bold
                            }
                        }
                    }

                    Slider {
                        id: sparsitySlider
                        Layout.fillWidth: true
                        from: 0
                        to: root.backend.sparsityCount - 1
                        stepSize: 1
                        snapMode: Slider.SnapAlways
                        value: root.backend.sparsityIndex
                        onMoved: root.backend.setSparsityIndex(Math.round(value))
                        background: Rectangle {
                            x: sparsitySlider.leftPadding
                            y: sparsitySlider.topPadding + sparsitySlider.availableHeight / 2 - height / 2
                            width: sparsitySlider.availableWidth
                            height: 6
                            radius: 3
                            color: "#d8ddd4"

                            Rectangle {
                                width: sparsitySlider.visualPosition * parent.width
                                height: parent.height
                                radius: 3
                                color: root.accent
                            }
                        }
                        handle: Rectangle {
                            x: sparsitySlider.leftPadding + sparsitySlider.visualPosition * (sparsitySlider.availableWidth - width)
                            y: sparsitySlider.topPadding + sparsitySlider.availableHeight / 2 - height / 2
                            implicitWidth: 20
                            implicitHeight: 20
                            radius: 10
                            color: "white"
                            border.color: root.accent
                            border.width: 3
                        }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    implicitHeight: 1
                    color: root.line
                }

                Label {
                    text: "Representations"
                    color: root.ink
                    font.pixelSize: 14
                    font.weight: Font.DemiBold
                }

                Flow {
                    Layout.fillWidth: true
                    spacing: 10

                    Repeater {
                        model: root.backend.representationNames

                        CheckBox {
                            id: representationToggle
                            text: modelData
                            checked: root.backend.isRepresentationSelected(modelData)
                            font.pixelSize: 13
                            indicator: Rectangle {
                                implicitWidth: 18
                                implicitHeight: 18
                                x: parent.leftPadding
                                y: parent.topPadding + (parent.availableHeight - height) / 2
                                radius: 4
                                color: parent.checked ? root.accent : "white"
                                border.color: parent.checked ? root.accent : root.ink
                                border.width: 2

                                Text {
                                    anchors.centerIn: parent
                                    text: parent.parent.checked ? "✓" : ""
                                    color: "white"
                                    font.pixelSize: 14
                                    font.weight: Font.Bold
                                }
                            }
                            contentItem: Text {
                                text: representationToggle.text
                                color: root.ink
                                font.pixelSize: 13
                                leftPadding: representationToggle.indicator.width + representationToggle.spacing
                                verticalAlignment: Text.AlignVCenter
                            }
                            onToggled: root.backend.setRepresentationSelected(modelData, checked)
                        }
                    }
                }
            }
        }

        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 12

            RowLayout {
                Layout.fillWidth: true
                spacing: 12

                TabBar {
                    id: plotTabs
                    Layout.preferredWidth: 320
                    implicitHeight: 40
                    background: Rectangle {
                        color: "#dde2dc"
                        radius: 8
                    }

                    TabButton {
                        id: queryTab
                        text: "Query Count"
                        contentItem: Text {
                            text: queryTab.text
                            color: plotTabs.currentIndex === 0 ? "white" : root.ink
                            font.pixelSize: 14
                            font.weight: Font.DemiBold
                            horizontalAlignment: Text.AlignHCenter
                            verticalAlignment: Text.AlignVCenter
                        }
                        background: Rectangle {
                            color: plotTabs.currentIndex === 0 ? root.ink : "transparent"
                            radius: 7
                        }
                    }

                    TabButton {
                        id: sizeTab
                        text: "Table Size"
                        contentItem: Text {
                            text: sizeTab.text
                            color: plotTabs.currentIndex === 1 ? "white" : root.ink
                            font.pixelSize: 14
                            font.weight: Font.DemiBold
                            horizontalAlignment: Text.AlignHCenter
                            verticalAlignment: Text.AlignVCenter
                        }
                        background: Rectangle {
                            color: plotTabs.currentIndex === 1 ? root.ink : "transparent"
                            radius: 7
                        }
                    }
                }
            }

            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: plotTabs.currentIndex

                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    color: root.panel
                    border.color: root.line
                    border.width: 1
                    radius: 8

                    LineChart {
                        anchors.fill: parent
                        anchors.margins: 18
                        series: root.backend.querySeries
                        yLabel: "Query Count"
                        emptyText: "Select representations to show query counts"
                        ink: root.ink
                        muted: root.muted
                        grid: root.line
                        panel: root.panel
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    color: root.panel
                    border.color: root.line
                    border.width: 1
                    radius: 8

                    LineChart {
                        anchors.fill: parent
                        anchors.margins: 18
                        series: root.backend.sizeSeries
                        yLabel: "Table Size (MB)"
                        emptyText: "Select representations to show table sizes"
                        ink: root.ink
                        muted: root.muted
                        grid: root.line
                        panel: root.panel
                    }
                }
            }
        }
    }
}
