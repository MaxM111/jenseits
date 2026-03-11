package jenseits;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import jenseits.setup.DB;
import jenseits.setup.Database;

import java.io.*;
import java.sql.*;

import static jenseits.setup.Config.*;
import static jenseits.setup.Database.*;
import static jenseits.setup.Utils.timeIt;

public class Tuning01 {
    private Connection conn;
    private final int BATCH_SIZE = 10_000;

    public Tuning01(Database database) throws SQLException, ClassNotFoundException {
        conn = DB.getConnection(database);
    }

    public void newTable() throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS auth");
        stmt.execute("CREATE TABLE auth (name VARCHAR(49), pubID VARCHAR(129))");
    }

    public void straight() throws SQLException, IOException {
        Statement stmt = conn.createStatement();
        var reader = new BufferedReader(new FileReader(PATH));
        String line;

        while ((line = reader.readLine()) != null) {
            String[] columns = line.split("\t");

            String sql = String.format("INSERT INTO auth (name, pubID) VALUES ('%s', '%s')",
                    columns[0].replace("'", "''"),
                    columns[1].replace("'", "''"));

            stmt.executeUpdate(sql);
        }

        conn.commit();
        reader.close();
    }

    public void straightPrep() throws SQLException, IOException {
        String prepInsert = "INSERT INTO auth (name, pubID) VALUES (?, ?)";
        PreparedStatement pStmt = conn.prepareStatement(prepInsert);
        var reader = new BufferedReader(new FileReader(PATH));
        String line;

        while ((line = reader.readLine()) != null) {
            String[] columns = line.split("\t");
            pStmt.setString(1, columns[0]);
            pStmt.setString(2, columns[1]);
            pStmt.execute();
        }

        conn.commit();
        reader.close();
    }

    public void batch() throws SQLException, IOException {
        Statement stmt = conn.createStatement();
        var reader = new BufferedReader(new FileReader(PATH));
        String line;
        int c = 0;

        while ((line = reader.readLine()) != null) {
            String[] columns = line.split("\t");

            stmt.addBatch(String.format("INSERT INTO auth (name, pubID) VALUES ('%s', '%s')",
                    columns[0].replace("'", "''"),
                    columns[1].replace("'", "''")));

            c++;
            if (c % BATCH_SIZE == 0)
                stmt.executeBatch();
        }

        stmt.executeBatch();
        conn.commit();
        reader.close();
    }

    public void batchPrep() throws SQLException, IOException {
        String prepInsert = "INSERT INTO auth (name, pubID) VALUES (?, ?)";

        PreparedStatement pStmt = conn.prepareStatement(prepInsert);
        var reader = new BufferedReader(new FileReader(PATH));
        String line;
        int c = 0;

        while ((line = reader.readLine()) != null) {
            String[] columns = line.split("\t");

            pStmt.setString(1, columns[0]);
            pStmt.setString(2, columns[1]);
            pStmt.addBatch();

            c++;
            if (c % BATCH_SIZE == 0)
                pStmt.executeBatch();
        }

        pStmt.executeBatch();
        conn.commit();
        reader.close();
    }

    public void copy() throws SQLException, IOException {
        var reader = new BufferedReader(new FileReader(PATH));
        var copyManager = new CopyManager(conn.unwrap(BaseConnection.class));
        String copySQL = "COPY auth(name, pubID) FROM STDIN WITH (FORMAT csv, DELIMITER E'\t')";
        copyManager.copyIn(copySQL, reader);

        conn.commit();
    }

    private static double[] run_tests(Database database, String fileName) throws Exception {
        Tuning01 t = new Tuning01(database);
        var logger = new BufferedWriter(new FileWriter(fileName, true));
        double[] results = new double[5];

        t.newTable();
        double time = timeIt("straightforward basic", t::straight, false);
        logger.write(String.format("straightforward basic: %fs", time));
        logger.newLine();
        results[0] = time;

        t.newTable();
        time = timeIt("straightforward prepared", t::straightPrep, false);
        logger.write(String.format("straightforward prepared: %fs", time));
        logger.newLine();
        results[1] = time;

        t.newTable();
        time = timeIt("batch basic", t::batch, false);
        logger.write(String.format("batch basic: %fs", time));
        logger.newLine();
        results[2] = time;

        t.newTable();
        time = timeIt("batch prepared", t::batchPrep, false);
        logger.write(String.format("prepared batch: %fs", time));
        logger.newLine();
        results[3] = time;

        if (database == POSTGRESQL) {
            t.newTable();
            time = timeIt("copy", t::copy, false);
            logger.write(String.format("copy: %fs", time));
            logger.newLine();
            results[4] = time;
        }

        logger.close();
        return results;
    }

    public static void main(String[] args) throws Exception {
        int iterations = 1;
        double straight = 0;
        double prepared = 0;
        double batch = 0;
        double prepBatch = 0;
        double copy = 0;

        for (int i = 0; i < iterations; i++) {
            double[] results = run_tests(POSTGRESQL, "postgres.txt");
            straight += results[0];
            prepared += results[1];
            batch += results[2];
            prepBatch += results[3];
            copy += results[4];
        }

        double straightAverage = straight / iterations;
        double preparedAverage = prepared / iterations;
        double batchAverage = batch / iterations;
        double prepBatchAverage = prepBatch / iterations;
        double copyAverage = copy / iterations;

        System.out.println(String.format("%f | %f | %f | %f | %f |", straightAverage, preparedAverage, batchAverage,
                prepBatchAverage, copyAverage));

        straight = 0;
        prepared = 0;
        batch = 0;
        prepBatch = 0;

        for (int i = 0; i < iterations; i++) {
            double[] results = run_tests(MARIADB, "mariadb.txt");
            straight += results[0];
            prepared += results[1];
            batch += results[2];
            prepBatch += results[3];
        }

        straightAverage = straight / iterations;
        preparedAverage = prepared / iterations;
        batchAverage = batch / iterations;
        prepBatchAverage = prepBatch / iterations;
        copyAverage = copy / iterations;

        System.out.println(String.format("%f | %f | %f | %f |", straightAverage, preparedAverage, batchAverage,
                prepBatchAverage));
    }
}
