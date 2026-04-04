package examples.retrieval;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Small console helpers for the retrieval examples.
 */
public final class ExampleOutput {

    private static final String SECTION_DIVIDER = "=".repeat(72);
    private static final String SUBSECTION_DIVIDER = "-".repeat(72);

    private ExampleOutput() {
    }

    public static void line() {
        System.out.println();
    }

    public static void line(String format, Object... args) {
        if (args == null || args.length == 0) {
            System.out.println(format);
            return;
        }
        System.out.println(String.format(Locale.ROOT, format, args));
    }

    public static void section(String title) {
        line();
        line(SECTION_DIVIDER);
        line(title);
        line(SECTION_DIVIDER);
    }

    public static void subsection(String title) {
        line();
        line(SUBSECTION_DIVIDER);
        line(title);
        line(SUBSECTION_DIVIDER);
    }

    public static void keyValue(String label, Object value) {
        line("%-28s %s", label + ":", value);
    }

    public static void printScoredMap(Map<String, Double> scores) {
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            line("  %-24s %.4f", entry.getKey(), entry.getValue());
        }
    }

    public static void printCollection(String title, Collection<?> values) {
        line("%s:", title);
        for (Object value : values) {
            line("  %s", value);
        }
    }
}