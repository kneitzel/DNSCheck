package de.neitzel.dnscheck.service.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creates and checks time-based one-time passwords and recovery codes for multi-factor authentication.
 */
@ApplicationScoped
public final class MfaCodeService {

    /**
     * Source of random bytes for MFA secret material and recovery codes.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Issuer name placed in the otpauth URI during MFA enrollment.
     */
    private static final String ISSUER = "DNSCheck";

    /**
     * Number of digits in a time-based one-time password.
     */
    private static final int DIGITS = 6;

    /**
     * Length of one time step, in seconds, used to check a time-based one-time password.
     */
    private static final int TIME_STEP_SECONDS = 30;

    /**
     * Number of random bytes drawn when creating MFA secret material.
     */
    private static final int SECRET_BYTES = 20;

    /**
     * JCA name of the HMAC-SHA1 algorithm used to derive a time-based one-time password from MFA secret material.
     */
    private static final String ALGORITHM = "HmacSHA1";

    /**
     * Alphabet used to encode and decode MFA secret material as Base32.
     */
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /**
     * Creates new MFA secret material for a pending MFA enrollment.
     *
     * @return Base32-encoded secret material.
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /**
     * Builds the otpauth URI an authenticator app uses during MFA enrollment.
     *
     * @param username Operator username included in the URI label.
     * @param secret Base32-encoded MFA secret material for the pending enrollment.
     * @return The otpauth URI. It contains {@code secret}, so callers must treat the result as secret material.
     */
    public String buildOtpAuthUri(String username, String secret) {
        String label = urlEncode(ISSUER + ":" + username);
        String issuer = urlEncode(ISSUER);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer + "&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    /**
     * Checks a time-based one-time password against MFA secret material.
     * The previous, current, and next time steps are accepted.
     *
     * @param secret Base32-encoded MFA secret material.
     * @param code Code presented by the operator.
     * @return {@code true} when {@code code} matches one of those time steps.
     */
    public boolean isValidTotpCode(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        String normalizedCode = code.trim();
        if (!normalizedCode.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (generateTotp(secret, step).equals(normalizedCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Derives the time-based one-time password for one time step.
     *
     * @param secret Base32-encoded MFA secret material.
     * @param timeStep Time step counter used as the moving factor.
     * @return The six-digit code for that step.
     * @throws IllegalStateException When the code cannot be derived from {@code secret}.
     */
    public String generateTotp(String secret, long timeStep) {
        byte[] decodedSecret = decodeBase32(secret);
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(decodedSecret, ALGORITHM));
            byte[] hash = mac.doFinal(timeBytes);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate MFA code", exception);
        }
    }

    /**
     * Creates one-time recovery codes for MFA enrollment.
     * Callers persist hashes of these codes and show the plaintext only while enrollment starts.
     *
     * @param count Number of recovery codes to create.
     * @return The new recovery codes in plaintext.
     */
    public List<String> generateRecoveryCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(generateRecoveryCode());
        }
        return codes;
    }

    /**
     * Returns a bcrypt hash of a recovery code so the plaintext code is not stored.
     *
     * @param recoveryCode Recovery code to hash. Whitespace and hyphens are removed before hashing.
     * @return The bcrypt hash of the normalized recovery code.
     */
    public String hashRecoveryCode(String recoveryCode) {
        return BcryptUtil.bcryptHash(normalizeRecoveryCode(recoveryCode));
    }

    /**
     * Checks a recovery code against a stored bcrypt hash.
     *
     * @param candidate Recovery code presented by the operator.
     * @param hash Stored bcrypt hash of a recovery code.
     * @return {@code true} when the normalized candidate matches {@code hash}.
     */
    public boolean matchesRecoveryCode(String candidate, String hash) {
        if (candidate == null || hash == null || hash.isBlank()) {
            return false;
        }
        String normalized = normalizeRecoveryCode(candidate);
        if (normalized.isEmpty()) {
            return false;
        }
        return BcryptUtil.matches(normalized, hash);
    }

    /**
     * Removes whitespace and hyphens from a recovery code and converts it to upper case.
     *
     * @param recoveryCode Recovery code to normalize.
     * @return The normalized code, or an empty string when {@code recoveryCode} is null.
     */
    static String normalizeRecoveryCode(String recoveryCode) {
        if (recoveryCode == null) {
            return "";
        }
        return recoveryCode.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Creates one recovery code from random characters.
     *
     * @return The new recovery code in plaintext.
     */
    private static String generateRecoveryCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    /**
     * Percent-encodes a value for an otpauth URI.
     *
     * @param value Label or issuer text to encode.
     * @return The encoded value, with spaces represented as {@code %20}.
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Encodes MFA secret material as Base32 without padding.
     *
     * @param bytes Raw secret material.
     * @return The Base32 encoding of {@code bytes}.
     */
    private static String encodeBase32(byte[] bytes) {
        StringBuilder builder = new StringBuilder((bytes.length + 7) * 8 / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                bitsLeft -= 5;
                builder.append(BASE32_ALPHABET[index]);
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1f;
            builder.append(BASE32_ALPHABET[index]);
        }
        return builder.toString();
    }

    /**
     * Decodes Base32 MFA secret material.
     *
     * @param value Base32 text, ignoring surrounding whitespace and padding.
     * @return The decoded secret material.
     * @throws IllegalArgumentException When {@code value} contains a character outside the Base32 alphabet.
     */
    private static byte[] decodeBase32(String value) {
        String normalized = value.trim().replace("=", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        byte[] output = new byte[normalized.length() * 5 / 8 + 1];
        int outputIndex = 0;
        for (char character : normalized.toCharArray()) {
            int index = base32Index(character);
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[outputIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        byte[] decoded = new byte[outputIndex];
        System.arraycopy(output, 0, decoded, 0, outputIndex);
        return decoded;
    }

    /**
     * Maps one Base32 character to its alphabet index.
     *
     * @param character Character to look up.
     * @return The zero-based index of {@code character} in the Base32 alphabet.
     * @throws IllegalArgumentException When {@code character} is not in the alphabet.
     */
    private static int base32Index(char character) {
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            if (BASE32_ALPHABET[i] == character) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid base32 character: " + character);
    }
}
