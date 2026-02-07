package example;

/**
 * Pure business-logic class with no boilerplate methods.
 * None of these methods should be matched by JaCoCo method filter rules,
 * so this class should NOT appear in verify output.
 *
 * Deliberately avoids: getters (get*), setters (set*), equals, hashCode, toString.
 */
public class StringFormatter {

    private final String prefix;
    private final String suffix;

    public StringFormatter(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public String format(String value) {
        return prefix + value + suffix;
    }

    public String padLeft(String value, int width, char padChar) {
        if (value.length() >= width) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width - value.length(); i++) {
            sb.append(padChar);
        }
        sb.append(value);
        return sb.toString();
    }

    public String padRight(String value, int width, char padChar) {
        if (value.length() >= width) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value);
        for (int i = 0; i < width - value.length(); i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    public String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public String repeat(String value, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(value);
        }
        return sb.toString();
    }
}
