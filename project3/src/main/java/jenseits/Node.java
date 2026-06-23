package jenseits;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLType;
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
    public String tag;
    public NavigableMap<String, String> attributes;
    private Node parent;
    private List<Node> children;
    public String content; // can be null
    private long preorder;
    private long postorder;

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

    public List<Node> getChildren() {
        return children;
    }

    public void setPreorder(long preorder) {
        this.preorder = preorder;
    }

    public void setPostorder(long postorder) {
        this.postorder = postorder;
    }

    public String create_s_id() {
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
        return s_id;
    }

    /*
     * Converts the XML subtree into the edge model.
     */
    public void toEdgeModel(Connection conn) throws SQLException {
        var statement = conn.createStatement();
        statement.execute("DROP TABLE IF EXISTS Node, Edge");
        statement.execute(
                "CREATE TABLE Node (id BIGINT NOT NULL, s_id VARCHAR(100), type VARCHAR(50) NOT NULL, content VARCHAR(1024))");
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

        nodeInserter.setLong(1, this.id);
        nodeInserter.setString(2, create_s_id());
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

    public void toAccel(Connection conn) throws SQLException {
        var accelInserter = conn.prepareStatement("INSERT INTO accel VALUES (?, ?, ?, ?)");
        var contentInserter = conn.prepareStatement("INSERT INTO content VALUES (?, ?)");
        var attributeInserter = conn.prepareStatement("INSERT INTO attribute VALUES (?, ?)");

        // we use node counter for accel inserter, edge counter for content inserter
        var counter = new BatchCounter(10_000);
        insertSubtreeIntoAccel(accelInserter, contentInserter, attributeInserter, counter);

        if (counter.nodeCounter > 0) {
            accelInserter.executeBatch();
        }
        if (counter.edgeCounter > 0) {
            contentInserter.executeBatch();
        }
        if (counter.attributeCounter > 0) {
            attributeInserter.executeBatch();
        }
        conn.commit();
    }

    private void insertSubtreeIntoAccel(PreparedStatement accelInserter, PreparedStatement contentInserter,
            PreparedStatement attributeInserter, BatchCounter counter) throws SQLException {
        accelInserter.setLong(1, this.id);
        accelInserter.setLong(2, this.preorder);
        accelInserter.setLong(3, this.postorder);
        if (parent == null) {
            accelInserter.setNull(4, java.sql.Types.BIGINT);
        } else {
            accelInserter.setLong(4, parent.getID());
        }
        accelInserter.addBatch();
        counter.incrementNodeCounter();

        if (counter.reachedNodeThreshold()) {
            accelInserter.executeBatch();
            counter.resetNodeCounter();
        }

        if (this.content != null) {
            contentInserter.setLong(1, this.id);
            contentInserter.setString(2, this.content);
            contentInserter.addBatch();
            counter.incrementEdgeCounter();

            if (counter.reachedEdgeThreshold()) {
                contentInserter.executeBatch();
                counter.resetEdgeCounter();
            }
        }

        for (var attribute : attributes.entrySet()) {
            attributeInserter.setLong(1, this.id);
            attributeInserter.setString(2, attribute.getKey() + "=" + attribute.getValue());
            attributeInserter.addBatch();
            counter.incrementAttributeCounter();

            if (counter.reachedAttributeThreshold()) {
                attributeInserter.executeBatch();
                counter.resetAttributeCounter();
            }
        }

        for (var child : children) {
            child.insertSubtreeIntoAccel(accelInserter, contentInserter, attributeInserter, counter);
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
        int attributeCounter;
        int threshold;

        BatchCounter(int threshold) {
            nodeCounter = 0;
            edgeCounter = 0;
            attributeCounter = 0;
            this.threshold = threshold;
        }

        void resetNodeCounter() {
            nodeCounter = 0;
        }

        void resetEdgeCounter() {
            edgeCounter = 0;
        }

        void resetAttributeCounter() {
            attributeCounter = 0;
        }

        void incrementNodeCounter() {
            nodeCounter++;
        }

        void incrementEdgeCounter() {
            edgeCounter++;
        }

        void incrementAttributeCounter() {
            attributeCounter++;
        }

        boolean reachedNodeThreshold() {
            return nodeCounter > threshold;
        }

        boolean reachedEdgeThreshold() {
            return edgeCounter > threshold;
        }

        boolean reachedAttributeThreshold() {
            return attributeCounter > threshold;
        }
    }
}
