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

@ApplicationScoped
public final class MfaCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ISSUER = "DNSCheck";
    private static final int DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int SECRET_BYTES = 20;
    private static final String ALGORITHM = "HmacSHA1";
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String buildOtpAuthUri(String username, String secret) {
        String label = urlEncode(ISSUER + ":" + username);
        String issuer = urlEncode(ISSUER);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer + "&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

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

    public List<String> generateRecoveryCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(generateRecoveryCode());
        }
        return codes;
    }

    public String hashRecoveryCode(String recoveryCode) {
        return BcryptUtil.bcryptHash(normalizeRecoveryCode(recoveryCode));
    }

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

    static String normalizeRecoveryCode(String recoveryCode) {
        if (recoveryCode == null) {
            return "";
        }
        return recoveryCode.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    private static String generateRecoveryCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

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

    private static int base32Index(char character) {
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            if (BASE32_ALPHABET[i] == character) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid base32 character: " + character);
    }
}
