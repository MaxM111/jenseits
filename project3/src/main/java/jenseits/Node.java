package jenseits;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Node {
    private static long idCounter = 0;

    private long id;
    private String tag;
    private NavigableMap<String, String> attributes;
    private Node parent;
    private List<Node> children;

    // null if it is composite, text value if it is atomic
    private String value;

    /**
     * Construct a new Node.
     *
     * @param value the value of the atomic element.
     */
    public Node(String value) {
        this.tag = value;
        this.parent = null;
        this.id = idCounter++;
        this.value = value;
    }

    /**
     * Construct a new Node.
     *
     * @param tag    the identifier of the element.
     * @param parent the parent of the element, set to null if it is the root.
     */
    public Node(String tag, Node parent) {
        this.tag = tag;
        this.parent = parent;
        this.id = idCounter++;
        this.children = new ArrayList<>();
        this.attributes = new TreeMap<>();
        this.value = null;
    }

    /**
     * Append a new child to the element.
     *
     * @param child the child to append.
     * @return the element the child was appended to.
     */
    public Node appendChild(Node child) {
        children.add(child);
        return this;
    }

    /**
     * Add an attribute to the element.
     *
     * @param identifier the name of the attribute,
     * @param value      the (string) value of the attribute.
     * @return the element the attribute was added to.
     */
    public Node addAttribute(String identifier, String value) {
        this.attributes.put(identifier, value);
        return this;
    }

    /*
     * Converts the XML subtree into the edge model.
     */
    public void toEdgeModel(Connection conn) {

    }

    @Override
    public String toString() {
        String s = "";

        if (value == null) {
            s += "<" + this.tag;
            for (var key : this.attributes.keySet()) {
                s += " " + key + "=\"" + this.attributes.get(key) + '"';
            }
            s += ">";
            if (this.tag.equals("article") || this.tag.equals("bib") || this.tag.equals("inproceedings")) {
                s += "\n";
            }

            for (var child : children) {
                s += child.toString()
                        .lines()
                        .map(line -> !line.strip().startsWith("<") ? line : "  " + line + "\n")
                        .collect(Collectors.joining());
            }

            s += "</" + this.tag + ">\n";
        } else {
            s += value;
        }

        return s;
    }

    /*
     * Construct a tree of Nodes from the given XML source.
     *
     * @param xmlPath the path to the xml file.
     * 
     * @return the root of the resulting element tree.
     */
    public static Node createFromXml(String xmlPath) {
        return null;
    }
}
