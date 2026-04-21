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
import java.lang.StringBuilder;

import java.sql.DatabaseMetaData;

import jenseits.setup.DB;
import jenseits.setup.Database;
import jenseits.setup.Pair;
import jenseits.util.*;

public class Jenseits01 {

    private static Logger logger;

    public static void main(String[] args) throws Exception {
        logger = new Logger("logs", "log.csv");
        var conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);

        infrastructureDemo(conn);

        createTableHorizontal(conn);
        fillValuesHorizontal(conn);

        createTableVertical(conn);
        fillValueVerticalManually(conn);

        generate(conn, 10, 0.3, 10);

        partitionV_toy(conn);

        H2V(conn, "generated");
        V2H(conn, "generated", 9);

        H2V(conn, "h_toy");
        V2H(conn, "h_toy", 100);

        benchmark(conn);
        logger.close();
    }

    // ----------------------- PHASE 1 -----------------------

    static void infrastructureDemo(Connection conn) throws Exception {
        var stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS demotable");
        stmt.execute("CREATE TABLE demotable (name VARCHAR(50), age INTEGER)");

        var valuesSql = "INSERT INTO demotable (name, age) VALUES (?, ?)";
        var prepStmt = conn.prepareStatement(valuesSql);
        prepStmt.setString(1, "Sam");
        prepStmt.setInt(2, 21);
        prepStmt.execute();
        prepStmt.setString(1, "Max");
        prepStmt.setInt(2, 32);
        prepStmt.execute();

        var querySamAge = """
                SELECT age
                FROM demotable
                WHERE demotable.name = 'Sam'
                """; // remember: use single quotes for postgresql
        ResultSet results = conn.createStatement().executeQuery(querySamAge);
        if (results.next()) {
            IO.println("Age of Sam : " + results.getInt("age"));
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

        stmt.execute("DROP TABLE IF EXISTS vertical_int_v_toy, vertical_str_v_toy CASCADE");

        stmt.execute("CREATE TABLE vertical_int_v_toy (oid INTEGER, key VARCHAR(10), val INTEGER)");
        stmt.execute("CREATE TABLE vertical_str_v_toy (oid INTEGER, key VARCHAR(10), val VARCHAR(10))");

        stmt.execute("""
                INSERT INTO vertical_int_v_toy (oid, key, val)
                SELECT oid, key, val::INTEGER FROM v_toy WHERE val ~ '^[0-9]+$';
                    """);
        stmt.execute("""
                INSERT INTO vertical_str_v_toy (oid, key, val)
                SELECT oid, key, val FROM v_toy WHERE val !~ '^[0-9]+$';
                    """);

        stmt.execute("DROP VIEW IF EXISTS v_toy_all;");
        stmt.execute("""
                CREATE view v_toy_all AS
                SELECT oid, key, val::VARCHAR(10) as val FROM vertical_int_v_toy
                UNION
                SELECT oid,key,val FROM vertical_str_v_toy
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
    static TableData generate(Connection conn, int numTuples, double sparsity, int numAttributes)
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
        List<Integer> intValues = new ArrayList<>();
        List<String> varcharValues = new ArrayList<>();
        for (int i = 0; i < numTuples; i++) {
            setGeneratedRowValues(prepStmt, i + 1, attributes, sparsity, intValues, varcharValues);
        }

        showCorrectnessWithViews(conn, attributes);

        // in the rare event that all values are null, generate again
        if (intValues.isEmpty() || varcharValues.isEmpty()) {
            return generate(conn, numTuples, sparsity, numAttributes);
        }

        return new TableData(attributes, intValues, varcharValues);
    }

    private static void setGeneratedRowValues(PreparedStatement prepStmt, int oid, Attribute[] attributes,
            double sparsity, List<Integer> intValues, List<String> varcharValues)
            throws Exception {
        prepStmt.setInt(1, oid);
        for (int j = 0; j < attributes.length; j++) {
            var attribute = attributes[j];
            int paramIdx = j + 2; // set<Type> is not 0-indexed, first column reserved for oid
            setAttributeValue(prepStmt, attribute, paramIdx, sparsity, intValues, varcharValues);
        }

        prepStmt.execute();
    }

    private static void setAttributeValue(PreparedStatement prepStmt, Attribute attribute, int paramIdx,
            double sparsity, List<Integer> intValues, List<String> varcharValues) throws Exception {
        var isNull = new Random().nextDouble() <= sparsity;

        switch (attribute.type) {
            case AttributeType.Integer -> {
                if (isNull) {
                    prepStmt.setNull(paramIdx, java.sql.Types.INTEGER);
                } else {
                    int val = attribute.intGenerator.next();
                    prepStmt.setInt(paramIdx, val);
                    intValues.add(val);
                }
            }

            case AttributeType.String -> {
                if (isNull) {
                    prepStmt.setNull(paramIdx, java.sql.Types.VARCHAR);
                } else {
                    var val = attribute.varcharGenerator.next();
                    prepStmt.setString(paramIdx, val);
                    varcharValues.add(val);
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
                case String -> "VARCHAR(100)";
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
        String primaryKeyTable = "primary_keys_" + horizontalRelation;
        var statement = conn.createStatement();
        statement.execute(String.format("DROP TABLE IF EXISTS %s, %s, %s CASCADE", verticalStrName, verticalIntName,
                primaryKeyTable));

        List<Attribute> attributes = queryAttributes(conn, horizontalRelation);

        createPartitionedVerticalTable(statement, verticalStrName, verticalIntName, primaryKeyTable);

        String query = String.format("SELECT * FROM %s", horizontalRelation);
        ResultSet rows = conn.createStatement().executeQuery(query);

        while (rows.next()) {
            transformRow(conn, rows, attributes, verticalStrName, verticalIntName, primaryKeyTable);
        }
    }

    private static void transformRow(Connection conn, ResultSet rows, List<Attribute> attributes,
            String verticalStrName, String verticalIntName, String primaryKeysTableName) throws Exception {
        int oid = rows.getInt("oid");
        conn.createStatement().execute(String.format("INSERT INTO %s VALUES (%d)", primaryKeysTableName, oid));
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
            String verticalIntName, String primaryKeysTableName) throws Exception {
        String createStringTable = String.format("""
                CREATE TABLE %s (
                    oid INTEGER,
                    key VARCHAR(10),
                    val VARCHAR(100)
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
        statement.execute(String.format("CREATE TABLE %s (oid INTEGER)", primaryKeysTableName));
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
        String primaryKeyName = "primary_keys_" + horizontalRelation;

        DatabaseMetaData meta = conn.getMetaData();
        ResultSet primaryKeyResults = meta.getPrimaryKeys(null, null, verticalStrName);
        String primaryKey = null;
        if (primaryKeyResults.next()) {
            primaryKey = primaryKeyResults.getString("COLUMN_NAME");
        }
        primaryKeyResults.close();
        if (primaryKey == null) {
            primaryKey = "oid";
        }

        Pair<String, Integer> pair = createHorizontalViewOfPartition(stmt, verticalStrName, numMaxAttributes,
                primaryKey);
        String strViewName = pair.getFirst();
        int remainingMaxAttributes = pair.getSecond();
        Pair<String, Integer> pair2 = createHorizontalViewOfPartition(stmt, verticalIntName, remainingMaxAttributes,
                primaryKey);
        String intViewName = pair2.getFirst();

        stmt.execute(String.format("""
                CREATE VIEW h_view_vertical_%s AS
                SELECT *
                FROM %s
                FULL OUTER JOIN %s
                USING (oid)
                FULL OUTER JOIN %s
                USING (oid)
                """, horizontalRelation, strViewName, intViewName, primaryKeyName));
    }

    /*
     * Creates a horizontal view on the given vertical partition of a relation.
     *
     * @return the name of the view and the remaining max. number of attributes
     */
    private static Pair<String, Integer> createHorizontalViewOfPartition(Statement stmt, String verticalStrRelation,
            int numMaxAttributes, String primaryKey) throws Exception {

        ResultSet allStrAttributes = stmt
                .executeQuery(String.format("SELECT DISTINCT key FROM %s", verticalStrRelation));

        List<String> attributes = new ArrayList<>();
        for (int i = 0; allStrAttributes.next() && i < numMaxAttributes; i++) {
            attributes.add(allStrAttributes.getString("key"));
        }

        var sql = new StringBuilder(String.format(
                "CREATE VIEW view_%s AS SELECT v.%s AS %s", verticalStrRelation, primaryKey, primaryKey));
        for (int i = 0; i < attributes.size(); i++) {
            sql.append(String.format(", v%d.val AS %s ", i, attributes.get(i)));
        }
        sql.append(String.format(" FROM ((SELECT DISTINCT %s FROM %s) AS v", primaryKey, verticalStrRelation));
        for (int i = 0; i < attributes.size(); i++) {
            sql.append(String.format(
                    " LEFT OUTER JOIN %s AS v%d ON (v.%s = v%d.%s AND v%d.key='%s')",
                    verticalStrRelation,
                    i,
                    primaryKey,
                    i,
                    primaryKey,
                    i,
                    attributes.get(i).toString()));
        }
        sql.append(String.format(") ORDER BY %s", primaryKey));
        stmt.execute(sql.toString());

        int remainingMaxAttributes = numMaxAttributes - attributes.size(); // guaranteed >= 0
        return new Pair<>("view_" + verticalStrRelation, remainingMaxAttributes);
    }

    // Benchmarking

    /*
     * |H| := #tuples
     * |A| := #attributes
     * S := sparsity (\in [0, 1))
     *
     * Measures how horizontal and vertical representations compare in terms of
     * queries in a minute.
     */
    static void benchmark(Connection conn) throws Exception {
        int[] tupleCounts = new int[] { 2000, 4000, 8000 };
        int[] attributeCounts = new int[] { 5, 10, 15 };
        double[] sparsityValues = new double[] { 1.0 - 1.0 / 2.0, 1.0 - 1.0 / 4.0,
                1.0 - (1.0 / 16.0) };

        var stmt = conn.createStatement();
        int unitInSeconds = 10; // unit in which we count the number of queries

        logger.log("representation", "tupleCount", "attributeCount", "sparsity", "tableSize", "queryCount1",
                "queryCount2", "duration");

        for (var tupleCount : tupleCounts) {
            for (var attributeCount : attributeCounts) {
                for (var sparsity : sparsityValues) {
                    IO.println("----------------------");
                    IO.println("#Tuples: " + tupleCount);
                    IO.println("#Attributes: " + attributeCount);
                    IO.println("Sparsity: " + sparsity);
                    IO.println("----------------------");
                    IO.println("  Horizontal: ");
                    logger.logPartial("Horizontal", String.valueOf(tupleCount),
                            String.valueOf(attributeCount),
                            String.valueOf(sparsity));
                    horizontalBenchmark(conn, stmt, tupleCount, sparsity, attributeCount,
                            unitInSeconds);
                    IO.println("  Vertical: ");
                    logger.logPartial("Vertical", String.valueOf(tupleCount),
                            String.valueOf(attributeCount),
                            String.valueOf(sparsity));

                    verticalBenchmark(conn, stmt, tupleCount, sparsity, attributeCount,
                            unitInSeconds);
                    IO.println("  Vertical Optimized: ");
                    logger.logPartial("Vertical Optimized", String.valueOf(tupleCount),
                            String.valueOf(attributeCount),
                            String.valueOf(sparsity));
                    verticalBenchmarkOpt(conn, stmt, tupleCount, sparsity, attributeCount, unitInSeconds, false);
                    IO.println("  Vertical Functions: ");
                    logger.logPartial("Vertical Functions", String.valueOf(tupleCount),
                            String.valueOf(attributeCount),
                            String.valueOf(sparsity));
                    verticalBenchmarkOpt(conn, stmt, tupleCount, sparsity, attributeCount, unitInSeconds, true);
                    logger.flush();
                }
            }
        }
    }

    private static void horizontalBenchmark(Connection conn, Statement stmt, int tupleCount, double sparsity,
            int attributeCount, int unitInSeconds) throws Exception {
        stmt.execute("DROP TABLE IF EXISTS generated CASCADE");
        var tableData = generate(conn, tupleCount, sparsity, attributeCount);
        String table = "generated"; // as defined in generate()

        long tableSize = tableSize(conn, table);
        logger.logPartial(String.valueOf(tableSize));
        IO.println("    Size: " + tableSize + " Bytes");

        benchmarkTable(stmt, table, tableData, unitInSeconds, tupleCount, sparsity, attributeCount);
    }

    private static void verticalBenchmark(Connection conn, Statement stmt, int tupleCount, double sparsity,
            int attributeCount, int unitInSeconds) throws Exception {
        stmt.execute("DROP TABLE IF EXISTS generated CASCADE");
        var tableData = generate(conn, tupleCount, sparsity, attributeCount);
        H2V(conn, "generated"); // transform into vertical representation
        V2H(conn, "generated", 20); // create view to access using a horizontal view
        String table = "h_view_vertical_generated"; // as defined in V2H()

        long tableSize = tableSize(conn, "vertical_str_generated") + tableSize(conn, "vertical_int_generated")
                + tableSize(conn, "primary_keys_generated");
        logger.logPartial(String.valueOf(tableSize));
        IO.println("    Size: " + tableSize + " Bytes");
        benchmarkTable(stmt, table, tableData, unitInSeconds, tupleCount, sparsity, attributeCount);
    }

    private static void benchmarkTable(Statement stmt, String table, TableData tableData, int unitInSeconds,
            int tupleCount, double sparsity, int attributeCount) throws Exception {
        var rand = new Random();
        var attributes = tableData.attributes;
        var intValues = tableData.intValues;
        var varcharValues = tableData.varcharValues;

        int queryCount1 = 0;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < unitInSeconds * 1_000) {
            queryCount1++;

            var query1 = String.format("SELECT * FROM %s WHERE oid = %d",
                    table,
                    rand.nextInt(1, tupleCount + 1)); // see generate()
            stmt.execute(query1);
        }

        int queryCount2 = 0;
        start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < unitInSeconds * 1_000) {
            queryCount2++;

            int attributeNum = rand.nextInt(1, attributeCount + 1);
            String query2;
            if (attributes[attributeNum - 1].type == AttributeType.Integer) {
                query2 = String.format(
                        "SELECT oid FROM %s WHERE a%d = %s",
                        table,
                        attributeNum,
                        intValues.get(rand.nextInt(intValues.size())));
            } else {
                query2 = String.format(
                        "SELECT oid FROM %s WHERE a%d = '%s'",
                        table,
                        attributeNum,
                        varcharValues.get(rand.nextInt(varcharValues.size())));
            }
            stmt.execute(query2);
        }
        IO.println("    Result: " + queryCount1 + " query1, " + queryCount2 + " query2 in " + unitInSeconds + "s");
        logger.log(String.valueOf(queryCount1), String.valueOf(queryCount2), String.valueOf(unitInSeconds));
    }

    /*
     * @return size of the table in bytes
     */
    private static long tableSize(Connection conn, String table) throws Exception {
        ResultSet result = conn.createStatement()
                .executeQuery(
                        String.format("SELECT pg_total_relation_size('\"%s\"') AS size", table)); // includes index size
        return result.next() ? result.getInt(1) : -1;
    }

    record TableData(Attribute[] attributes, List<Integer> intValues, List<String> varcharValues) {
    }

    /*
     * Benchmarks the vertical tables using optimizations
     */
    private static void verticalBenchmarkOpt(Connection conn, Statement stmt, int tupleCount, double sparsity,
            int attributeCount, int unitInSeconds, boolean useFunctions) throws Exception {
        stmt.execute("DROP TABLE IF EXISTS generated CASCADE");
        var tableData = generate(conn, tupleCount, sparsity, attributeCount);
        H2V(conn, "generated"); // transform into vertical representation
        V2H(conn, "generated", 20); // create view to access using a horizontal view
        String table = "h_view_vertical_generated"; // as defined in V2H()
        createIndex(conn, "vertical_str_generated");
        createIndex(conn, "vertical_int_generated");
        createDBMSFunction_q_i(conn, table, tableData);
        createDBMSFunction_q_ii(conn, table, tableData);

        long tableSize = tableSize(conn, "vertical_str_generated") + tableSize(conn, "vertical_int_generated")
                + tableSize(conn, "primary_keys_generated");
        IO.println("    Size: " + tableSize + " Bytes");
        logger.logPartial(String.valueOf(tableSize));

        if (useFunctions) {
            benchmarkTableOpt(conn, stmt, table, tableData, unitInSeconds, tupleCount, sparsity, attributeCount, true);
        } else {
            benchmarkTableOpt(conn, stmt, table, tableData, unitInSeconds, tupleCount, sparsity, attributeCount, false);
        }
    }

    private static void createIndex(Connection conn, String table) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute(String.format("DROP INDEX IF EXISTS %s_oid_key_val;", table));
        stmt.execute(String.format("CREATE INDEX %s_oid_key ON %s (oid,key);", table, table));
    }

    /*
     * Benchmarks a table using Optimizations
     */
    private static void benchmarkTableOpt(Connection conn, Statement stmt, String table, TableData tableData,
            int unitInSeconds,
            int tupleCount, double sparsity, int attributeCount, boolean useDBMSFunction) throws Exception {
        var rand = new Random();
        var attributes = tableData.attributes;
        var intValues = tableData.intValues;
        var varcharValues = tableData.varcharValues;

        int queryCount1 = 0;
        long start = System.currentTimeMillis();
        var query1 = useDBMSFunction ? String.format("SELECT * FROM q_i(?)", table)
                : String.format("SELECT * FROM %s WHERE oid = ?", table); // see generate()
        PreparedStatement prepStmt1 = conn.prepareStatement(query1);
        while (System.currentTimeMillis() - start < unitInSeconds * 1_000) {
            queryCount1++;
            prepStmt1.setInt(1, rand.nextInt(1, tupleCount + 1));
            prepStmt1.execute();
        }

        int queryCount2 = 0;
        start = System.currentTimeMillis();
        String query2;
        query2 = useDBMSFunction ? "SELECT * FROM q_ii(?, ?)"
                : String.format(
                        "SELECT oid FROM %s WHERE ? = ?",
                        table);
        PreparedStatement prepStmt2 = conn.prepareStatement(query2);

        while (System.currentTimeMillis() - start < unitInSeconds * 1_000) {
            queryCount2++;

            int attributeNum = rand.nextInt(1, attributeCount + 1);

            if (attributes[attributeNum - 1].type == AttributeType.Integer) {
                prepStmt2.setString(1, "a" + attributeNum);
                prepStmt2.setString(2, Integer.toString(intValues.get(rand.nextInt(intValues.size()))));
            } else {
                prepStmt2.setString(1, "a" + attributeNum);
                prepStmt2.setString(2, varcharValues.get(rand.nextInt(varcharValues.size())));
            }
            prepStmt2.execute();
        }
        IO.println("    Result: " + queryCount1 + " query1, " + queryCount2 + " query2 in " + unitInSeconds + "s");
        logger.log(String.valueOf(queryCount1), String.valueOf(queryCount2), String.valueOf(unitInSeconds));
    }

    // Note on SQL procedures: We order the attributes, so that oid is first, then
    // the strings, then the integers. This makes
    // the return type consistent.

    private static void createDBMSFunction_q_i(Connection conn, String table, TableData tableData) throws Exception {
        Statement stmt = conn.createStatement();
        String verticalStrName = "vertical_str_generated";
        String verticalIntName = "vertical_int_generated";

        String subqueryStr = buildSubqueryHorizontalFromVerticalPartition(tableData, verticalStrName,
                AttributeType.String, "WHERE oid = par_oid", false);
        String subqueryInt = buildSubqueryHorizontalFromVerticalPartition(tableData, verticalIntName,
                AttributeType.Integer, "WHERE oid = par_oid", false);

        stmt.execute("DROP FUNCTION IF EXISTS q_i(INTEGER);");
        StringBuilder qb = new StringBuilder(
                "CREATE FUNCTION q_i (par_oid INTEGER) RETURNS TABLE(oid INTEGER");
        var attributes = tableData.attributes;
        for (var attribute : attributes) {
            if (attribute.type == AttributeType.String) {
                qb.append(", ");
                qb.append(attribute.name);
                qb.append(String.format(" %s", attribute.type.sqlType()));
            }
        }
        for (var attribute : attributes) {
            if (attribute.type == AttributeType.Integer) {
                qb.append(", ");
                qb.append(attribute.name);
                qb.append(String.format(" %s", attribute.type.sqlType()));
            }
        }
        qb.append(") LANGUAGE SQL STABLE AS $$ ");
        qb.append("SELECT * FROM " + subqueryStr + " JOIN " + subqueryInt);
        qb.append(" USING (oid)");
        qb.append(" $$;");
        stmt.execute(qb.toString());
    }

    /*
     * To ensure consistent attribute order, this method should be called twice:
     * Once for attributes of type varchar and once for attributes of type integer
     */
    private static String buildSubqueryHorizontalFromVerticalPartition(TableData tableData, String tableName,
            AttributeType type, String whereClause, boolean use_q_ii_condition) {
        var attributes = tableData.attributes;
        StringBuilder qb = new StringBuilder();
        qb.append("(SELECT v.oid as oid");
        int i = 0;
        for (var attribute : attributes) {
            if (attribute.type == type) {
                qb.append(String.format(", v%d.val as %s ", i, attribute.name));
            }
            i++;
        }
        qb.append(String.format("FROM ((SELECT DISTINCT oid from %s %s) AS v\n",
                tableName, whereClause));
        int j = 0;
        for (var attribute : attributes) {
            if (attribute.type == type) {
                qb.append(String.format(
                        " LEFT OUTER JOIN %s AS v%d ON (v.oid = v%d.oid AND v%d.key = '%s' %s)\n",
                        tableName,
                        j,
                        j,
                        j,
                        attribute.name,
                        false ? String.format("AND v%d.val = param_value", j) : ""));
            }
            j++;
        }
        qb.append(") ORDER BY oid)");
        return qb.toString();
    }

    private static String buildSubqueryHorizontalFromVerticalPartition2(TableData tableData, String stringTableName,
            String intTableName, AttributeType firstType, String whereClause, boolean use_q_ii_condition) {
        var attributes = tableData.attributes;
        var firstPartitionTableName = firstType == AttributeType.String ? stringTableName : intTableName;
        var secondPartitionTableName = firstType == AttributeType.String ? intTableName : stringTableName;

        StringBuilder qb = new StringBuilder();
        qb.append("(SELECT v.oid as oid");

        int i = 0;
        for (var attribute : attributes) {
            if (attribute.type == AttributeType.String) {
                qb.append(String.format(", v%d.val as %s ", i, attribute.name));
            }
            i++;
        }

        i = 0;
        for (var attribute : attributes) {
            if (attribute.type == AttributeType.Integer) {
                qb.append(String.format(", v%d.val as %s ", i, attribute.name));
            }
            i++;
        }
        qb.append(String.format("FROM ((SELECT DISTINCT oid from %s %s) AS v\n", firstPartitionTableName, whereClause));

        int j = 0;
        for (var attribute : attributes) {
            if (attribute.type == firstType) {
                qb.append(String.format(
                        " LEFT OUTER JOIN %s AS v%d ON (v.oid = v%d.oid AND v%d.key = '%s')\n",
                        firstPartitionTableName,
                        j,
                        j,
                        j,
                        attribute.name));
            }
            j++;
        }

        j = 0;
        for (var attribute : attributes) {
            if (attribute.type != firstType) {
                qb.append(String.format(
                        " LEFT OUTER JOIN %s AS v%d ON (v.oid = v%d.oid AND v%d.key = '%s')\n",
                        secondPartitionTableName,
                        j,
                        j,
                        j,
                        attribute.name));
            }
            j++;
        }

        qb.append(") ORDER BY oid)");
        return qb.toString();
    }

    /*
     * We overload the function to work with both string and integer parameters.
     */
    private static void createDBMSFunction_q_ii(Connection conn, String table, TableData tableData) throws Exception {
        Statement stmt = conn.createStatement();
        String verticalStrName = "vertical_str_generated";
        String verticalIntName = "vertical_int_generated";
        var attributes = tableData.attributes;

        // function 1
        stmt.execute("DROP FUNCTION IF EXISTS q_ii(VARCHAR(10), INTEGER)");

        String resultRowIntQuery = buildSubqueryHorizontalFromVerticalPartition2(tableData, verticalStrName,
                verticalIntName, AttributeType.Integer, "WHERE key = param_a_i AND val = param_value", true);

        StringBuilder qb = new StringBuilder(
                "CREATE FUNCTION q_ii(param_a_i VARCHAR(10), param_value INTEGER) RETURNS TABLE(oid INTEGER");
        appendFunctionDefinition(qb, attributes, resultRowIntQuery, AttributeType.Integer);
        IO.println(qb.toString());
        stmt.execute(qb.toString());

        // function 2
        stmt.execute("DROP FUNCTION IF EXISTS q_ii(VARCHAR(10), VARCHAR(100))");

        String resultRowStrQuery = buildSubqueryHorizontalFromVerticalPartition2(tableData, verticalStrName,
                verticalIntName, AttributeType.String, "WHERE key = param_a_i AND val = param_value", true);

        StringBuilder builder = new StringBuilder(
                "CREATE FUNCTION q_ii(param_a_i VARCHAR(10), param_value VARCHAR(100)) RETURNS TABLE(oid INTEGER");
        appendFunctionDefinition(builder, attributes, resultRowStrQuery, AttributeType.String);
        stmt.execute(builder.toString());
    }

    private static void appendFunctionDefinition(StringBuilder builder, Attribute[] attributes, String query,
            AttributeType type) {
        for (var attribute : attributes) {
            if (attribute.type == AttributeType.String) {
                builder.append(", ");
                builder.append(attribute.name);
                builder.append(String.format(" %s", attribute.type.sqlType()));
            }
        }
        for (var attribute : attributes) {
            if (attribute.type == AttributeType.Integer) {
                builder.append(", ");
                builder.append(attribute.name);
                builder.append(String.format(" %s", attribute.type.sqlType()));
            }
        }
        builder.append(") LANGUAGE SQL STABLE AS $$ ");
        builder.append("SELECT * FROM " + query);
        builder.append(" $$");
    }
}
