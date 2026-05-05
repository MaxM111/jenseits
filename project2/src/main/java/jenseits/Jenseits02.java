package jenseits;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Random;
import java.sql.Statement;
import java.sql.ResultSet;

import jenseits.setup.*;
import jenseits.util.*;
import jenseits.setup.Pair;

public class Jenseits02 implements AutoCloseable {

    private static Logger logger;

    public static void main(String[] args) throws Exception {
        System.out.println("Toy Example:");
        show_toy_example();
        System.out.println("DB Example:");
        try (var obj = new Jenseits02()) {
            obj.createMatrixTable("A");
            obj.createMatrixTable("B");
            int length = 4;
            var pair = generate(length, 0.5);
            System.out.println("Matrix A: ");
            printMatrix(pair.getFirst());
            System.out.println("Matrix B: ");
            printMatrix(pair.getSecond());
            obj.fillMatrixTable("A", pair.getFirst());
            obj.fillMatrixTable("B", pair.getSecond());
            obj.createDBMSMultFunction("A", "B");
            System.out.println("Result C: ");
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

    /*
     * Creates two tables to store two matrices as described in
     * "Effiziente Matrixmultiplikationen"
     * NOTE: could use numeric datatype. Numeric datatype precision can be adjusted
     * up to 16383 digits after decimal point. Requires java BigDecimal.
     * https://www.postgresql.org/docs/current/datatype-numeric.html
     */
    private void createMatrixTable(String name) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute(String.format("DROP TABLE IF EXISTS %s", name));
        stmt.execute(String.format("CREATE TABLE %s (i INTEGER, j INTEGER, val DOUBLE PRECISION)", name));
    }

    /*
     * Stores a matrix in the designated DB table
     */
    private void fillMatrixTable(String tableName, double[][] m) throws Exception {
        PreparedStatement pstmt1 = conn.prepareStatement(String.format("INSERT INTO %s VALUES (?,?,?)", tableName));
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[i].length; j++) {
                if (m[i][j] == 0) {
                    continue;
                }
                pstmt1.setInt(1, i);
                pstmt1.setInt(2, j);
                pstmt1.setDouble(3, m[i][j]);
                pstmt1.execute();
            }
        }
    }

    // TODO: Find best option to dertmine matrix shape. matrix shape is hardcoded
    // for now.
    private double[][] calculateMatrixMultiplication(int length)
            throws Exception {
        Statement stmt = conn.createStatement();
        double[][] resultMatrix = new double[length - 1][length - 1];
        ResultSet rs = stmt.executeQuery("SELECT * FROM mult()");
        while (rs.next()) {
            resultMatrix[rs.getInt(1)][rs.getInt(2)] = rs.getDouble(3);
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

    public static void show_toy_example() {
        var pair = generate(4, 0.5);
        var A = pair.getFirst();
        var B = pair.getSecond();

        printMatrix(A);
        printMatrix(B);

        var C = matrixMultiply(A, B);
        printMatrix(C);
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
    //
    // I have checked this against a matrix calculator and it seems almost correct.
    // I think the minor deviation comes from floating point.
    // NOTE: add guard for dimension mismatch between rows of A and columns of B?
    public static double[][] matrixMultiply(double[][] A, double[][] B) {
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

    public void importMatrices(double[][] A, double[][] B) throws Exception {
        var statement = conn.createStatement();
        statement.execute("DROP TABLE IF EXISTS A, B");

        var createStmt = "CREATE TABLE %s (i INTEGER, j INTEGER, val DOUBLE PRECISION)";
        var insertStmt = "INSERT INTO %s VALUES (?, ?, ?)";

        var matrices = new Matrix[] {
                new Matrix("A", A),
                new Matrix("B", B)
        };

        for (Matrix matrix : matrices) {
            String table = matrix.name;
            statement.execute(String.format(createStmt, table));

            var preparedStatementA = conn.prepareStatement(String.format(insertStmt, table));
            for (int i = 0; i < matrix.data.length; i++) {
                for (int j = 0; j < matrix.data[0].length; j++) {
                    double val = matrix.data[i][j];

                    if (val != 0) {
                        preparedStatementA.setInt(1, i);
                        preparedStatementA.setInt(2, j);
                        preparedStatementA.setDouble(3, val);
                        preparedStatementA.execute();
                    }
                }
            }
        }
    }
}
