package jenseits;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DBLPHandler extends DefaultHandler {
    private static final String BIB = "bib";
    private static final String ARTICLE = "article";
    private static final String INPROCEEDINGS = "inproceedings";

    private static final String AUTHOR = "author";
    private static final String TITLE = "title";
    private static final String PAGES = "pages";
    private static final String YEAR = "year";
    private static final String BOOK_TITLE = "booktitle";
    private static final String VOLUME = "volume";
    private static final String JOURNAL = "journal";
    private static final String NUMBER = "number";
    private static final String EE = "ee";
    private static final String CROSSREF = "crossref";
    private static final String URL = "url";

    private Node root;
    private StringBuilder valueBuilder;

    private Node currentNode;
    private String temp;

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
        switch (qName) {
            case BIB -> {
                root = new Node("bib", null);
            }
            case ARTICLE -> {
                currentNode = new Node("article", root);
                var key = attributes.getValue("key");
                if (key == null) {
                    IO.println("warning: key attribute is missing");
                }
                currentNode.addAttribute("key", key);
            }
            case INPROCEEDINGS -> {
                currentNode = new Node("article", root);
                var key = attributes.getValue("key");
                if (key == null) {
                    IO.println("warning: key attribute is missing");
                }
                currentNode.addAttribute("key", key);
            }
            case AUTHOR, TITLE, PAGES, YEAR,
                    BOOK_TITLE, VOLUME, JOURNAL,
                    NUMBER, CROSSREF, URL -> {
                valueBuilder = new StringBuilder();
            }
            case EE -> {
                valueBuilder = new StringBuilder();
                var type = attributes.getValue("type");
                temp = type;
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (qName) {
            case BIB -> {
            }
            case ARTICLE -> {
                root.appendChild(currentNode);
            }
            case INPROCEEDINGS -> {
                root.appendChild(currentNode);
            }

            case AUTHOR, TITLE, PAGES, YEAR,
                    BOOK_TITLE, VOLUME, JOURNAL,
                    NUMBER, CROSSREF, URL -> {
                var tag = switch (qName) {
                    case AUTHOR -> "author";
                    case TITLE -> "title";
                    case PAGES -> "pages";
                    case YEAR -> "year";
                    case BOOK_TITLE -> "booktitle";
                    case VOLUME -> "volume";
                    case JOURNAL -> "journal";
                    case NUMBER -> "number";
                    case EE -> "ee";
                    case CROSSREF -> "crossref";
                    case URL -> "url";
                    default -> throw new SAXException("unexpected tag when parsing xml file");
                };
                var node = new Node(tag, currentNode);
                currentNode.appendChild(node);
                node.appendChild(new Node(valueBuilder.toString()));
            }
            case EE -> {
                var node = new Node("ee", currentNode);
                currentNode.appendChild(node);
                node.appendChild(new Node(valueBuilder.toString()));
                if (temp != null) {
                    node.addAttribute("type", temp);
                }
            }
        }
    }
}
