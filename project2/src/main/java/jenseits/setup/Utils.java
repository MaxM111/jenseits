package jenseits.setup;

public class Utils {
    public static double timeIt(String label, ThrowingRunnable action, Boolean showTimes) throws Exception {
        long start = System.nanoTime();
        action.run();
        long end = System.nanoTime();
        double result = ((end - start) / 1_000_000.0);
        if (showTimes)
            System.out.println(String.format("%s: %.2f ms", label, result));
        return result;
    }

    public static long countExecutions(ThrowingRunnable multiplicationVariant, int timeUnitInSeconds) throws Exception {
        long timeUnitInNanos = timeUnitInSeconds * 1_000_000_000L;
        long startTime = System.nanoTime();
        long counter = 0;
        while (System.nanoTime() - startTime < timeUnitInNanos) {
            multiplicationVariant.run();
            counter++;
        }
        var timePassed = System.nanoTime() - startTime;
        return counter;
    }

}
