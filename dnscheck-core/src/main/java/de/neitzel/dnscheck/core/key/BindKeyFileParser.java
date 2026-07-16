package de.neitzel.dnscheck.core.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BindKeyFileParser {

    private static final Pattern KEY_BLOCK = Pattern.compile(
            "key\\s+\"([^\"]+)\"\\s*\\{([^}]*)\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ALGORITHM = Pattern.compile(
            "algorithm\\s+(\\S+)\\s*;",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET = Pattern.compile(
            "secret\\s+\"([^\"]+)\"\\s*;",
            Pattern.CASE_INSENSITIVE);

    public TsigKey parse(Path keyFile) throws IOException {
        return parseContent(Files.readString(keyFile));
    }

    public TsigKey parseContent(String content) throws IOException {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Key file content is empty");
        }
        Matcher keyMatcher = KEY_BLOCK.matcher(content);
        if (!keyMatcher.find()) {
            throw new IllegalArgumentException("No BIND key block found in key content");
        }
        String keyName = keyMatcher.group(1);
        String body = keyMatcher.group(2);

        Matcher algorithmMatcher = ALGORITHM.matcher(body);
        if (!algorithmMatcher.find()) {
            throw new IllegalArgumentException("Missing algorithm in key content");
        }

        Matcher secretMatcher = SECRET.matcher(body);
        if (!secretMatcher.find()) {
            throw new IllegalArgumentException("Missing secret in key content");
        }

        return new TsigKey(keyName, algorithmMatcher.group(1), secretMatcher.group(1));
    }
}
