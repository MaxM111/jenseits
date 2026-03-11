package jenseits;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jenseits.setup.DB;
import jenseits.setup.Database;

public class Tuning06 {
    private Database db;
    private int maxConcurrent;
    private final int numEmployees;
    private final int initialCompanyBalance;

    enum SolutionType {
        A, B
    }

    enum IsolationMode {
        READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
        SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

        private final int level;

        IsolationMode(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    public Tuning06(Database database) throws SQLException, ClassNotFoundException {
        this.db = database;
        this.maxConcurrent = 5;
        this.numEmployees = 100;
        this.initialCompanyBalance = 100;
    }

    public void createTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // clear table
            stmt.execute("DROP TABLE IF EXISTS Accounts");

            stmt.execute("""
                    CREATE TABLE Accounts (
                        account integer PRIMARY KEY,
                        balance integer NOT NULL
                    )
                    """);
        }
    }

    /**
     * A company with 100 employees pays the salaries at the end of the month. The
     * account of the company (account number 0, initial balance 100) and the
     * accounts of
     * all employees (account numbers 1 to 100, initial balance 0) are with the same
     * bank.
     */
    public void fillTable(Connection conn) throws SQLException {
        String sql = "INSERT INTO Accounts (account, balance) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i <= this.numEmployees; i++) {
                ps.setInt(1, i);
                ps.setInt(2, i == 0 ? initialCompanyBalance : 0);
                ps.executeUpdate();
            }
        }
    }

    private void resetBalances(Connection conn) throws SQLException {
        String update = "UPDATE Accounts SET balance = ? WHERE account = 0";
        try (var prepStatement = conn.prepareStatement(update)) {
            prepStatement.setInt(1, initialCompanyBalance);
            prepStatement.executeUpdate();
        }

        update = "UPDATE Accounts SET balance = 0 WHERE account BETWEEN 1 AND ?";
        try (var prepStatement = conn.prepareStatement(update)) {
            prepStatement.setInt(1, this.numEmployees); // BETWEEN is on both sides inclusive
            prepStatement.executeUpdate();
        }
    }

    private int getBalance(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT balance FROM Accounts WHERE account = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        throw new SQLException("Account not found: " + accountId);
    }

    public void runAll() throws Exception {
        try (Connection adminConn = DB.getConnection(this.db)) {
            adminConn.setAutoCommit(true);
            createTable(adminConn);
            fillTable(adminConn);
        }

        for (SolutionType sol : SolutionType.values()) {
            for (IsolationMode iso : IsolationMode.values()) {
                for (int threads = 1; threads <= this.maxConcurrent; threads++) {
                    runExperiment(sol, iso, threads);
                }
            }
        }
    }

    private void runExperiment(SolutionType solution, IsolationMode iso, int numThreads) throws Exception {
        try (Connection adminConn = DB.getConnection(this.db)) {
            adminConn.setAutoCommit(true);

            System.out.printf("\n--- Solution=%s, Isolation=%s, Threads=%d ---\n",
                    solution, iso, numThreads);

            resetBalances(adminConn);
            int initBalance = getBalance(adminConn, 0);
            System.out.println("Initial balance of account 0: " + initBalance);

            Transaction[] transactions = new Transaction[this.numEmployees];
            for (int i = 0; i < this.numEmployees; i++) {
                transactions[i] = new Transaction(this.db, i + 1, solution, iso);
            }

            ExecutorService pool = Executors.newFixedThreadPool(numThreads);
            long start = System.nanoTime();
            for (var transaction : transactions) {
                pool.execute(transaction);
            }
            pool.shutdown();
            while (!pool.isTerminated()) {
            }
            long end = System.nanoTime();

            int finalBalance = getBalance(adminConn, 0);
            System.out.println("Final balance of account 0: " + finalBalance);

            double correctness = (initBalance - finalBalance) / 100.0;
            double secs = (end - start) / 1_000_000_000.0;
            double throughput = transactions.length / secs;

            System.out.printf("Solution: %s, Isolation: %s, Threads: %d%n",
                    solution, iso, numThreads);
            System.out.printf("Throughput: %.2f tx/sec, Correctness: %.2f%n",
                    throughput, correctness);
        }
    }

    static class Transaction extends Thread {
        private static final int BACKOFF_TIME = 50; // ms
        private final int id;
        private final SolutionType solution;
        private final IsolationMode iso;
        private final Database db;

        Transaction(Database db, int id, SolutionType solution, IsolationMode iso)
                throws SQLException, ClassNotFoundException {
            this.id = id;
            this.solution = solution;
            this.iso = iso;
            this.db = db;
        }

        @Override
        public void run() {
            try (Connection conn = DB.getConnection(db)) {
                conn.setAutoCommit(false);
                conn.setTransactionIsolation(iso.getLevel());
                int backoff = BACKOFF_TIME;

                while (true) {
                    try {
                        if (solution == SolutionType.A) {
                            int e = selectBalance(conn, id);
                            updateBalance(conn, id, e + 1);
                            int c = selectBalance(conn, 0);
                            updateBalance(conn, 0, c - 1);
                        } else {
                            simpleInc(conn, id);
                            simpleDec(conn, 0);
                        }
                        conn.commit();
                        return;
                    } catch (SQLException e) {
                        System.out.println("ABORT");
                        try {
                            conn.rollback();
                        } catch (SQLException ignored) {
                        }

                        // SQLSTATE (https://www.postgresql.org/docs/current/errcodes-appendix.html):
                        // Class 40: Transaction Rollback
                        // (includes serialization_failure and deadlock_detected)
                        String state = e.getSQLState();
                        if (state.startsWith("40")) {
                            try {
                                Thread.sleep(backoff);
                            } catch (InterruptedException ignored) {
                            }
                            backoff = Math.min(backoff * 2, 1000);
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        private int selectBalance(Connection conn, int accountId) throws SQLException {
            String sql = "SELECT balance FROM Accounts WHERE account = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        return rs.getInt(1);
                }
            }
            throw new SQLException("Account not found: " + accountId);
        }

        private void updateBalance(Connection conn, int accountId, int newBalance) throws SQLException {
            String sql = "UPDATE Accounts SET balance = ? WHERE account = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, newBalance);
                ps.setInt(2, accountId);
                ps.executeUpdate();
            }
        }

        private void simpleInc(Connection conn, int accountId) throws SQLException {
            String sql = "UPDATE Accounts SET balance = balance + 1 WHERE account = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, accountId);
                ps.executeUpdate();
            }
        }

        private void simpleDec(Connection conn, int accountId) throws SQLException {
            String sql = "UPDATE Accounts SET balance = balance - 1 WHERE account = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, accountId);
                ps.executeUpdate();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        var tuning = new Tuning06(Database.POSTGRESQL);
        tuning.runAll();
    }
}
