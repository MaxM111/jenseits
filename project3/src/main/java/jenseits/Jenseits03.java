package jenseits;

import java.io.FileInputStream;
import java.sql.Connection;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;

import jenseits.setup.*;

import jenseits.util.Logger;

public class Jenseits03 implements AutoCloseable {
    private Logger logger;

    public static void main(String[] args) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        var handler = new DBLPHandler();
        parser.parse("toy_example.txt", handler);
        var tree = handler.getTree();
        IO.println(tree.toString());
        try (var obj = new Jenseits03()) {
            tree.toEdgeModel(obj.getConn());
        }

    }

    Connection conn;

    public Jenseits03() throws Exception {
        conn = DB.getConnection(Database.POSTGRESQL);
        conn.setAutoCommit(true);
        logger = new Logger("logs", "log.csv");
    }

    public Connection getConn() {
        return conn;
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }

}
