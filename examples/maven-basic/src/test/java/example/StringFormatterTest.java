package example;

import org.junit.jupiter.api.Test;

class StringFormatterTest {

    @Test
    void formatWrapsValueWithPrefixAndSuffix() {
        StringFormatter fmt = new StringFormatter("[", "]");
        String result = fmt.format("hello");

        if (!"[hello]".equals(result)) {
            throw new AssertionError("Expected [hello] but got " + result);
        }
    }

    @Test
    void padLeftPadsShorterStrings() {
        StringFormatter fmt = new StringFormatter("", "");
        String result = fmt.padLeft("42", 5, ' ');

        if (!"   42".equals(result)) {
            throw new AssertionError("Expected '   42' but got '" + result + "'");
        }
    }

    @Test
    void padRightPadsShorterStrings() {
        StringFormatter fmt = new StringFormatter("", "");
        String result = fmt.padRight("42", 5, ' ');

        if (!"42   ".equals(result)) {
            throw new AssertionError("Expected '42   ' but got '" + result + "'");
        }
    }

    @Test
    void truncateShortsLongStrings() {
        StringFormatter fmt = new StringFormatter("", "");
        String result = fmt.truncate("abcdefgh", 5);

        if (!"abcde".equals(result)) {
            throw new AssertionError("Expected 'abcde' but got '" + result + "'");
        }
    }

    @Test
    void repeatDuplicatesString() {
        StringFormatter fmt = new StringFormatter("", "");
        String result = fmt.repeat("ab", 3);

        if (!"ababab".equals(result)) {
            throw new AssertionError("Expected 'ababab' but got '" + result + "'");
        }
    }
}
