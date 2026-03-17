package jenseits;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

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

        partitionV_toy(conn);
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

    /*
     * Create view v2h_toy to view V_toy like H_toy
     */
    static void createViewV2H(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP VIEW IF EXISTS h2v_toy");
        stmt.execute("""
                CREATE VIEW v2h_toy AS
                SELECT v.oid as oid,
                        v1.val as a1,
                        v2.val as a2,
                        v3.val as a3
                FROM
                    (
                    (SELECT DISTINCT oid from v_toy) AS v
                    LEFT OUTER JOIN v_toy AS v1 ON (v.oid = v1.oid AND v1.key='a1')
                    LEFT OUTER JOIN v_toy AS v2 ON (v.oid = v2.oid AND v2.key='a2')
                    LEFT OUTER JOIN v_toy AS v3 ON (v.oid = v3.oid AND v3.key='a3')
                    )
                ORDER BY oid;
                """);
    }

    /*
     * Create Tables V_toy_int and V_toy_str
     * Fill Tables with appropraite values
     * Create View V_toy_all
     */
    static void partitionV_toy(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();

        stmt.execute("DROP TABLE IF EXISTS V_toy_int CASCADE");
        stmt.execute("DROP TABLE IF EXISTS V_toy_str CASCADE");

        stmt.execute("CREATE TABLE V_toy_int (oid INTEGER, key VARCHAR(10), val INTEGER)");
        stmt.execute("CREATE TABLE V_toy_str (oid INTEGER, key VARCHAR(10), val VARCHAR(10))");

        stmt.execute("""
                INSERT INTO V_toy_int (oid, key, val)
                SELECT oid, key, val::INTEGER FROM v_toy WHERE val ~ '^[0-9]+$';
                    """);
        stmt.execute("""
                INSERT INTO V_toy_str (oid, key, val)
                SELECT oid, key, val FROM v_toy WHERE val !~ '^[0-9]+$';
                    """);

        stmt.execute("""
                CREATE view v_toy_all AS
                SELECT oid, key, val::VARCHAR(10) as val FROM v_toy_int
                UNION
                SELECT oid,key,val FROM v_toy_str
                ORDER BY OID;
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
     */
    static void generate(Connection conn, int numTuples, double sparsity, int numAttributes)
            throws Exception {
        assert 0 <= numTuples;
        assert 0 <= sparsity && sparsity <= 1;
        assert 0 < numAttributes && numAttributes <= 1600; // Postgresql supports at most 1600

        conn.createStatement().execute("DROP TABLE IF EXISTS generated CASCADE");

        var createSqlBuilder = new StringBuilder()
                .append("CREATE TABLE generated (");
        Attribute[] attributes = generateAttributes(numAttributes);
        String attributesSql = Arrays.stream(attributes)
                .map(attribute -> attribute.name + ' ' + attribute.type.sqlType())
                .collect(Collectors.joining(", "));
        createSqlBuilder
                .append(attributesSql)
                .append(')');
        conn.createStatement().execute(createSqlBuilder.toString());

        var insertSql = "INSERT INTO generated VALUES (" + "?, ".repeat(attributes.length - 1) + "?)";
        var prepStmt = conn.prepareStatement(insertSql);
        for (int i = 0; i < numTuples; i++) {
            setGeneratedRowValues(prepStmt, attributes, sparsity);
        }

        showCorrectnessWithViews(conn, attributes);
    }

    private static void setGeneratedRowValues(PreparedStatement prepStmt, Attribute[] attributes, double sparsity)
            throws Exception {
        for (int j = 0; j < attributes.length; j++) {
            var attribute = attributes[j];
            int paramIdx = j + 1; // set<Type> is not 0-indexed
            setAttributeValue(prepStmt, attribute, paramIdx, sparsity);
        }

        prepStmt.execute();
    }

    private static void setAttributeValue(PreparedStatement prepStmt, Attribute attribute, int paramIdx,
            double sparsity) throws Exception {
        var isNull = new Random().nextDouble() <= sparsity;

        switch (attribute.type) {
            case AttributeType.Integer -> {
                if (isNull) {
                    prepStmt.setNull(paramIdx, java.sql.Types.INTEGER);
                } else {
                    prepStmt.setInt(paramIdx, attribute.intGenerator.next());
                }
            }

            case AttributeType.String -> {
                if (isNull) {
                    prepStmt.setNull(paramIdx, java.sql.Types.VARCHAR);
                } else {
                    prepStmt.setString(paramIdx, attribute.varcharGenerator.next());
                }
            }
        }
    }

    private static Attribute[] generateAttributes(int numAttributes) {
        var rand = new Random();

        var attributes = new Attribute[numAttributes];
        for (int i = 0; i < attributes.length; i++) {
            AttributeType type;
            IntGenerator intGenerator = null;
            StringGenerator varcharGenerator = null;

            if (rand.nextBoolean()) {
                type = AttributeType.String;
                varcharGenerator = new StringGenerator();
            } else {
                type = AttributeType.Integer;
                intGenerator = new IntGenerator();
            }

            attributes[i] = new Attribute(
                    "a" + (i + 1),
                    type,
                    intGenerator,
                    varcharGenerator);
        }

        return attributes;
    }

    static void showCorrectnessWithViews(Connection conn, Attribute[] attributes) throws Exception {
        var views = List.of(
                "DROP VIEW IF EXISTS num_tuples, attributes, num_attributes, num_values, null_amount, over5Dups CASCADE",
                """
                        CREATE VIEW num_tuples AS
                        SELECT COUNT(*)
                        FROM generated
                            """,
                """
                        CREATE VIEW attributes AS
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_name = 'generated'
                                """,
                """
                        CREATE VIEW num_attributes AS
                        SELECT COUNT(*)
                        FROM attributes
                                    """,
                """
                        CREATE VIEW num_values AS
                        SELECT num_tuples.count * num_attributes.count AS total
                        FROM num_tuples, num_attributes
                                """,
                nullAmountView(attributes),
                over5DuplicatesView(attributes));

        var stmt = conn.createStatement();
        for (var view : views) {
            stmt.execute(view);
        }
    }

    private static String nullAmountView(Attribute[] attributes) {
        var nullAmountBuilder = new StringBuilder()
                .append("""
                        CREATE VIEW null_amount AS
                        SELECT COUNT(*) * 1.0 / MAX(num_values.total) AS relative_null
                        FROM num_values, (
                            """);

        String allColumnsNulls = Arrays.stream(attributes)
                .map(attribute -> String.format("""
                        SELECT *
                        FROM generated
                        WHERE %s IS NULL
                        """, attribute.name))
                .collect(Collectors.joining(" UNION ALL ")); // ALL keeps duplicates

        return nullAmountBuilder
                .append(allColumnsNulls)
                .append(")")
                .toString();
    }

    private static String over5DuplicatesView(Attribute[] attributes) {
        var max5DupsBuilder = new StringBuilder("""
                CREATE VIEW over5Dups AS
                SELECT *
                FROM (
                    """);

        // cast is needed since some columns are integer, some are varchar
        String over5CountValue = Arrays.stream(attributes)
                .map(attribute -> String.format("""
                        SELECT CAST(%1$s AS VARCHAR) AS value, COUNT(%1$s)
                        FROM generated
                        GROUP BY %1$s
                        HAVING COUNT(%1$s) > 5
                        """, attribute.name))
                .collect(Collectors.joining(" UNION ALL "));

        return max5DupsBuilder
                .append(over5CountValue)
                .append(")")
                .toString();
    }

    record Attribute(String name, AttributeType type, IntGenerator intGenerator, StringGenerator varcharGenerator) {
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
