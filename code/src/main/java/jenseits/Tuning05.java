package jenseits;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import jenseits.setup.DB;
import jenseits.setup.Database;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Tuning05 {
    private Connection conn;
    private long nanoTime;

    public Tuning05(Database database) throws SQLException, ClassNotFoundException {
        conn = DB.getConnection(database);
    }

    /**
     * Creates new Statement object from connection
     *
     * @return new Statement object generated from the internal connection
     * @throws SQLException
     */
    public Statement createStatement() throws SQLException {
        return conn.createStatement();
    }

    /**
     * Runs ANALYZE and then commits
     *
     * @throws SQLException
     */
    public void commit() throws SQLException {
        analyze();
        conn.commit();
    }

    /**
     * Drops, creates and fills up tables
     * 
     * @throws IOException
     * @throws SQLException
     */
    public void init() throws IOException, SQLException {
        createTables();
        fillTables();
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

        commit();
    }

    private void analyze() throws SQLException {
        var stmt = conn.createStatement();
        // collect statistics for query planner
        stmt.execute("ANALYZE auth");
        stmt.execute("ANALYZE publ");
    }

    public void fillTables() throws IOException, SQLException {
        new CopyManager(conn.unwrap(BaseConnection.class))
                .copyIn("COPY auth (name, pubID) FROM STDIN WITH (FORMAT text, DELIMITER E'\t')",
                        new BufferedReader(new FileReader("auth.tsv")));

        new CopyManager(conn.unwrap(BaseConnection.class))
                .copyIn("COPY publ (pubID, type, title, booktitle, year, publisher) FROM STDIN WITH (FORMAT text, DELIMITER E'\t')",
                        new BufferedReader(new FileReader("publ.tsv")));

        analyze();

        commit();
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

    public void executeQuery1(boolean explain) throws SQLException {
        String query = "SELECT name, title FROM auth, publ WHERE auth.pubID = publ.pubID";

        if (explain) {
            System.out.println("---------------------------------------");
            printExplainAnalyze("EXPLAIN ANALYZE " + query);
        }

        Statement stmt = conn.createStatement();

        long start = System.nanoTime();
        stmt.execute(query);
        this.nanoTime = System.nanoTime() - start;
        commit();
    }

    public void executeQuery2(boolean explain) throws SQLException {
        String query = "SELECT title FROM auth, publ WHERE auth.pubID = publ.pubID AND auth.name = 'Divesh Srivastava'";

        if (explain) {
            printExplainAnalyze("EXPLAIN ANALYZE " + query);
        }

        Statement stmt = conn.createStatement();

        long start = System.nanoTime();
        stmt.execute(query);
        this.nanoTime = System.nanoTime() - start;
        commit();
    }

    public double getMillis() {
        return (double) this.nanoTime / 1_000_000;
    }

    private static void runTests(Tuning05 t, String testName, boolean explain)
            throws SQLException {
        t.executeQuery1(explain);
        double timeQ1 = t.getMillis();

        t.executeQuery2(explain);
        double timeQ2 = t.getMillis();

        System.out.println(String.format("  %s: Query 1: %,.2fms | Query 2: %,.2fms", testName, timeQ1, timeQ2));
    }

    public static void main(String[] args) throws Exception {
        boolean explain = true;

        // initialize database
        System.out.println("Initializing Database");
        var tuning = new Tuning05(Database.POSTGRESQL);
        var stmt = tuning.createStatement();

        // --- Join Strategy Proposed by System ---
        tuning.init();
        System.out.println("Join Strategy Proposed by System:");
        // no index
        runTests(tuning, "SYS_NO_INDEX", explain);
        // unique non-clustering on publ.pubID
        stmt.execute("CREATE INDEX idx_publ_pubID on publ(pubID)");
        tuning.commit();
        runTests(tuning, "SYS_NON_CLUSTERING(publ.pubID)", explain);
        // clustering on publ.pubID and auth.pubID
        stmt.execute("CLUSTER publ USING idx_publ_pubID");
        stmt.execute("CREATE INDEX idx_auth_pubID on auth(pubID)");
        stmt.execute("CLUSTER auth USING idx_auth_pubID");
        tuning.commit();
        runTests(tuning, "SYS_CLUSTERING(publ.pubID & auth.pubID)", explain);

        // Index Nested Loop Join
        tuning.init();
        System.out.println("Index Nested Loop Join:");
        stmt.execute("SET enable_nestloop TO true");
        stmt.execute("SET enable_mergejoin TO false");
        stmt.execute("SET enable_hashjoin TO false");
        // non-clustering index on publ.pubID
        stmt.execute("CREATE INDEX idx_publ_pubID on publ(pubID)");
        tuning.commit();
        runTests(tuning, "NL_IDX(publ.pubID)", explain);
        // non-clustering index on auth.pubID
        stmt.execute("DROP INDEX idx_publ_pubID");
        stmt.execute("CREATE INDEX idx_auth_pubID on auth(pubID)");
        tuning.commit();
        runTests(tuning, "NL_IDX(auth.pubID)", explain);
        // index on both
        stmt.execute("CREATE INDEX idx_publ_pubID on publ(pubID)");
        tuning.commit();
        runTests(tuning, "NL_IDX(publ.pubID & auth.pubID)", explain);

        // Sort Merge Join
        tuning.init();
        System.out.println("Sort Merge Join:");
        stmt.execute("SET enable_nestloop TO false");
        stmt.execute("SET enable_mergejoin TO true");
        stmt.execute("SET enable_hashjoin TO false");
        // no index
        runTests(tuning, "MJ_NO_INDEX", explain);
        // non-clustering indexes on publ.pubID and auth.pubID
        stmt.execute("CREATE INDEX idx_publ_pubID on publ(pubID)");
        stmt.execute("CREATE INDEX idx_auth_pubID on auth(pubID)");
        tuning.commit();
        runTests(tuning, "MJ_NON_CLUSTERING(publ.pubID & auth.pubID)", explain);
        // clustering on publ.pubID and auth.pubID
        stmt.execute("CLUSTER publ USING idx_publ_pubID");
        stmt.execute("CLUSTER auth USING idx_auth_pubID");
        tuning.commit();
        runTests(tuning, "MJ_CLUSTERING(publ.pubID & auth.pubID)", explain);

        // Hash Join
        tuning.init();
        System.out.println("Hash Join:");
        stmt.execute("SET enable_nestloop TO false");
        stmt.execute("SET enable_mergejoin TO false");
        stmt.execute("SET enable_hashjoin TO true");
        // no index
        runTests(tuning, "HASH_NO_INDEX", explain);
    }
}
