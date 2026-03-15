package jenseits.util;

public class GenericVarchar {
    private String current;
    private int count;

    public GenericVarchar() {
        current = String.valueOf('a');
        count = 0;
    }

    public String next() {
        if (count < 5) {
            count++;
            return current;
        }

        int lastIdx = current.length() - 1;
        char lastChar = current.charAt(lastIdx);
        if (lastChar < 'z') {
            current = current.substring(0, lastIdx) + (char) (lastChar + 1);
        } else {
            current = current + 'a';
        }

        count = 1;
        return current;
    }
}
