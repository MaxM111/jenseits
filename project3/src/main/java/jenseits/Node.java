package jenseits;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Node {
    private static long idCounter = 0;

    private long id;
    private String tag;
    private NavigableMap<String, String> attributes;
    private Node parent;
    private List<Node> children;
    private String content; // can be null

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
        this.content = null;
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

    public void removeChild(Node child) {
        children.remove(child);
        child.parent = null;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Optional<String> getContent() {
        return Optional.ofNullable(this.content);
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

    public Node getParent() {
        return this.parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public long getID() {
        return this.id;
    }

    /*
     * Converts the XML subtree into the edge model.
     */
    public void toEdgeModel(Connection conn) {

    }

    @Override
    public String toString() {
        String s = "";

        s += "<" + this.tag;
        for (var key : this.attributes.keySet()) {
            s += " " + key + "=\"" + this.attributes.get(key) + '"';
        }
        s += ">";
        if (this.tag.equals("article") || this.tag.equals("bib") || this.tag.equals("inproceedings")) {
            s += "\n";
        }

        if (this.content != null) {
            s += this.content;
        }

        for (var child : children) {
            s += child.toString()
                    .lines()
                    .map(line -> !line.strip().startsWith("<") ? line : "  " + line + "\n")
                    .collect(Collectors.joining());
        }

        s += "</" + this.tag + ">\n";

        return s;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Node) {
            return ((Node) other).getID() == this.id;
        } else {
            return false;
        }
    }
}
