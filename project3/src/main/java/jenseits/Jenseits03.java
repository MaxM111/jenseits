package jenseits;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import jenseits.setup.*;

import jenseits.util.Logger;

public class Jenseits03 implements AutoCloseable {
    private Logger logger;

    public static void main(String[] args) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        var handler = new DBLPHandler();
        parser.parse("toy_example.txt", handler);
        var tree = handler.getTree();
        IO.println(tree.toString());
        try (var obj = new Jenseits03()) {
            tree.toEdgeModel(obj.getConn());

            var descendants = obj.getDescendants(17); // 17 is ID of "pvldb_2023"
            pprintNodeRecords("The descendants of pvldb_2023 are", descendants);

            var ancestors = obj.getAncestors(2); // Author Daniel Ulrich Schmitt has ID 2
            pprintNodeRecords("The ancestors of Author Daniel Ulrich Schmitt are", ancestors);

            var psiblings1 = obj.getPrecedingSiblings(1); // 1 is ID of "SchmittKAMM23"
            pprintNodeRecords("The p-siblings of SchmittKAMM23 are", psiblings1);

            var fsiblings1 = obj.getFollowingSiblings(1); // 1 is ID of "SchmittKAMM23"
            pprintNodeRecords("The f-siblings of SchmittKAMM23 are", fsiblings1);

            var psiblings50 = obj.getPrecedingSiblings(50); // is ID of "SchalerHS23"
            pprintNodeRecords("The p-siblings of SchalerHS23 are", psiblings50);

            var fsiblings50 = obj.getFollowingSiblings(50); // is ID of "SchalerHS23"
            pprintNodeRecords("The f-siblings of SchalerHS23 are", fsiblings50);

        }

    }

    Connection conn;

    public Jenseits03() throws Exception {
        conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);
        logger = new Logger("logs", "log.csv");
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
}
