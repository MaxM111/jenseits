package jenseits.setup;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
