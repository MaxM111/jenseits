package jenseits.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Logger implements AutoCloseable {
    private final BufferedWriter writer;

    public Logger(String fileName) throws IOException {
        Path path = Path.of(fileName);
        writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void log(String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            writer.write(values[i] == null ? "" : values[i]);
            if (i < values.length - 1) {
                writer.write(",");
            }
        }
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
