package de.neitzel.dnscheck.core.util;

import java.util.regex.Pattern;

public final class GlobMatcher {

    private GlobMatcher() {
    }

    public static boolean matches(String pattern, String value) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return Pattern.compile(toRegex(pattern), Pattern.DOTALL).matcher(value).matches();
    }

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
