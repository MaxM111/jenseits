package jenseits;

import java.sql.Connection;
import java.sql.Statement;

import jenseits.setup.DB;
import jenseits.setup.Database;

public class Jenseits01 {
    public static void main(String[] args) throws Exception {
        var conn = DB.getConnection(Database.POSTGRESQL);
        createTable(conn);
    }

    static void createTable(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS H_toy");
        // NOTE: no length for string specified. Example uses single characters, so 10
        // should suffice
        stmt.execute("CREATE TABLE H_toy (a1 VARCHAR(10), a2 VARCHAR(10), a3 INTEGER)");
    }
}
