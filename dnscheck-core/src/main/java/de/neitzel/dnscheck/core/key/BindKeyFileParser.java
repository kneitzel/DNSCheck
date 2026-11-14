package de.neitzel.dnscheck.core.key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a TSIG key from a BIND key file.
 */
public final class BindKeyFileParser {

    /**
     * Matches a BIND {@code key "name" { ... }} block and captures the key name and body.
     */
    private static final Pattern KEY_BLOCK = Pattern.compile(
            "key\\s+\"([^\"]+)\"\\s*\\{([^}]*)\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Matches the {@code algorithm} statement inside a key block.
     */
    private static final Pattern ALGORITHM = Pattern.compile(
            "algorithm\\s+(\\S+)\\s*;",
            Pattern.CASE_INSENSITIVE);

    /**
     * Matches the {@code secret} statement inside a key block.
     */
    private static final Pattern SECRET = Pattern.compile(
            "secret\\s+\"([^\"]+)\"\\s*;",
            Pattern.CASE_INSENSITIVE);

    /**
     * Reads a key file and parses its first BIND key block.
     *
     * @param keyFile the key file to read.
     * @return the TSIG key named in that file.
     * @throws IOException when the file cannot be read.
     * @throws IllegalArgumentException when the content is not a BIND key block.
     */
    public TsigKey parse(Path keyFile) throws IOException {
        return parseContent(Files.readString(keyFile));
    }

    /**
     * Parses the first BIND key block in {@code content}.
     * The algorithm token is kept as written, including a trailing dot when BIND stored one.
     *
     * @param content the text of a key file.
     * @return the TSIG key from the first key block.
     * @throws IOException when the content cannot be read. Malformed content is reported as an illegal argument.
     * @throws IllegalArgumentException when the content is empty or missing the key block, algorithm, or secret.
     */
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
