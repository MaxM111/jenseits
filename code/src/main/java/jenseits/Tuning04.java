package jenseits;

import tuning.setup.*;
import java.io.*;
import java.sql.*;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import java.util.*;
import java.util.stream.Collectors;

public class Tuning04 {
    private Connection conn;
    private long nanoTime;

    public Tuning04(Database database) throws SQLException, ClassNotFoundException {
        conn = DB.getConnection(database);
    }

    public void createTables() throws SQLException {
        Statement stmt = conn.createStatement();

        // clear tables
        stmt.execute("DROP TABLE IF EXISTS auth");
        stmt.execute("DROP TABLE IF EXISTS publ");

        stmt.execute("""
                CREATE TABLE auth (
                    name varchar(49),
                    pubID varchar(129)
                )
                """);

        stmt.execute("""
                CREATE TABLE publ (
                    pubID varchar(129),
                    type varchar(13),
                    title varchar(700),
                    booktitle varchar(132),
                    year varchar(4),
                    publisher varchar(196)
                )
                """);

        conn.commit();
    }

    // run for every change in the tables
    public void analyze() throws SQLException {
        var stmt = conn.createStatement();
        // collect statistics for query planner
        stmt.execute("ANALYZE auth");
        stmt.execute("ANALYZE publ");
        conn.commit();
    }

    public void fillTables() throws IOException, SQLException {
        new CopyManager(conn.unwrap(BaseConnection.class))
                .copyIn("COPY auth (name, pubID) FROM STDIN WITH (FORMAT text, DELIMITER E'\t')",
                        new BufferedReader(new FileReader("auth.tsv")));

        new CopyManager(conn.unwrap(BaseConnection.class))
                .copyIn("COPY publ (pubID, type, title, booktitle, year, publisher) FROM STDIN WITH (FORMAT text, DELIMITER E'\t')",
                        new BufferedReader(new FileReader("publ.tsv")));

        analyze();

        conn.commit();
    }

    // @return shuffled list of unique attribute values
    public List<String> getRandomValues(String table, String attr) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(String.format("SELECT %s FROM %s", attr, table));

        List<String> values = new ArrayList<>(100);
        while (rs.next()) {
            values.add(rs.getString(attr));
        }
        values = values.stream().distinct().collect(Collectors.toList());
        Collections.shuffle(values);
        System.out.println(table + "(" + attr + "): " + values.size() + " unique values");
        return values;
    }

    public void printExplainAnalyze(String query) throws SQLException {
        System.out.println("### " + query + " ###");

        var stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        var sb = new StringBuilder();
        while (rs.next()) {
            sb.append(rs.getString(1)).append("\n");
        }
        System.out.println(sb.toString());
    }

    public int executeQuery(String attr, List<String> values) throws SQLException {
        String prepString = String.format("SELECT * FROM publ WHERE %s = ?", attr);

        String query = prepString.split("\\?")[0];
        query = "EXPLAIN ANALYZE " + query + "\'" + values.get(0) + "\'";
        printExplainAnalyze(query);

        var stmt = conn.prepareStatement(prepString);

        long start = System.nanoTime();
        for (int i = 0;; i++) {
            stmt.setString(1, values.get(i % values.size()));

            stmt.executeQuery();

            if (isMinute(System.nanoTime() - start)) {
                this.nanoTime = System.nanoTime() - start;
                conn.commit();
                return i;
            }
        }
    }

    public int executeInQuery(List<String> values) throws SQLException {
        String prepString = "SELECT * FROM publ WHERE pubID IN (?, ?, ?)";

        String query = prepString.split("\\?")[0];
        query = "EXPLAIN ANALYZE " + query + "\'"
                + values.get(0) + "\', " + "\'"
                + values.get(1) + "\', " + "\'"
                + values.get(2) + "\')";
        printExplainAnalyze(query);

        var stmt = conn.prepareStatement(prepString);

        long start = System.nanoTime();
        for (int i = 0;; i = i + 3) {
            stmt.setString(1, values.get(i % (values.size())));
            stmt.setString(2, values.get((i + 1) % values.size()));
            stmt.setString(3, values.get((i + 2) % values.size()));

            stmt.executeQuery();

            if (isMinute(System.nanoTime() - start)) {
                this.nanoTime = System.nanoTime() - start;
                conn.commit();
                return i / 3;
            }
        }
    }

    public double getMillis() {
        return (double) this.nanoTime / 1_000_000;
    }

    private static Pair<Double, Double> runTests(Tuning04 t, String attr, List<String> values, boolean isINQuery)
            throws SQLException {
        int queryCount = isINQuery ? t.executeInQuery(values) : t.executeQuery(attr, values);

        double seconds = t.getMillis() / 1000;
        double queryPerSec = queryCount / seconds;
        double avgRuntimeMillis = t.getMillis() / queryCount;
        System.out.println(String.format(
                "%,d Queries in %,.2fs, Throughput: %,.2f Queries/s",
                queryCount,
                seconds,
                queryPerSec));
        return new Pair<>(avgRuntimeMillis, queryPerSec);
    }

    public Connection getConnection() throws SQLException {
        return conn;
    }

    private static boolean isMinute(long nanos) {
        return (double) nanos / 1_000_000_000 >= 10;
    }

    public static void main(String[] args) throws Exception {
        var tuning = new Tuning04(Database.POSTGRESQL);
        tuning.createTables();
        tuning.fillTables();

        System.out.println("Creating lists");
        var pubIDList = tuning.getRandomValues("publ", "pubID");
        var booktitleList = tuning.getRandomValues("publ", "booktitle");
        var authorList = tuning.getRandomValues("auth", "pubID");
        var yearList = tuning.getRandomValues("publ", "year");
        System.out.println("Created lists");
        System.out.println("---------------------");

        // table scan (no index)
        System.out.println("Started Table Scan");
        Pair<Double, Double> avgTSQ1 = runTests(tuning, "pubID", pubIDList, false);
        System.out.println("---------------------");
        Pair<Double, Double> avgTSQ2 = runTests(tuning, "booktitle", booktitleList, false);
        System.out.println("---------------------");
        Pair<Double, Double> avgTSQ3 = runTests(tuning, "pubID", authorList, true);
        System.out.println("---------------------");
        Pair<Double, Double> avgTSQ4 = runTests(tuning, "year", yearList, false);
        System.out.println("---------------------");

        System.out.print("Preparing indexes for hash...");
        Connection conn = tuning.getConnection();
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE INDEX idx_pubID on publ USING hash (pubID)");
        stmt.execute("CREATE INDEX idx_year on publ USING hash (year)");
        stmt.execute("CREATE INDEX idx_booktitle on publ USING hash (booktitle)");
        stmt.execute("CREATE INDEX idx_publisher on publ USING btree (publisher)");
        stmt.execute("CLUSTER publ USING idx_publisher"); // cluster on unused index
        tuning.analyze(); // analyse tables for the planner
        conn.commit();
        System.out.println(" - Indexes created");
        System.out.println("---------------------");

        // non clustering hash index
        System.out.println("Started hash index runs");
        Pair<Double, Double> avgHNQ1 = runTests(tuning, "pubID", pubIDList, false);
        System.out.println("---------------------");
        Pair<Double, Double> avgHNQ2 = runTests(tuning, "booktitle", booktitleList, false);
        System.out.println("---------------------");
        Pair<Double, Double> avgHNQ3 = runTests(tuning, "pubID", authorList, true);
        System.out.println("---------------------");
        Pair<Double, Double> avgHNQ4 = runTests(tuning, "year", yearList, false);
        System.out.println("---------------------");

        stmt.execute("DROP INDEX idx_pubID");
        stmt.execute("DROP INDEX idx_booktitle");
        stmt.execute("DROP INDEX idx_year");
        conn.commit();

        // non clustering B+ tree
        System.out.print("Preparing indexes for non-clustering b+tree...");
        stmt.execute("CREATE INDEX idx_pubID on publ USING btree (pubID)");
        stmt.execute("CREATE INDEX idx_year on publ USING btree (year)");
        stmt.execute("CREATE INDEX idx_booktitle on publ USING btree (booktitle)");
        tuning.analyze(); // analyse tables for the planner
        conn.commit();
        System.out.println(" - Indexes created");
        System.out.println("---------------------");

        System.out.println("Started non-clustering b+tree index runs");
        Pair<Double, Double> avgBNQ1 = runTests(tuning, "pubID", pubIDList, false);
        System.out.println("---------------------");
        Pair<Double, Double> avgBNQ2 = runTests(tuning, "booktitle", booktitleList, false);
        System.out.println("---------------------");
        Pair<Double, Double> avgBNQ3 = runTests(tuning, "pubID", authorList, true);
        System.out.println("---------------------");
        Pair<Double, Double> avgBNQ4 = runTests(tuning, "year", yearList, false);
        System.out.println("---------------------");

        // clustering B+ tree
        System.out.print("Clustering table...");
        stmt.execute("CLUSTER publ USING idx_pubID");
        tuning.analyze(); // analyse tables for the planner
        conn.commit();
        System.out.println(" - Clustering completed");
        System.out.println("---------------------");

        System.out.println("Started clustering b+tree index run");
        Pair<Double, Double> avgBCQ1 = runTests(tuning, "pubID", pubIDList, false);
        System.out.println("---------------------");
        Pair<Double, Double> avgBCQ3 = runTests(tuning, "pubID", authorList, true);
        System.out.println("---------------------");

        System.out.print("Clustering table by idx_booktitle...");
        stmt.execute("CLUSTER publ USING idx_booktitle");
        tuning.analyze(); // analyse tables for the planner
        conn.commit();
        System.out.println(" - Clustering completed");
        System.out.println("Started clustering b+tree index run");
        Pair<Double, Double> avgBCQ2 = runTests(tuning, "booktitle", booktitleList, false);
        System.out.println("---------------------");

        System.out.print("Clustering table by idx_year...");
        stmt.execute("CLUSTER publ USING idx_year");
        tuning.analyze(); // analyse tables for the planner
        conn.commit();
        System.out.println(" - Clustering completed");
        System.out.println("Started clustering b+tree index run");
        Pair<Double, Double> avgBCQ4 = runTests(tuning, "year", yearList, false);
        System.out.println("---------------------");

        // print results
        System.out.println("----- table scan (no index) -----");
        System.out.println(String.format("%,.4f ms | %,.4f ms | %,.4f ms | %,.4f ms", avgTSQ1.getFirst(),
                avgTSQ2.getFirst(), avgTSQ3.getFirst(), avgTSQ4.getFirst()));
        System.out.println("----- non clustering hash index -----");
        System.out.println(String.format("%,.4f ms | %,.4f ms | %,.4f ms | %,.4f ms", avgHNQ1.getFirst(),
                avgHNQ2.getFirst(), avgHNQ3.getFirst(), avgHNQ4.getFirst()));
        System.out.println("----- non clustering B+ tree -----");
        System.out.println(String.format("%,.4f ms | %,.4f ms | %,.4f ms | %,.4f ms", avgBNQ1.getFirst(),
                avgBNQ2.getFirst(), avgBNQ3.getFirst(), avgBNQ4.getFirst()));
        System.out.println("----- clustering B+ tree -----");
        System.out.println(String.format("%,.4f ms | %,.4f ms | %,.4f ms | %,.4f ms", avgBCQ1.getFirst(),
                avgBCQ2.getFirst(), avgBCQ3.getFirst(), avgBCQ4.getFirst()));
        System.out.println();

        // print results
        System.out.println("----- table scan (no index) -----");
        System.out.println(String.format("%,.4f Q/s | %,.4f Q/s | %,.4f Q/s | %,.4f Q/s", avgTSQ1.getSecond(),
                avgTSQ2.getSecond(), avgTSQ3.getSecond(), avgTSQ4.getSecond()));
        System.out.println("----- non clustering hash index -----");
        System.out.println(String.format("%,.4f Q/s | %,.4f Q/s | %,.4f Q/s | %,.4f Q/s", avgHNQ1.getSecond(),
                avgHNQ2.getSecond(), avgHNQ3.getSecond(), avgHNQ4.getSecond()));
        System.out.println("----- non clustering B+ tree -----");
        System.out.println(String.format("%,.4f Q/s | %,.4f Q/s | %,.4f Q/s | %,.4f Q/s", avgBNQ1.getSecond(),
                avgBNQ2.getSecond(), avgBNQ3.getSecond(), avgBNQ4.getSecond()));
        System.out.println("----- clustering B+ tree -----");
        System.out.println(String.format("%,.4f Q/s | %,.4f Q/s | %,.4f Q/s | %,.4f Q/s", avgBCQ1.getSecond(),
                avgBCQ2.getSecond(), avgBCQ3.getSecond(), avgBCQ4.getSecond()));

    }
}
