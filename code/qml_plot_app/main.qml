import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ApplicationWindow {
    id: root
    width: 1280
    height: 820
    visible: true
    title: "Benchmark Plot Explorer"
    color: "#f7f7f4"
    property var backend: typeof plotController === "undefined" ? previewController : plotController

    QtObject {
        id: previewController
        property int attributeIndex: 1
        property int attributeCount: 3
        property string attributeLabel: "10"
        property int sparsityIndex: 1
        property int sparsityCount: 3
        property string sparsityLabel: "0.75"
        property string queryPlotUrl: ""
        property string sizePlotUrl: ""
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

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 18
        spacing: 14

        RowLayout {
            Layout.fillWidth: true
            spacing: 32

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 6

                RowLayout {
                    spacing: 8

                    Label {
                        text: "Attribute Count"
                        color: "#171712"
                        font.pixelSize: 15
                        font.weight: Font.DemiBold
                    }

                    Rectangle {
                        color: "#171712"
                        radius: 4
                        implicitWidth: 48
                        implicitHeight: 26

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
                    Layout.fillWidth: true
                    from: 0
                    to: root.backend.attributeCount - 1
                    stepSize: 1
                    snapMode: Slider.SnapAlways
                    value: root.backend.attributeIndex
                    onMoved: root.backend.setAttributeIndex(Math.round(value))
                }
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 6

                RowLayout {
                    spacing: 8

                    Label {
                        text: "Sparsity"
                        color: "#171712"
                        font.pixelSize: 15
                        font.weight: Font.DemiBold
                    }

                    Rectangle {
                        color: "#171712"
                        radius: 4
                        implicitWidth: 70
                        implicitHeight: 26

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
                    Layout.fillWidth: true
                    from: 0
                    to: root.backend.sparsityCount - 1
                    stepSize: 1
                    snapMode: Slider.SnapAlways
                    value: root.backend.sparsityIndex
                    onMoved: root.backend.setSparsityIndex(Math.round(value))
                }
            }
        }

        Flow {
            Layout.fillWidth: true
            spacing: 12

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
                        radius: 3
                        color: parent.checked ? "#171712" : "white"
                        border.color: "#171712"
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
                        color: "#171712"
                        font.pixelSize: 13
                        leftPadding: representationToggle.indicator.width + representationToggle.spacing
                        verticalAlignment: Text.AlignVCenter
                    }
                    onToggled: root.backend.setRepresentationSelected(modelData, checked)
                }
            }
        }

        TabBar {
            id: plotTabs
            Layout.fillWidth: true

            TabButton {
                text: "Query Count"
            }

            TabButton {
                text: "Table Size"
            }
        }

        StackLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: plotTabs.currentIndex

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: "white"
                border.color: "#d8d8d2"
                radius: 4

                Image {
                    anchors.fill: parent
                    anchors.margins: 10
                    source: root.backend.queryPlotUrl
                    fillMode: Image.PreserveAspectFit
                    cache: false
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: "white"
                border.color: "#d8d8d2"
                radius: 4

                Image {
                    anchors.fill: parent
                    anchors.margins: 10
                    source: root.backend.sizePlotUrl
                    fillMode: Image.PreserveAspectFit
                    cache: false
                }
            }
        }
    }
}
