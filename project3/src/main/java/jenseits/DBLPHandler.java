package jenseits;

import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DBLPHandler extends DefaultHandler {
    // special tags that require special handling
    private static final String BIB = "bib";
    private static final String YEAR = "year";
    private static final String EE = "ee";

    private Node root;
    private StringBuilder valueBuilder;

    private Node currentNode;
    private String temp;
    private String currentVenue;
    private String currentYear;
    private final Map<String, Node> venueNodes = new HashMap<>();
    private final Map<String, Node> yearNodes = new HashMap<>();
    private String currentPublication; // used to match the closing tag of a publication

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (valueBuilder == null) {
            valueBuilder = new StringBuilder();
        } else {
            valueBuilder.append(ch, start, length);
        }
    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void startElement(String uri, String lName, String qName, Attributes attributes) throws SAXException {
        // if there is a key attribute, we assume it is a publication
        var key = attributes.getValue("key");
        if (key != null) {
            currentNode = new Node(qName, root);
            var keyParts = key.split("/");
            currentVenue = keyParts[1];
            currentNode.addAttribute("key", key);
            currentYear = null;
            currentPublication = qName;
            return;
        }

        switch (qName) {
            case BIB -> root = new Node("bib", null);
            case EE -> {
                valueBuilder = new StringBuilder();
                var type = attributes.getValue("type");
                temp = type;
            }
            default -> valueBuilder = new StringBuilder();
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals(currentPublication)) {
            appendCurrentPublication();
            currentPublication = null;
            return;
        }

        switch (qName) {
            case BIB -> {
            }
            case EE -> {
                var node = new Node(EE, currentNode);
                currentNode.appendChild(node);
                node.setContent(valueBuilder.toString());
                if (temp != null) {
                    node.addAttribute("type", temp);
                }
            }
            default -> {
                var node = new Node(qName, currentNode);
                currentNode.appendChild(node);
                node.setContent(valueBuilder.toString());
                if (qName.equals(YEAR)) {
                    currentYear = valueBuilder.toString();
                }
            }
        }
    }

    private void appendCurrentPublication() {
        if (currentVenue == null || currentYear == null) {
            IO.println("warning: publication venue or year is missing. Discarding publication...");
            return;
        }

        var venueNode = venueNodes.get(currentVenue);
        if (venueNode == null) {
            venueNode = new Node("venue", root);
            venueNode.addAttribute("name", currentVenue);
            root.appendChild(venueNode);
            venueNodes.put(currentVenue, venueNode);
        }

        var yearKey = currentVenue + "_" + currentYear;
        var yearNode = yearNodes.get(yearKey);
        if (yearNode == null) {
            yearNode = new Node("publishingYear", venueNode);
            yearNode.addAttribute("value", currentYear);
            venueNode.appendChild(yearNode);
            yearNodes.put(yearKey, yearNode);
        }

        currentNode.setParent(yearNode);
        yearNode.appendChild(currentNode);
    }

    public Node getTree() {
        return this.root;
    }
}
