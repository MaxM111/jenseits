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
        factory.setValidating(true);

        SAXParser parser = factory.newSAXParser();

        var reader = parser.getXMLReader();
        reader.setEntityResolver((publicId, systemId) -> new InputSource(new FileInputStream("dblp.dtd")));
        reader.setContentHandler(new DBLPHandler());

        reader.parse("toy_example.txt");
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
