package jenseits;

import java.sql.Connection;

import jenseits.setup.*;
import jenseits.util.*;

public class Jenseits02 implements AutoCloseable {

    private static Logger logger;

    public static void main(String[] args) throws Exception {
        logger = new Logger("logs", "log.csv");

        try (var obj = new Jenseits02()) {
            // TODO:
        }

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

    // NOTE: this should be static though, because it does not depend on anything
    public static double[][] generate(int size, double sparsity) {
        return null;
    }
}
