package jenseits;

import java.sql.Connection;
import java.util.Random;

import jenseits.setup.*;
import jenseits.util.*;

public class Jenseits02 implements AutoCloseable {

    private static Logger logger;

    public static void main(String[] args) throws Exception {
        logger = new Logger("logs", "log.csv");

        try (var obj = new Jenseits02()) {
            // TODO:
        }

        var pair = generate(10, 0.5);

        printMatrix(pair.getFirst());
        printMatrix(pair.getFirst());

        logger.close();
    }

    // NOTE: lets do it more OOP this time, i.e. use fields for state tracking
    // rather than passing vars
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
     * Generate two matrices A, B with dimensions m x l and l x n respectively,
     * where m + 1 = l = n + 1
     */
    public static Pair<double[][], double[][]> generate(int l, double sparsity) {
        int m = l - 1; // since m + 1 = l
        int n = l - 1; // since n + 1 = l

        double[][] A = new double[m][l]; // m x l
        double[][] B = new double[l][n]; // l x n

        // NOTE: wie Werte bestimmen?? Erstmal gleichverteilt über [1, 11];
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

}
