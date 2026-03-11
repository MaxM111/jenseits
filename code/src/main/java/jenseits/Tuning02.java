package jenseits;

import org.w3c.dom.ls.LSOutput;
import tuning.setup.DB;

import static tuning.setup.Utils.timeIt;
import tuning.setup.Database;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.List;
import java.util.Random;

public class Tuning02 {
    private Connection conn;

    public Tuning02(Database database) throws SQLException, ClassNotFoundException {
        conn = DB.getConnection(database);
    }

    // sources:
    // https://www.postgresql.org/docs/current/sql-createtable.html
    // https://mariadb.com/kb/en/getting-started-with-indexes/
    public void createTables() throws SQLException {
        Statement stmt = conn.createStatement();

        // clear tables
        stmt.execute("DROP TABLE IF EXISTS student");
        stmt.execute("DROP TABLE IF EXISTS employee");
        stmt.execute("DROP TABLE IF EXISTS techdept");

        // techdept table
        stmt.execute("""
                CREATE TABLE techdept (
                    dept VARCHAR(50) PRIMARY KEY,
                    manager VARCHAR(100),
                    location VARCHAR(100)
                )
                """);
        // both DBMS automatically creates unique btree index on primary key

        // employee table
        stmt.execute("""
                CREATE TABLE employee (
                    ssnum INTEGER PRIMARY KEY ,
                    name VARCHAR(100) UNIQUE NOT NULL,
                    manager VARCHAR(100),
                    dept VARCHAR(100),
                    salary REAL,
                    numfriends INTEGER
                )
                """);
        stmt.execute("CREATE INDEX deptidx ON employee(dept)");
        // both DBMS automatically creates unique btree index on primary keys

        // student table
        stmt.execute("""
                CREATE TABLE student (
                    ssnum INTEGER PRIMARY KEY ,
                    name VARCHAR(100) UNIQUE NOT NULL,
                    course VARCHAR(100),
                    grade INTEGER
                )
                """);
        // both DBMS automatically creates unique btree index on primary keys

        conn.commit();
    }

    public void fillTables() throws SQLException {
        var techdepts = List.of("Software", "Hardware", "AI", "ML", "Data",
                "Security", "Networking", "Cloud", "DevOps", "Web");
        Random rand = new Random();

        // techdept table
        var pstmt = conn.prepareStatement("INSERT INTO techdept VALUES (?, ?, ?)");
        for (var dept : techdepts) {
            pstmt.setString(1, dept);
            pstmt.setString(2, "manager" + rand.nextInt(10));
            pstmt.setString(3, "location" + rand.nextInt(10));
            pstmt.execute();
        }

        var depts = List.of("Customer Service", "HR", "Finance", "Sales", "Marketing");

        // employee table
        pstmt = conn.prepareStatement("INSERT INTO employee VALUES (?, ?, ?, ?, ?, ?)");
        for (int i = 0; i < 100_000; i++) {
            pstmt.setInt(1, i);
            pstmt.setString(2, "name" + i);
            pstmt.setString(3, "manager" + rand.nextInt(10));
            pstmt.setString(4, i % 10 == 0 ? techdepts.get(rand.nextInt(techdepts.size()))
                    : depts.get(rand.nextInt(depts.size())));
            pstmt.setDouble(5, Math.round(rand.nextDouble(10_000) * 100.0) / 100.0);
            pstmt.setInt(6, rand.nextInt(30));
            pstmt.execute();
        }

        var courses = List.of("LinAlg", "Tuning", "Krypto", "Compiler");

        // student table
        pstmt = conn.prepareStatement("INSERT INTO student VALUES (?, ?, ?, ?)");
        for (int i = 0; i < 100_000; i++) {
            pstmt.setInt(1, i * 2);
            pstmt.setString(2, "name" + i * 2);
            pstmt.setString(3, courses.get(rand.nextInt(courses.size())));
            pstmt.setInt(4, rand.nextInt(1, 6));
            pstmt.execute();
        }

        conn.commit();
    }

    // Slow query with DISTINCT although it is not needed
    public void query1_1() throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("""
                SELECT DISTINCT employee.ssnum
                FROM employee, student
                WHERE employee.name = student.name
                """);

        conn.commit();
    }

    // Improving query by removing unnecessary DISTINCT
    public void query1_2() throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("""
                SELECT employee.ssnum
                FROM employee, student
                WHERE employee.name = student.name
                """);

        conn.commit();
    }

    // Further improving query by joining on numeric values
    public void query1_3() throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("""
                SELECT employee.ssnum
                FROM employee, student
                WHERE employee.ssnum = student.ssnum
                """);

        conn.commit();
    }


    // Slow query with HAVING although it is not needed
    public void query2_1() throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("""
                SELECT avg(salary) as avgsalary, numfriends
                FROM employee
                GROUP BY numfriends
                HAVING numfriends > 25
                """);

        conn.commit();
    }

    // Improving query by using WHERE instead of HAVING
    public void query2_2() throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("""
                SELECT avg(salary) as avgsalary, numfriends
                FROM employee
                WHERE numfriends > 25
                GROUP BY numfriends
                """);

        conn.commit();
    }

    private static double[] runTests(Database database, String fileName) throws Exception {
        Tuning02 t = new Tuning02(database);
        t.createTables();
        t.fillTables();
        var logger = new BufferedWriter(new FileWriter(fileName, true));
        double[] results = new double[5];

        double time = timeIt("query1_1", t::query1_1, false);
        logger.write(String.format("query1_1: %fs", time));
        logger.newLine();
        results[0] = time;

        time = timeIt("query1_2", t::query1_2, false);
        logger.write(String.format("query1_2: %fs", time));
        logger.newLine();
        results[1] = time;

        time = timeIt("query1_3", t::query1_3, false);
        logger.write(String.format("query1_3: %fs", time));
        logger.newLine();
        results[2] = time;

        time = timeIt("query2_1", t::query2_1, false);
        logger.write(String.format("query2_1: %fs", time));
        logger.newLine();
        results[3] = time;

        time = timeIt("query2_2", t::query2_2, false);
        logger.write(String.format("query2_2: %fs", time));
        logger.newLine();
        results[4] = time;

        logger.close();
        return results;
    }

    public static void runTestsWithDatabase(Database database, int iterations) throws Exception {
        double query1_1 = 0;
        double query1_2 = 0;
        double query1_3 = 0;
        double query2_1 = 0;
        double query2_2 = 0;

        for (int i = 0; i < iterations; i++) {
            double[] results = runTests(database, database + "2.txt");
            query1_1 += results[0];
            query1_2 += results[1];
            query1_3 += results[2];
            query2_1 += results[3];
            query2_2 += results[4];
        }

        double avg_query1_1 = query1_1 / iterations;
        double avg_query1_2 = query1_2 / iterations;
        double avg_query1_3 = query1_3 / iterations;
        double avg_query2_1 = query2_1 / iterations;
        double avg_query2_2 = query2_2 / iterations;

        System.out.println(database + ":");
        System.out.println(String.format("query 1_1: %.2f: ms | 1_2: %.2f ms | 1_3: %.2f ms", avg_query1_1,
                avg_query1_2, avg_query1_3));

        System.out.println(String.format("query 2_1: %.2f ms | 2_2: %.2f ms", avg_query2_1, avg_query2_2));
    }

    public Boolean checkEquivalence(String query1, String query2) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet res = stmt.executeQuery("(" + query1 + " EXCEPT ALL " + query2 + ") UNION ALL (" + query2 + " EXCEPT ALL " + query1 + ")");
        return !res.next();
    }

    public static void main(String[] args) throws Exception {
        int iterations = 1;
        runTestsWithDatabase(Database.POSTGRESQL, iterations);
        runTestsWithDatabase(Database.MARIADB, iterations);

        // check equivalence
        Tuning02 t1 = new Tuning02(Database.POSTGRESQL);
        System.out.print("Checking equivalence of Query 1: ");
        System.out.println(t1.checkEquivalence("""
                SELECT DISTINCT employee.ssnum
                FROM employee, student
                WHERE employee.name = student.name
                """, """
                SELECT employee.ssnum
                FROM employee, student
                WHERE employee.ssnum = student.ssnum
                """));

        System.out.print("Checking equivalence of Query 2: ");
        System.out.println(t1.checkEquivalence("""
                SELECT avg(salary) as avgsalary, numfriends
                FROM employee
                GROUP BY numfriends
                HAVING numfriends > 25
                """, """
                SELECT avg(salary) as avgsalary, numfriends
                FROM employee
                WHERE numfriends > 25
                GROUP BY numfriends
                """));
    }
}
