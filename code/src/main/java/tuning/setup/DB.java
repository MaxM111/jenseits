package tuning.setup;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static tuning.setup.Config.*;

public class DB {

    public static Connection getConnection(Database database) throws SQLException, ClassNotFoundException {
        Class.forName("org.mariadb.jdbc.Driver");

        String db, port;
        if (database == Database.POSTGRESQL) {
            db = "postgresql";
            port = POSTGRES_PORT;
        } else {
            db = "mariadb";
            port = MARIADB_PORT;
        }

        String url = String.format("jdbc:%s://%s:%s/%s", db, HOST, port, DB_NAME);

        Connection conn = DriverManager.getConnection(url, USER, PWD);
        conn.setAutoCommit(false);
        return conn;
    }
}
