package jenseits.util;

public class IntGenerator {
    private int current;
    private int count;

    public IntGenerator() {
        current = 1;
        count = 0;
    }

    public int next() {
        if (count < 5) {
            count++;
            return current;
        }

        current++;
        count = 1;
        return current;
    }
}
