package jenseits;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
    public void toEdgeModel(Connection conn) throws SQLException {
        var statement = conn.createStatement();
        statement.execute("DROP TABLE IF EXISTS Node, Edge");
        statement.execute(
                "CREATE TABLE Node (id BIGINT NOT NULL, s_id VARCHAR(100), type VARCHAR(50) NOT NULL, content VARCHAR(512))");
        statement.execute("CREATE TABLE Edge (from_ BIGINT NOT NULL, to_ BIGINT NOT NULL)");
        statement.close();
        var nodeInserter = conn.prepareStatement("INSERT INTO Node VALUES (?, ?, ?, ?)");
        var edgeInserter = conn.prepareStatement("INSERT INTO Edge VALUES (?, ?)");

        var counter = new BatchCounter(10_000);
        insertSubtreeToEdgeModel(nodeInserter, edgeInserter, counter);

        if (counter.nodeCounter > 0) {
            nodeInserter.executeBatch();
        }
        if (counter.edgeCounter > 0) {
            edgeInserter.executeBatch();
        }
        conn.commit();
    }

    private void insertSubtreeToEdgeModel(PreparedStatement nodeInserter, PreparedStatement edgeInserter,
            BatchCounter counter)
            throws SQLException {
        String s_id;
        if (tag.equals("bib")) {
            s_id = "bib";
        } else if (tag.equals("venue")) {
            s_id = attributes.getOrDefault("name", "null");
        } else if (tag.equals("publishingYear")) {
            String year = attributes.getOrDefault("value", "null");
            String venue = this.parent.attributes.getOrDefault("name", "null");
            s_id = venue + "_" + year;
        } else if (attributes.containsKey("key")) {
            var key = attributes.get("key");
            if (key == null) {
                IO.println("warning: publication does not have attribute `key`, not inserting into DB");
                s_id = "null";
            } else {
                var parts = key.split("/");
                s_id = parts[parts.length - 1];
            }
        } else {
            s_id = "null";
        }

        nodeInserter.setLong(1, this.id);
        nodeInserter.setString(2, s_id);
        nodeInserter.setString(3, this.tag);
        nodeInserter.setString(4, this.content == null ? "null" : this.content);
        nodeInserter.addBatch();

        counter.incrementNodeCounter();
        if (counter.reachedNodeThreshold()) {
            nodeInserter.executeBatch();
            counter.resetNodeCounter();
        }

        insertChildrenIntoEdgeModel(nodeInserter, edgeInserter, counter);
    }

    private void insertChildrenIntoEdgeModel(PreparedStatement nodeInserter, PreparedStatement edgeInserter,
            BatchCounter counter)
            throws SQLException {
        for (var child : children) {
            edgeInserter.setLong(1, this.id);
            edgeInserter.setLong(2, child.getID());
            edgeInserter.addBatch();

            counter.incrementEdgeCounter();
            if (counter.reachedEdgeThreshold()) {
                edgeInserter.executeBatch();
                counter.resetEdgeCounter();
            }

            child.insertSubtreeToEdgeModel(nodeInserter, edgeInserter, counter);
        }
    }

    @Override
    public String toString() {
        String s = "";

        s += "<" + this.tag;
        for (var key : this.attributes.keySet()) {
            s += " " + key + "=\"" + this.attributes.get(key) + '"';
        }
        s += ">";
        if (this.tag.equals("bib") || this.tag.equals("publishingYear") || this.tag.equals("venue")
                || this.attributes.containsKey("key")) {
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

    private class BatchCounter {
        int nodeCounter;
        int edgeCounter;
        int threshold;

        BatchCounter(int threshold) {
            nodeCounter = 0;
            edgeCounter = 0;
        }

        void resetNodeCounter() {
            nodeCounter = 0;
        }

        void resetEdgeCounter() {
            edgeCounter = 0;
        }

        void incrementNodeCounter() {
            nodeCounter++;
        }

        void incrementEdgeCounter() {
            edgeCounter++;
        }

        boolean reachedNodeThreshold() {
            return nodeCounter > threshold;
        }

        boolean reachedEdgeThreshold() {
            return edgeCounter > threshold;
        }
    }
}
