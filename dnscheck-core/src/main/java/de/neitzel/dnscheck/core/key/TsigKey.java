package de.neitzel.dnscheck.core.key;

public record TsigKey(String name, String algorithm, String secret) {

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
