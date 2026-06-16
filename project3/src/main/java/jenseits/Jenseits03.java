package jenseits;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
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

            var descendants = obj.getDescendants(17); // 17 is ID of "vldb_2023"
            pprintNodeRecords("The descendants of vldb_2023 are", descendants);

            var ancestors = obj.getAncestors(2); // Author Daniel Ulrich Schmitt has ID 2
            pprintNodeRecords("The ancestors of Author Daniel Ulrich Schmitt are", ancestors);

            var psiblings1 = obj.getPrecedingSiblings(1); // 1 is ID of "SchmittKAMM23"
            pprintNodeRecords("The p-siblings of SchmittKAMM23 are", psiblings1);

            var fsiblings1 = obj.getFollowingSiblings(1); // 1 is ID of "SchmittKAMM23"
            pprintNodeRecords("The f-siblings of SchmittKAMM23 are", fsiblings1);

            var psiblings50 = obj.getPrecedingSiblings(49); // is ID of "SchalerHS23"
            pprintNodeRecords("The p-siblings of SchalerHS23 are", psiblings50);

            var fsiblings50 = obj.getFollowingSiblings(49); // is ID of "SchalerHS23"
            pprintNodeRecords("The f-siblings of SchalerHS23 are", fsiblings50);

            // Phase 2 Bullet Point 1

            SAXParser parser2 = factory.newSAXParser();
            var handler2 = new DBLPHandler();
            parser2.parse("dblp.xml", handler2);

            var root = handler2.getTree();

            IO.println("Writing the XML");
            var mySmallBibXml = root.toString();
            try (var writer = new FileWriter("my_small_bib.xml")) {
                writer.write(mySmallBibXml);
            }

            System.out.println("Starting Import as Edge Model");
            root.toEdgeModel(obj.getConn());
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
        statement.execute("DROP TABLE IF EXISTS accel, content");
        statement.execute("""
                CREATE TABLE accel (
                    id BIGINT NOT NULL,
                    pre BIGINT NOT NULL,
                    post BIGINT NOT NULL,
                    parent BIGINT,
                    type VARCHAR(50) NOT NULL,
                    s_id VARCHAR(100)
                )
                """);
        statement.execute("CREATE TABLE content (id BIGINT, text VARCHAR(512))");
        // NOTE: why this?
        // statement.execute("CREATE TABLE attribute(id BIGINT, text VARCHAR(512))");
        conn.commit();
    }

    /*
     * Annotate the tree with preorder and postorder and import it into the database
     * into the accel table.
     */
    public void importAccel(Node root) throws SQLException {
        // annotatePreorder(root);
        annotatePostorder(root);
        createAccelTable();
        root.toAccel(conn);
        conn.commit();
    }

    public static void annotatePreorder(Node root) throws SQLException {
        annotatePreorderSubtree(root, 0);
    }

    private static long annotatePreorderSubtree(Node subtree, long counter) {
        subtree.setPostorder(counter++);
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
}
