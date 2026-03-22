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
import jenseits.setup.Pair;
import jenseits.util.*;

public class Jenseits01 {
    public static void main(String[] args) throws Exception {
        var conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);

        infrastructureDemo(conn);

        createTableHorizontal(conn);
        fillValuesHorizontal(conn);

        createTableVertical(conn);
        fillValueVerticalManually(conn);

        createViewV2H(conn);

        generate(conn, 20, 0.3, 10);

        partitionV_toy(conn);

        H2V(conn, "generated");
        V2H(conn, "generated", 9);
    }

    // ----------------------- PHASE 1 -----------------------

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

    static void createTableHorizontal(Connection conn) throws Exception {
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

    static void fillValuesHorizontal(Connection conn) throws Exception {
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
        stmt.execute("DROP VIEW IF EXISTS v2h_toy");
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
                .append("CREATE TABLE generated (")
                .append("oid INTEGER, ");
        Attribute[] attributes = generateAttributes(numAttributes);
        String attributesSql = Arrays.stream(attributes)
                .map(attribute -> attribute.name + ' ' + attribute.type.sqlType())
                .collect(Collectors.joining(", "));
        createSqlBuilder
                .append(attributesSql)
                .append(')');
        conn.createStatement().execute(createSqlBuilder.toString());

        var insertSql = "INSERT INTO generated VALUES (" + "?, ".repeat(attributes.length) + "?)";
        var prepStmt = conn.prepareStatement(insertSql);
        for (int i = 0; i < numTuples; i++) {
            setGeneratedRowValues(prepStmt, i + 1, attributes, sparsity);
        }

        showCorrectnessWithViews(conn, attributes);
    }

    private static void setGeneratedRowValues(PreparedStatement prepStmt, int oid, Attribute[] attributes,
            double sparsity)
            throws Exception {
        prepStmt.setInt(1, oid);
        for (int j = 0; j < attributes.length; j++) {
            var attribute = attributes[j];
            int paramIdx = j + 2; // set<Type> is not 0-indexed, first column reserved for oid
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

    // ----------------------- PHASE 2 -----------------------

    /*
     * Transforms a relation in horizontal representation into a vertically
     * represented relation.
     *
     * Type safety is preserved by partitioning the table according to the types
     */
    static void H2V(Connection conn, String horizontalRelation) throws Exception {
        String verticalStrName = "vertical_str_" + horizontalRelation;
        String verticalIntName = "vertical_int_" + horizontalRelation;
        var statement = conn.createStatement();
        statement.execute(String.format("DROP TABLE IF EXISTS %s, %s CASCADE", verticalStrName, verticalIntName));

        List<Attribute> attributes = queryAttributes(conn, horizontalRelation);

        createPartitionedVerticalTable(statement, verticalStrName, verticalIntName);

        String query = String.format("SELECT * FROM %s", horizontalRelation);
        ResultSet rows = conn.createStatement().executeQuery(query);

        while (rows.next()) {
            transformRow(conn, rows, attributes, verticalStrName, verticalIntName);
        }
    }

    private static void transformRow(Connection conn, ResultSet rows, List<Attribute> attributes,
            String verticalStrName, String verticalIntName) throws Exception {
        int oid = rows.getInt("oid");
        for (var attribute : attributes) {
            switch (attribute.type) {
                case AttributeType.String -> {
                    var val = rows.getString(attribute.name);
                    if (rows.wasNull()) {
                        continue;
                    }
                    insertStrAttribute(conn, verticalStrName, oid, attribute.name, val);
                }
                case AttributeType.Integer -> {
                    int val = rows.getInt(attribute.name);
                    if (rows.wasNull()) {
                        continue;
                    }
                    insertIntAttribute(conn, verticalIntName, oid, attribute.name, val);
                }
            }
        }
    }

    private static void insertStrAttribute(Connection conn, String verticalStrName, int oid, String name, String val)
            throws Exception {
        PreparedStatement prepStmt = conn
                .prepareStatement(String.format("INSERT INTO %s VALUES (?, ?, ?)", verticalStrName));
        prepStmt.setInt(1, oid);
        prepStmt.setString(2, name);
        prepStmt.setString(3, val);
        prepStmt.execute();
    }

    private static void insertIntAttribute(Connection conn, String verticalIntName, int oid, String name, int val)
            throws Exception {
        PreparedStatement prepStmt = conn
                .prepareStatement(String.format("INSERT INTO %s VALUES (?, ?, ?)", verticalIntName));
        prepStmt.setInt(1, oid);
        prepStmt.setString(2, name);
        prepStmt.setInt(3, val);
        prepStmt.execute();
    }

    private static List<Attribute> queryAttributes(Connection conn, String table) throws Exception {
        String sql = String.format("""
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_name = '%s'
                        """, table);

        ResultSet results = conn.createStatement().executeQuery(sql);
        var list = new ArrayList<Attribute>();
        while (results.next()) {
            var name = results.getString("column_name");
            if (name.equals("oid")) {
                continue;
            }

            var type = results.getString("data_type").equals("integer") ? AttributeType.Integer : AttributeType.String;
            Attribute attribute = new Attribute(name, type, null, null);
            list.add(attribute);
        }

        list.sort((a1, a2) -> {
            Integer a1ColumnNumber = Integer.parseInt(a1.name.substring(1));
            Integer a2ColumnNumber = Integer.parseInt(a2.name.substring(1));
            return a1ColumnNumber.compareTo(a2ColumnNumber); // e.g. compares 10 and 2 for a10 and a2
        });
        return list;
    }

    private static void createPartitionedVerticalTable(Statement statement, String verticalStrName,
            String verticalIntName) throws Exception {
        String createStringTable = String.format("""
                CREATE TABLE %s (
                    oid INTEGER,
                    key VARCHAR(10),
                    val VARCHAR(10)
                )
                        """, verticalStrName);
        String createIntTable = String.format("""
                CREATE TABLE %s (
                    oid INTEGER,
                    key VARCHAR(10),
                    val INTEGER
                )
                """, verticalIntName);
        statement.execute(createStringTable);
        statement.execute(createIntTable);
    }

    /*
     * Creates a view in database to view vertical tables as horizontal tables.
     *
     * // NOTE: we could inline the two sub-views, if we don't want them to be
     * // accessible to the user
     */
    static void V2H(Connection conn, String horizontalRelation, int numMaxAttributes) throws Exception {
        Statement stmt = conn.createStatement();
        String verticalStrName = "vertical_str_" + horizontalRelation;
        String verticalIntName = "vertical_int_" + horizontalRelation;

        Pair<String, Integer> pair = createHorizontalViewOfPartition(stmt, verticalStrName, numMaxAttributes);
        String strViewName = pair.getFirst();
        int remainingMaxAttributes = pair.getSecond();
        Pair<String, Integer> pair2 = createHorizontalViewOfPartition(stmt, verticalIntName, remainingMaxAttributes);
        String intViewName = pair2.getFirst();

        stmt.execute(String.format("""
                CREATE VIEW h_view_vertical_%s AS
                SELECT *
                FROM %s AS str
                FULL OUTER JOIN %s AS int
                USING (oid)
                """, horizontalRelation, strViewName, intViewName));
    }

    /*
     * Creates a horizontal view on the given vertical partition of a relation.
     *
     * @return the name of the view and the remaining max. number of attributes
     */
    private static Pair<String, Integer> createHorizontalViewOfPartition(Statement stmt, String verticalStrRelation,
            int numMaxAttributes) throws Exception {
        ResultSet allStrAttributes = stmt
                .executeQuery(String.format("SELECT DISTINCT key FROM %s", verticalStrRelation));

        List<String> attributes = new ArrayList<>();
        for (int i = 0; allStrAttributes.next() && i < numMaxAttributes; i++) {
            attributes.add(allStrAttributes.getString("key"));
        }

        var sql = new StringBuilder(String.format(
                "CREATE VIEW view_%s AS SELECT v.oid as oid", verticalStrRelation));
        for (int i = 0; i < attributes.size(); i++) {
            sql.append(String.format(", v%d.val as %s ", i, attributes.get(i)));
        }
        sql.append(String.format("FROM ((SELECT DISTINCT oid from %s) AS v", verticalStrRelation));
        for (int i = 0; i < attributes.size(); i++) {
            sql.append(String.format(
                    " LEFT OUTER JOIN %s AS v%d ON (v.oid = v%d.oid AND v%d.key='%s')",
                    verticalStrRelation,
                    i,
                    i,
                    i,
                    attributes.get(i).toString()));
        }
        sql.append(") ORDER BY oid;");
        stmt.execute(sql.toString());

        int remainingMaxAttributes = numMaxAttributes - attributes.size(); // guaranteed >= 0
        return new Pair<>("view_" + verticalStrRelation, remainingMaxAttributes);
    }
}
