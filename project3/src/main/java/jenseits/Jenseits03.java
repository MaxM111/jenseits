package jenseits;

import java.sql.Connection;

import jenseits.setup.*;

import jenseits.util.Logger;

public class Jenseits03 implements AutoCloseable {
    private Logger logger;

    public static void main(String[] args) throws Exception {
        try (var obj = new Jenseits03()) {
            // TODO:
        }
    }

    Connection conn;

    public Jenseits03() throws Exception {
        conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);
        logger = new Logger("logs", "log.csv");
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }

}
