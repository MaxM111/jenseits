package jenseits;

import java.sql.Connection;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import jenseits.setup.*;

import jenseits.util.Logger;

public class Jenseits03 implements AutoCloseable {
    private Logger logger;

    public static void main(String[] args) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        DBLPHandler handler = new DBLPHandler();
        parser.parse("toy_example.txt", handler);
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
