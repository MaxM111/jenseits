package jenseits;

import java.io.FileWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import jenseits.setup.*;

public class Jenseits03 implements AutoCloseable {
    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.xml.entityExpansionLimit", "1000000000");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "1000000000");
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "1000000000");

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        var handler = new DBLPHandler();
        parser.parse("toy_example.txt", handler);
        var tree = handler.getTree();
        IO.println(tree.toString());
        try (var obj = new Jenseits03()) {
            tree.toEdgeModel(obj.getConn());
            obj.importAccel(tree);
            int treeHeight = height(tree);
            IO.println("Tree height: " + treeHeight);

            var descendants = obj.getDescendants(17); // 17 is ID of "vldb_2023"
            pprintNodeRecords("The descendants of vldb_2023 are", descendants);
            var xpathDescendant = obj.xpath(17, XPathAxis.Descendant);
            pprintNodeRecords("XPath Axis Descendant", xpathDescendant);
            var xpathDescendantReduced = obj.xpathDescendantReduced(17, treeHeight);
            pprintNodeRecords("XPath Axis Descendant Reduced", xpathDescendantReduced);

            var ancestors = obj.getAncestors(2); // Author Daniel Ulrich Schmitt has ID 2
            pprintNodeRecords("The ancestors of Author Daniel Ulrich Schmitt are", ancestors);
            var xpathAncestor = obj.xpath(2, XPathAxis.Ancestor);
            pprintNodeRecords("XPath Axis Ancestor", xpathAncestor);

            var psiblings1 = obj.getPrecedingSiblings(1); // 1 is ID of "SchmittKAMM23"
            pprintNodeRecords("The p-siblings of SchmittKAMM23 are", psiblings1);
            var xpathPSibling1 = obj.xpath(1, XPathAxis.PrecedingSibling);
            pprintNodeRecords("XPath Axis p-siblings of SchmittKAMM23", xpathPSibling1);

            var fsiblings1 = obj.getFollowingSiblings(1); // 1 is ID of "SchmittKAMM23"
            pprintNodeRecords("The f-siblings of SchmittKAMM23 are", fsiblings1);
            var xpathFSibling1 = obj.xpath(1, XPathAxis.FollowingSibling);
            pprintNodeRecords("XPath Axis f-siblings of SchmittKAMM23", xpathFSibling1);

            var psiblings50 = obj.getPrecedingSiblings(49); // is ID of "SchalerHS23"
            pprintNodeRecords("The p-siblings of SchalerHS23 are", psiblings50);
            var xpathPSibling50 = obj.xpath(49, XPathAxis.PrecedingSibling);
            pprintNodeRecords("XPath Axis p-siblings of SchalerHS23", xpathPSibling50);

            var fsiblings50 = obj.getFollowingSiblings(49); // is ID of "SchalerHS23"
            pprintNodeRecords("The f-siblings of SchalerHS23 are", fsiblings50);
            var xpathFSibling50 = obj.xpath(49, XPathAxis.FollowingSibling);
            pprintNodeRecords("XPath Axis f-siblings of SchalerHS23", xpathFSibling50);

            // Phase 2 Bullet Point 1

            SAXParser parser2 = factory.newSAXParser();
            var handler2 = new DBLPHandler();
            parser2.parse("dblp.xml", handler2);

            var root = handler2.getTree();
            var augstenCounts = obj.countAugstenPublications(root);
            IO.println("Nikolaus Augsten publications per venue: " + augstenCounts);

            IO.println("Writing the XML");
            var mySmallBibXml = root.toString();
            try (var writer = new FileWriter("my_small_bib.xml")) {
                writer.write(mySmallBibXml);
            }

            System.out.println("Starting Import as Edge Model");
            root.toEdgeModel(obj.getConn());
            IO.println("Node tuples: " + obj.countTuples("Node"));
            IO.println("Edge tuples: " + obj.countTuples("Edge"));

            // Phase 3 Bullet Point 2
            System.out.println("Import Accel with One Axis Annotation");
            obj.importAccelOneAxis(tree);
            var xpathDescendantOneAxis = obj.xpathDescendantOneAxis(17);
            pprintNodeRecords("XPath Axis Descendant One Axis", xpathDescendantOneAxis);

        }

    }

    Connection conn;

    public Jenseits03() throws Exception {
        conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(false);
    }

    public Connection getConn() {
        return conn;
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }

    public HashMap<String, Integer> countAugstenPublications(Node root) {
        HashMap<String, Integer> counts = new HashMap<>();
        List<Node> venues = root.getChildren();
        for (var venue : venues) {
            String name = venue.attributes.get("name");
            counts.put(name, 0);
            List<Node> years = venue.getChildren();
            for (var year : years) {
                List<Node> publications = year.getChildren();
                for (var publication : publications) {
                    boolean hasAugsten = publication.getChildren().stream().anyMatch(
                            property -> "author".equals(property.tag) && "Nikolaus Augsten".equals(property.content));
                    if (hasAugsten) {
                        counts.put(name, counts.get(name) + 1);
                    }
                }
            }
        }
        return counts;
    }

    public long countTuples(String tableName) throws SQLException {
        var results = conn.createStatement().executeQuery("SELECT COUNT(*) AS count FROM " + tableName);
        results.next();
        return results.getLong("count");
    }

    /*
     * Return the list of preceding-siblings of a given node ID.
     *
     * @param id the ID of the node
     *
     * @return the list of IDs of nodes that are p-siblings of the given node ID
     *
     * @throws SQLException
     */
    public List<NodeRecord> getPrecedingSiblings(long id) throws SQLException {
        // preceding siblings are siblings with ID < id
        var results = conn.createStatement().executeQuery(String.format("""
                SELECT *
                FROM (SELECT e.to_ AS id
                FROM Edge AS e
                WHERE e.to_ < %s AND e.from_ = (SELECT e.from_ AS id
                                                FROM Edge AS e
                                                WHERE e.to_ = %s
                                                LIMIT 1)) as result
                INNER JOIN Node as n ON result.id = n.id
                            """,
                String.valueOf(id), String.valueOf(id)));
        List<NodeRecord> precidingSiblings = new ArrayList<>();
        while (results.next()) {
            var id_ = results.getLong("id");
            var s_id = results.getString("s_id");
            var type = results.getString("type");
            var content = results.getString("content");
            precidingSiblings.add(new NodeRecord(id_, s_id, type, content));
        }
        return precidingSiblings;
    }

    /*
     * Return the list of descendants of a given node ID.
     *
     * @param id the ID of the node
     * 
     * @return the list of IDs of nodes that are descendants of the given node ID
     * 
     * @throws SQLException
     */
    public List<NodeRecord> getDescendants(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                WITH RECURSIVE descendants AS (
                    SELECT e.to_ AS id
                    FROM Edge AS e
                    WHERE e.from_ = %s
                    UNION ALL
                    SELECT e.to_ AS id
                    FROM Edge AS e
                    INNER JOIN descendants AS d
                    ON e.from_ = d.id
                ) SELECT * FROM descendants NATURAL JOIN Node
                    """,
                String.valueOf(id)));

        List<NodeRecord> descendants = new ArrayList<>();
        while (results.next()) {
            var id_ = results.getLong("id");
            var s_id = results.getString("s_id");
            var type = results.getString("type");
            var content = results.getString("content");
            descendants.add(new NodeRecord(id_, s_id, type, content));
        }
        return descendants;
    }

    public List<NodeRecord> getAncestors(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                WITH RECURSIVE descendants AS (
                    SELECT e.from_ AS id
                    FROM Edge AS e
                    WHERE e.to_ = %s
                    UNION ALL
                    SELECT e.from_ AS id
                    FROM Edge AS e
                    INNER JOIN descendants AS d
                    ON e.to_ = d.id
                ) SELECT * FROM descendants NATURAL JOIN Node
                    """,
                String.valueOf(id)));

        List<NodeRecord> descendants = new ArrayList<>();
        while (results.next()) {
            var id_ = results.getLong("id");
            var s_id = results.getString("s_id");
            var type = results.getString("type");
            var content = results.getString("content");
            descendants.add(new NodeRecord(id_, s_id, type, content));
        }
        return descendants;
    }

    public List<NodeRecord> getFollowingSiblings(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                WITH parent AS (
                    SELECT e.from_ AS id
                    FROM Edge AS e
                    WHERE e.to_ = %s
                ),
                siblings AS (
                    SELECT e.to_ AS id
                    FROM Edge AS e
                    INNER JOIN parent AS p
                    ON e.from_ = p.id
                )
                SELECT *
                FROM siblings NATURAL JOIN Node
                WHERE Node.id > %s
                    """,
                String.valueOf(id),
                String.valueOf(id)));

        List<NodeRecord> siblings = new ArrayList<>();
        while (results.next()) {
            var id_ = results.getLong("id");
            var s_id = results.getString("s_id");
            var type = results.getString("type");
            var content = results.getString("content");
            siblings.add(new NodeRecord(id_, s_id, type, content));
        }
        return siblings;
    }

    public record NodeRecord(long id, String s_id, String type, String content) {
        @Override
        public final String toString() {
            String s_id = this.s_id;
            String content = this.content;
            if (this.s_id == null) {
                s_id = "null";
            }
            if (this.content == null) {
                content = "null";
            }
            if (this.content.length() > 10) {
                content = this.content.substring(0, 10) + "...";
            }
            return String.format("id(%d):s_id(%s):type(%s):content(%s)", id, s_id, type, content);
        }
    }

    public static void pprintNodeRecords(String message, List<NodeRecord> nodes) {
        IO.println(message + ":\n[\n"
                + nodes.stream()
                        .map(d -> "  " + d.toString())
                        .collect(Collectors.joining(",\n"))
                + "\n]");
    }

    public void createAccelTable() throws SQLException {
        var statement = conn.createStatement();
        statement.execute("DROP TABLE IF EXISTS accel, content, attribute");
        statement.execute("""
                CREATE TABLE accel (
                    id BIGINT NOT NULL,
                    pre BIGINT NOT NULL,
                    post BIGINT NOT NULL,
                    parent BIGINT
                )
                """);
        statement.execute("CREATE TABLE content (id BIGINT, text VARCHAR(512))");
        statement.execute("CREATE TABLE attribute(id BIGINT, text VARCHAR(512))");
        conn.commit();
    }

    /*
     * Annotate the tree with preorder and postorder and import it into the database
     * into the accel table.
     */
    public void importAccel(Node root) throws SQLException {
        annotatePreorder(root);
        annotatePostorder(root);
        createAccelTable();
        root.toAccel(conn);
        createHelperFunctions();
        conn.commit();
    }

    public static void annotatePreorder(Node root) throws SQLException {
        annotatePreorderSubtree(root, 0);
    }

    private static long annotatePreorderSubtree(Node subtree, long counter) {
        subtree.setPreorder(counter++);
        for (var child : subtree.getChildren()) {
            counter = annotatePreorderSubtree(child, counter);
        }
        return counter;
    }

    public static void annotatePostorder(Node root) throws SQLException {
        annotatePostorderSubtree(root, 0);
    }

    // "Jeder Knoten vor seinem Vater und vor seinem rechten Nachbar".
    private static long annotatePostorderSubtree(Node subtree, long counter) {
        for (var child : subtree.getChildren()) {
            // annotate from left to right => "Jeder Knoten vor seinem rechten Nachbar"
            counter = annotatePostorderSubtree(child, counter);
        }
        // annotate parent after annotating children => "Jeder Knoten vor seinem Vater"
        subtree.setPostorder(counter++);
        return counter;
    }

    public List<NodeRecord> xpath(long id, XPathAxis axis) throws SQLException {
        return switch (axis) {
            case Ancestor -> xpathAncestor(id);
            case Descendant -> xpathDescendant(id);
            case FollowingSibling -> xpathFSibling(id);
            case PrecedingSibling -> xpathPSibling(id);
        };
    }

    public void createHelperFunctions() throws SQLException {
        var statement = conn.createStatement();
        statement.execute("DROP FUNCTION IF EXISTS preorder, postorder, parent");
        statement.execute("""
                CREATE FUNCTION preorder(param BIGINT)
                RETURNS BIGINT
                LANGUAGE SQL STABLE
                AS $$
                SELECT pre
                FROM accel AS a
                WHERE a.id = param
                $$
                    """);

        statement.execute("""
                CREATE FUNCTION postorder(param BIGINT)
                RETURNS BIGINT
                LANGUAGE SQL STABLE
                AS $$
                SELECT post
                FROM accel AS a
                WHERE a.id = param
                $$
                    """);

        statement.execute("""
                CREATE FUNCTION parent(param BIGINT)
                RETURNS BIGINT
                LANGUAGE SQL STABLE
                AS $$
                SELECT parent
                FROM accel AS a
                WHERE a.id = param
                $$
                    """);
    }

    // For a node v, ancestors are:
    // -----pre----------------post-----------parent-----
    // <[0, preorder(v)), (postorder(v), inf), * >
    private List<NodeRecord> xpathAncestor(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                SELECT id
                FROM accel
                WHERE pre < preorder(%d)
                AND postorder(%d) < post
                    """, id, id));
        List<NodeRecord> ids = new ArrayList<>();
        while (results.next()) {
            var node = new NodeRecord(results.getLong("id"), "", "", "");
            ids.add(node);
        }
        return ids;
    }

    // For a node v, descendants are:
    // -----pre----------------post-----------parent-----
    // <[preorder(v), inf, (0, postorder(v)), * >
    private List<NodeRecord> xpathDescendant(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                SELECT id
                FROM accel
                WHERE pre > preorder(%d)
                AND post < postorder(%d)
                    """, id, id));
        List<NodeRecord> ids = new ArrayList<>();
        while (results.next()) {
            var node = new NodeRecord(results.getLong("id"), "", "", "");
            ids.add(node);
        }
        return ids;
    }

    // For a node v, preceding siblings are:
    // -----pre----------------post-----------parent-----
    // <[0, preorder(v)), [0, postorder(v)), parent(v)>
    private List<NodeRecord> xpathPSibling(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                SELECT id
                FROM accel
                WHERE pre < preorder(%d) AND post < postorder(%d) AND parent = parent(%s)
                    """, id, id, id));
        List<NodeRecord> ids = new ArrayList<>();
        while (results.next()) {
            var node = new NodeRecord(results.getLong("id"), "", "", "");
            ids.add(node);
        }
        return ids;
    }

    // For a node v, following siblingss are:
    // -----pre----------------post-----------parent-----
    // <(preoder(v),inf), (postorder(v),inf), parent(v)>
    private List<NodeRecord> xpathFSibling(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                SELECT id
                FROM accel
                WHERE pre > preorder(%d)
                AND post > postorder(%d)
                AND parent = parent(%d)
                    """, id, id, id));
        List<NodeRecord> ids = new ArrayList<>();
        while (results.next()) {
            var node = new NodeRecord(results.getLong("id"), "", "", "");
            ids.add(node);
        }
        return ids;
    }

    private enum XPathAxis {
        Ancestor,
        Descendant,
        FollowingSibling,
        PrecedingSibling,
    }

    public static int height(Node root) {
        int maxHeight = 0;
        for (var child : root.getChildren()) {
            maxHeight = Math.max(maxHeight, height(child) + 1);
        }
        return maxHeight;
    }

    private List<NodeRecord> xpathDescendantReduced(long id, Integer treeHeight) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                SELECT id
                FROM accel
                WHERE pre > preorder(%d)
                AND pre <= postorder(%d) + %d
                AND post < postorder(%d)
                AND post >= preorder(%d) - %d
                    """, id, id, treeHeight, id, id, treeHeight));
        List<NodeRecord> ids = new ArrayList<>();
        while (results.next()) {
            var node = new NodeRecord(results.getLong("id"), "", "", "");
            ids.add(node);
        }
        return ids;
    }

    private static long annotateOneAxis(Node node, long counter) {
        node.setPreorder(counter++);
        for (var child : node.getChildren()) {
            counter = annotateOneAxis(child, counter);
        }
        node.setPostorder(counter++);
        return counter;
    }

    public void importAccelOneAxis(Node root) throws SQLException {
        annotateOneAxis(root, 0);
        createAccelTable();
        root.toAccel(conn);
        createHelperFunctions();
        conn.commit();
    }

    private List<NodeRecord> xpathDescendantOneAxis(long id) throws SQLException {
        var results = conn.createStatement().executeQuery(String.format("""
                SELECT id
                FROM accel
                WHERE pre > preorder(%d)
                AND pre < postorder(%d)
                    """, id, id));
        List<NodeRecord> ids = new ArrayList<>();
        while (results.next()) {
            var node = new NodeRecord(results.getLong("id"), "", "", "");
            ids.add(node);
        }
        return ids;
    }
}
