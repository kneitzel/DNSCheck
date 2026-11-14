package de.neitzel.dnscheck.core.key;

/**
 * TSIG key material used to authenticate AXFR and nsupdate.
 *
 * @param name the TSIG key name.
 * @param algorithm the TSIG algorithm name as stored in the key file.
 * @param secret the shared secret.
 */
public record TsigKey(String name, String algorithm, String secret) {

    /**
     * Requires a key name, an algorithm, and a secret.
     *
     * @throws IllegalArgumentException when {@code name}, {@code algorithm}, or {@code secret} is blank.
     */
    public TsigKey {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("TSIG key name is required");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("TSIG algorithm is required");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("TSIG secret is required");
        }
    }
}
