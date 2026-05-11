package jenseits;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Random;
import java.sql.Statement;
import java.sql.ResultSet;

import jenseits.setup.*;

import static jenseits.setup.Utils.countExecutions;
import jenseits.setup.Pair;
import jenseits.util.Logger;

public class Jenseits02 implements AutoCloseable {
    private Logger logger;

    public static void main(String[] args) throws Exception {
        try (var obj = new Jenseits02()) {
            obj.benchmark();
        }
    }

    Connection conn;

    public Jenseits02() throws Exception {
        conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);
        logger = new Logger("logs", "log.csv");
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }

    private void benchmark() throws Exception {
        int[] lengthVals = new int[] { Math.powExact(2, 3), Math.powExact(2, 5), Math.powExact(2, 7),
                Math.powExact(2, 8) };
        double[] sparsityVals = new double[] { 0.2, 0.4, 0.6, 0.8 };

        int timeUnitInSeconds = 3 * 60;

        logger.log("Approach", "matrixLength", "sparsity", "queryCount");
        IO.println("Beginning Benchmark.");

        for (var length : lengthVals) {
            for (var sparsity : sparsityVals) {
                IO.println(String.format("Benchmarking: MatrixLength %d | Sparsity %.2f", length, sparsity));
                var pair = generate(length, sparsity);
                var A = pair.getFirst();
                var B = pair.getSecond();

                importMatrix("A", A);
                importMatrix("B", B);
                importMatrixVector("A_vec", A, true);
                importMatrixVector("B_vec", B, false);

                createDBMSMultFunction("A", "B");
                createDBMSDotProductFunction();

                long count0 = countExecutions(() -> calculateMatrixMultApproach0(A, B), timeUnitInSeconds);
                logger.log("approach0", String.valueOf(length), String.valueOf(sparsity), String.valueOf(count0));

                long count1 = countExecutions(() -> calculateMatrixMultApproach1(length), timeUnitInSeconds);
                logger.log("approach1", String.valueOf(length), String.valueOf(sparsity), String.valueOf(count1));

                long count2 = countExecutions(() -> calculateMatrixMultApproach2("A_vec", "B_vec", length),
                        timeUnitInSeconds);
                logger.log("approach2", String.valueOf(length), String.valueOf(sparsity), String.valueOf(count2));
            }
        }
        logger.flush();
        IO.println("Finished Benchmark.");
    }

    private double[][] calculateMatrixMultApproach1(int length)
            throws Exception {
        Statement stmt = conn.createStatement();
        double[][] resultMatrix = new double[length - 1][length - 1];
        ResultSet rs = stmt.executeQuery("SELECT * FROM mult()");
        while (rs.next()) {
            int i = rs.getInt(1);
            int j = rs.getInt(2);
            double val = rs.getDouble(3);
            resultMatrix[i][j] = val;
        }
        return resultMatrix;
    }

    /*
     * Approach1: Creates a DBMS function for the multiplication of two matrix
     * tables
     */
    private void createDBMSMultFunction(String matrixName1, String matrixName2) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP FUNCTION IF EXISTS mult ()");
        stmt.execute(String.format(
                """
                                    CREATE FUNCTION mult()
                                    RETURNS TABLE(i INTEGER, j INTEGER, val DOUBLE PRECISION)
                                    LANGUAGE SQL STABLE
                                    AS $$
                                    SELECT A.i, B.j, SUM(A.val * B.val)
                                    FROM %s AS A, %s AS B
                                    WHERE A.j = B.i
                                    GROUP BY A.i, B.j
                                    $$
                        """, matrixName1, matrixName2));
    }

    public void show_toy_example() throws Exception {
        // l := 4
        // l - 1 x l
        double[][] A = {
                { 1, 0, 0, 0 },
                { 0, 3, 2, 4 },
                { 5, 3, 1, 0 },
        };

        // l x l - 1
        double[][] B = {
                { 0, 1, 0 },
                { 3, 4, 4 },
                { 5, 5, 0 },
                { 2, 0, 3 }
        };

        importMatrix("toy_A", A);
        importMatrix("toy_B", B);

        double[][] manualC = {
                { 0, 1, 0 },
                { 27, 22, 24 },
                { 14, 22, 12 },
        };
        double[][] C = calculateMatrixMultApproach0(A, B);
        assert Arrays.equals(manualC, C);
    }

    public static void show_phase1() throws Exception {
        try (var obj = new Jenseits02()) {
            IO.println("Toy Example:");
            obj.show_toy_example();
            obj.createDBMSMultFunction("toy_A", "toy_B");
            IO.println("Result C: ");
            printMatrix(obj.calculateMatrixMultApproach1(4));

            IO.println("DB Example:");
            int length = 4;
            var pair = generate(length, 0.5);
            var A = pair.getFirst();
            var B = pair.getSecond();
            IO.println("Matrix A: ");
            printMatrix(A);
            IO.println("Matrix B: ");
            printMatrix(B);

            obj.importMatrix("A", A);
            obj.importMatrix("B", B);
            obj.importMatrixVector("A_vec", A, true);
            obj.importMatrixVector("B_vec", B, false);

            obj.createDBMSMultFunction("A", "B");
            obj.createDBMSDotProductFunction();

            IO.println("Approach 0 Result: ");
            printMatrix(calculateMatrixMultApproach0(A, B));
            IO.println("Approach 1 Result: ");
            printMatrix(obj.calculateMatrixMultApproach1(length));
            IO.println("Approach 2 Result: ");
            printMatrix(obj.calculateMatrixMultApproach2("A_vec", "B_vec", length));
        }
    }

    /*
     * Approach 2: Matrix Multiplication
     */
    private double[][] calculateMatrixMultApproach2(String name1, String name2, int length) throws Exception {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(String.format("""
                SELECT A.i, B.j, dot_product(A.row,B.col) AS val
                FROM %s AS A, %s AS B
                """, name1, name2));
        double[][] resultMatrix = new double[length - 1][length - 1];
        while (rs.next()) {
            int i = rs.getInt(1);
            int j = rs.getInt(2);
            double val = rs.getDouble(3);
            resultMatrix[i][j] = val;
        }
        return resultMatrix;
    }

    /*
     * Create the dot_product function for approach 2
     * The function takes a row array and a column array as input
     */
    private void createDBMSDotProductFunction() throws Exception {
        Statement stmt = conn.createStatement();
        // define dotproduct function
        stmt.execute("DROP FUNCTION IF EXISTS dot_product (DOUBLE PRECISION[], DOUBLE PRECISION[])");
        stmt.execute("""
                CREATE FUNCTION dot_product (v1 DOUBLE PRECISION[], v2 DOUBLE PRECISION[])
                RETURNS DOUBLE PRECISION
                LANGUAGE SQL
                AS $$
                SELECT SUM(a*b)
                FROM unnest(v1, v2) as t(a,b)
                $$
                """);
    }

    /*
     * Creates matrix tables as either (i,row) or (j,column) as schema
     * and
     * imports values for approach 2
     */
    private void importMatrixVector(String name, double[][] matrix, boolean insertAsRows) throws Exception {
        var statement = conn.createStatement();
        statement.execute("DROP TABLE IF EXISTS " + name);
        if (!insertAsRows) {
            statement.execute(String.format("CREATE TABLE %s (j INTEGER, col DOUBLE PRECISION[])", name));
        } else {
            statement.execute(String.format("CREATE TABLE %s (i INTEGER, row DOUBLE PRECISION[])", name));
        }

        var insertStmt = conn.prepareStatement(String.format("INSERT INTO %s VALUES (?, ?)", name));
        if (!insertAsRows) {
            double[][] matrixCols = new double[matrix[0].length][matrix.length];
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix[0].length; j++) {
                    matrixCols[j][i] = matrix[i][j];
                }
            }
            for (int j = 0; j < matrixCols.length; j++) {
                var col = Arrays.stream(matrixCols[j]).boxed().toArray(Double[]::new);
                var sqlArray = conn.createArrayOf("FLOAT8", col);
                insertStmt.setInt(1, j);
                insertStmt.setArray(2, sqlArray);
                insertStmt.execute();
            }
        } else {
            for (int i = 0; i < matrix.length; i++) {
                var row = Arrays.stream(matrix[i]).boxed().toArray(Double[]::new);
                var sqlArray = conn.createArrayOf("FLOAT8", row);
                insertStmt.setInt(1, i);
                insertStmt.setArray(2, sqlArray);
                insertStmt.execute();
            }
        }

    }

    /*
     * Generate two matrices A, B with dimensions m x l and l x n respectively,
     * where m + 1 = l = n + 1
     */
    public static Pair<double[][], double[][]> generate(int l, double sparsity) {
        int m = l - 1; // since m + 1 = l
        int n = l - 1; // since n + 1 = l

        double[][] A = new double[m][l]; // m x l
        double[][] B = new double[l][n]; // l x n

        // NOTE: wie Werte bestimmen?? Erstmal gleichverteilt über [1, 100];
        Random rand = new Random();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < l; j++) {
                A[i][j] = rand.nextDouble() <= sparsity ? 0 : rand.nextDouble(1, 100);
            }
        }

        for (int i = 0; i < l; i++) {
            for (int j = 0; j < n; j++) {
                B[i][j] = rand.nextDouble() <= sparsity ? 0 : rand.nextDouble(1, 100);
            }
        }

        return new Pair<double[][], double[][]>(A, B);
    }

    public static void printMatrix(double[][] matrix) {
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[0].length; col++) {
                IO.print(String.format("%.2f ", matrix[row][col]));
            }
            IO.println();
        }
        IO.println();
    }

    /*
     * Approach0: Reference: https://en.wikipedia.org/wiki/Matrix_multiplication
     */
    public static double[][] calculateMatrixMultApproach0(double[][] A, double[][] B) {
        assert A.length > 0 && B.length > 0;
        assert A[0].length == B.length;

        var result = new double[A.length][B[0].length];
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < B[0].length; j++) {
                result[i][j] = computeMatrixMultiplicationCell(A, B, i, j);
            }
        }
        return result;
    }

    private static double computeMatrixMultiplicationCell(double[][] A, double[][] B, int i, int j) {
        double sum = 0;
        for (int k = 0; k < B.length; k++) {
            sum += A[i][k] * B[k][j];
        }
        return sum;
    }

    public record Matrix(String name, double[][] data) {
    }

    public void importMatrix(String name, double[][] matrix) throws Exception {
        var statement = conn.createStatement();
        statement.execute("DROP TABLE IF EXISTS " + name);

        statement.execute(String.format("CREATE TABLE %s (i INTEGER, j INTEGER, val DOUBLE PRECISION)", name));

        var insertStmt = conn.prepareStatement(String.format("INSERT INTO %s VALUES (?, ?, ?)", name));
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                double val = matrix[i][j];

                if (val != 0) {
                    insertStmt.setInt(1, i);
                    insertStmt.setInt(2, j);
                    insertStmt.setDouble(3, val);
                    insertStmt.execute();
                }
            }
        }
    }
}
