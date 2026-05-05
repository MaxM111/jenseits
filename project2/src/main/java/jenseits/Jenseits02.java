package jenseits;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Random;
import java.sql.Statement;
import java.sql.ResultSet;

import jenseits.setup.*;
import jenseits.setup.Pair;

public class Jenseits02 implements AutoCloseable {

    public static void main(String[] args) throws Exception {

        try (var obj = new Jenseits02()) {
            IO.println("Toy Example:");
            obj.show_toy_example();
            obj.createDBMSMultFunction("toy_A", "toy_B");
            IO.println("Result C: ");
            printMatrix(obj.calculateMatrixMultiplication(4));

            IO.println("DB Example:");
            int length = 4;
            var pair = generate(length, 0.5);
            IO.println("Matrix A: ");
            printMatrix(pair.getFirst());
            IO.println("Matrix B: ");
            printMatrix(pair.getSecond());

            obj.importMatrix("A", pair.getFirst());
            obj.importMatrix("B", pair.getSecond());

            obj.createDBMSMultFunction("A", "B");
            IO.println("Result C: ");
            printMatrix(obj.calculateMatrixMultiplication(length));
        }
    }

    Connection conn;

    public Jenseits02() throws Exception {
        conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }

    private double[][] calculateMatrixMultiplication(int length)
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
        double[][] C = matrixMultiply(A, B);
        assert Arrays.equals(manualC, C);
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

    // Reference: https://en.wikipedia.org/wiki/Matrix_multiplication
    public static double[][] matrixMultiply(double[][] A, double[][] B) {
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
