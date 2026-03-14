package jenseits;

import java.sql.Connection;
import java.sql.Statement;

import jenseits.setup.DB;
import jenseits.setup.Database;

public class Jenseits01 {
    public static void main(String[] args) throws Exception {
        var conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);
        createTable(conn);
        fillValues(conn);
    }

    static void createTable(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS H_toy");
        // NOTE: no length for string specified. Example uses single characters, so 10
        // should suffice
        stmt.execute("CREATE TABLE H_toy (a1 VARCHAR(10), a2 VARCHAR(10), a3 INTEGER)");
    }

    static void fillValues(Connection conn) throws Exception {
        var prepSql = "INSERT INTO H_toy (a1, a2, a3) VALUES (?, ?, ?)";
        var prepStmt = conn.prepareStatement(prepSql);

        var a1Column = new String[] { "a", null, null, null };
        var a2Column = new String[] { "b", "c", null, null };
        var a3Column = new Integer[] { null, 2, 3, null };
        for (int i = 0; i < 4; i++) {
            prepStmt.setString(1, a1Column[i]);
            prepStmt.setString(2, a2Column[i]);
            if (a3Column[i] == null) {
                prepStmt.setNull(3, java.sql.Types.INTEGER);
            } else {
                prepStmt.setInt(3, a3Column[i]);
            }
            prepStmt.execute();
        }
    }
}
