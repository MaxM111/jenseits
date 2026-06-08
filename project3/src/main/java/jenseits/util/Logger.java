package jenseits.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Logger implements AutoCloseable {
    private final BufferedWriter writer;

    public Logger(String directory, String fileName) throws IOException {
        Path dir = Path.of(directory);
        Files.createDirectories(dir);
        Path path = dir.resolve(fileName);
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

    public void logPartial(String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            writer.write(values[i] == null ? "" : values[i]);
            writer.write(",");
        }
    }

    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
