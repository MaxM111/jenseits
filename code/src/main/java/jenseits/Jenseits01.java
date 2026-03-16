package jenseits;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Random;

import jenseits.setup.DB;
import jenseits.setup.Database;
import jenseits.util.*;

public class Jenseits01 {
    public static void main(String[] args) throws Exception {
        var conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);

        infrastructureDemo(conn);

        createTable(conn);
        fillValues(conn);

        createTableVertical(conn);
        fillValueVerticalManually(conn);

        createViewV2H(conn);

        generate(conn, 20, 0.3, 10);
    }

    static void infrastructureDemo(Connection conn) throws Exception {
        var stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS demotable");
        stmt.execute("CREATE TABLE demotable (name VARCHAR(50), age INTEGER)");

        var valuesSql = "INSERT INTO demotable (name, age) VALUES (?, ?)";
        var prepStmt = conn.prepareStatement(valuesSql);
        prepStmt.setString(1, "Sam Crawford");
        prepStmt.setInt(2, 21);
        prepStmt.execute();
        prepStmt.setString(1, "Maximilan Moderegger");
        prepStmt.setInt(2, 32);
        prepStmt.execute();

        var querySamAge = """
                SELECT age
                FROM demotable
                WHERE demotable.name = 'Sam Crawford'
                """; // remember: use single quotes for postgresql
        ResultSet results = conn.createStatement().executeQuery(querySamAge);
        if (results.next()) {
            IO.println("Age of Sam Crawford: " + results.getInt("age"));
        }
    }

    static void createTable(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS H_toy");
        // NOTE: no length for string specified. Example uses single characters, so 10
        // should suffice
        stmt.execute("""
                        CREATE TABLE H_toy (
                            oid INTEGER PRIMARY KEY,
                            a1 VARCHAR(10),
                            a2 VARCHAR(10),
                            a3 INTEGER
                )""");
    }

    static final String[] a1Column = new String[] { "a", null, null, null };
    static final String[] a2Column = new String[] { "b", "c", null, null };
    static final Integer[] a3Column = new Integer[] { null, 2, 3, null };

    static void fillValues(Connection conn) throws Exception {
        var prepSql = "INSERT INTO H_toy (oid, a1, a2, a3) VALUES (?, ?, ?, ?)";
        var prepStmt = conn.prepareStatement(prepSql);

        for (int i = 0; i < 4; i++) {
            int oid = i + 1;
            prepStmt.setInt(1, oid);
            prepStmt.setString(2, a1Column[i]);
            prepStmt.setString(3, a2Column[i]);
            if (a3Column[i] == null) {
                prepStmt.setNull(4, java.sql.Types.INTEGER);
            } else {
                prepStmt.setInt(4, a3Column[i]);
            }
            prepStmt.execute();
        }
    }

    static void createTableVertical(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS V_toy CASCADE");
        stmt.execute("CREATE TABLE V_toy (oid INTEGER, key VARCHAR(10), val VARCHAR(10))");
    }

    static void fillValueVerticalManually(Connection conn) throws Exception {
        var prepSql = "INSERT INTO V_toy (oid, key, val) VALUES (?, ?, ?)";
        var prepStmt = conn.prepareStatement(prepSql);

        for (int i = 0; i < 4; i++) {
            int oid = i + 1;
            if (a1Column[i] != null) {
                prepStmt.setInt(1, oid);
                prepStmt.setString(2, "a1");
                prepStmt.setString(3, a1Column[i]);
                prepStmt.execute();
            }
            if (a2Column[i] != null) {
                prepStmt.setInt(1, oid);
                prepStmt.setString(2, "a2");
                prepStmt.setString(3, a2Column[i]);
                prepStmt.execute();
            }
            if (a3Column[i] != null) {
                prepStmt.setInt(1, oid);
                prepStmt.setString(2, "a3");
                prepStmt.setString(3, a3Column[i].toString());
                prepStmt.execute();
            }
        }
    }

    // view V_toy like H_toy
    static void createViewV2H(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP VIEW IF EXISTS h2v_toy");
        stmt.execute("""
                CREATE VIEW h2v_toy AS
                SELECT v.oid as oid,
                        v1.val as a1,
                        v2.val as a2,
                        v3.val as a3
                FROM ((SELECT DISTINCT oid from v_toy) v
                LEFT OUTER JOIN v_toy as v1 on (v.oid = v1.oid)
                LEFT OUTER JOIN v_toy as v2 on (v.oid = v2.oid)
                LEFT OUTER JOIN v_toy as v3 on (v.oid = v3.oid))
                """);
    }

    /*
     * Create and fill a horizontal table H
     * Replace the table if it already existed.
     *
     * numTuples: number of tuples in H
     * sparsity: average amount of tuples per attribute whose value is null
     * numAttributes: number of attributes per tuple
     *
     * Further, for every attribute A, there are at most 5 tuples that share the
     * same value for attribute A.
     *
     * TODO:
     * "Für alle Argumente ist es zulässig nur eine Auswahl an vordefinierten Werten zuzulassen."
     * - Prof
     * What does this mean?
     */
    static void generate(Connection conn, int numTuples, double sparsity, int numAttributes)
            throws Exception {
        assert 0 <= sparsity && sparsity <= 1;
        assert 0 < numAttributes && numAttributes <= 1600; // Postgresql supports at most 1600

        conn.createStatement().execute("DROP TABLE IF EXISTS generated");

        var rand = new Random();
        var createSqlBuilder = new StringBuilder().append("CREATE TABLE generated (");

        var attributes = new Attribute[numAttributes];
        for (int i = 0; i < attributes.length; i++) {
            var type = rand.nextBoolean() ? AttributeType.String : AttributeType.Integer;
            attributes[i] = new Attribute("a" + (i + 1), type);

            createSqlBuilder
                    .append(attributes[i].name)
                    .append(' ')
                    .append(attributes[i].type.sqlType());
            if (i < attributes.length - 1) {
                createSqlBuilder.append(", ");
            }
        }
        conn.createStatement()
                .execute(createSqlBuilder.append(')').toString());

        var genericVarchar = new GenericVarchar();
        var genericInteger = new GenericInteger();
        var insertSql = "INSERT INTO generated VALUES (" + "?, ".repeat(attributes.length - 1) + "?)";
        var prepStmt = conn.prepareStatement(insertSql);
        for (int i = 0; i < numTuples; i++) {
            for (int j = 0; j < attributes.length; j++) {
                var attribute = attributes[j];
                int paramIdx = j + 1; // set<Type> is not 0-indexed

                switch (attribute.type) {
                    case AttributeType.Integer -> {
                        if (rand.nextDouble() <= sparsity) {
                            prepStmt.setNull(paramIdx, java.sql.Types.INTEGER);
                        } else {
                            prepStmt.setInt(paramIdx, genericInteger.next());
                        }
                    }

                    case AttributeType.String -> {
                        if (rand.nextDouble() <= sparsity) {
                            prepStmt.setNull(paramIdx, java.sql.Types.VARCHAR);
                        } else {
                            prepStmt.setString(paramIdx, genericVarchar.next());
                        }
                    }
                }
            }

            prepStmt.execute();
        }
    }

    private record Attribute(String name, AttributeType type) {
    record Attribute(String name, AttributeType type) {
    }

    enum AttributeType {
        String,
        Integer;

        String sqlType() {
            return switch (this) {
                case String -> "VARCHAR(50)";
                case Integer -> "INTEGER";
            };
        }
    }
}
