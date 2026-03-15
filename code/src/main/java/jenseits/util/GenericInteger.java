package jenseits.util;

public class GenericInteger {
    private int current;
    private int count;

    public GenericInteger() {
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
