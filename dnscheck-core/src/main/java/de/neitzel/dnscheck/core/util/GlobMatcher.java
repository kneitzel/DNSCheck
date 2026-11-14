package de.neitzel.dnscheck.core.util;

import java.util.regex.Pattern;

/**
 * Matches rdata against the glob syntax used by an rdata pattern.
 * {@code *} matches any sequence and {@code ?} matches one character.
 */
public final class GlobMatcher {

    /**
     * Prevents instantiation.
     */
    private GlobMatcher() {
    }

    /**
     * Reports whether {@code value} matches an rdata pattern.
     * A missing or blank pattern matches every value. A pattern does not match a missing value.
     *
     * @param pattern the glob, or blank when every rdata value should match.
     * @param value the rdata to test, or {@code null} when the record has no rdata.
     * @return {@code true} when the pattern is blank or the value matches the glob.
     */
    public static boolean matches(String pattern, String value) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return Pattern.compile(toRegex(pattern), Pattern.DOTALL).matcher(value).matches();
    }

    /**
     * Converts a glob into an anchored regular expression.
     * {@code *} becomes {@code .*} and {@code ?} matches a single character. Other regex
     * metacharacters are escaped so they are matched literally.
     *
     * @param glob the rdata pattern to convert.
     * @return a regular expression that matches the entire value.
     */
    static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char current = glob.charAt(i);
            switch (current) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.' -> regex.append("\\.");
                case '\\' -> regex.append("\\\\");
                default -> {
                    if ("+()[]{}^$|".indexOf(current) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(current);
                }
            }
        }
        regex.append('$');
        return regex.toString();
    }
}
