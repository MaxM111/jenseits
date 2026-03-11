package tuning.setup;

public class Utils {
    public static double timeIt(String label, ThrowingRunnable action, Boolean showTimes) throws Exception {
        long start = System.nanoTime();
        action.run();
        long end = System.nanoTime();
        double result = ((end - start) / 1_000_000.0);
        if (showTimes) System.out.println(String.format("%s: %.2f ms", label, result));
        return result;
    }
}
