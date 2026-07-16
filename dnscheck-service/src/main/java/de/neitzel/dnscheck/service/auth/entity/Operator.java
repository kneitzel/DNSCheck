package de.neitzel.dnscheck.service.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "operators")
public class Operator extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String username;

    @Column(nullable = false)
    public String passwordHash;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(name = "mfa_enabled", nullable = false)
    public boolean mfaEnabled = false;

    @Lob
    @Column(name = "mfa_pending_secret")
    public String mfaPendingSecret;

    @Lob
    @Column(name = "mfa_secret")
    public String mfaSecret;

    @Lob
    @Column(name = "mfa_recovery_code_hashes")
    public String mfaRecoveryCodeHashes;

    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    public Set<Group> groups = new HashSet<>();

    public static Operator findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
