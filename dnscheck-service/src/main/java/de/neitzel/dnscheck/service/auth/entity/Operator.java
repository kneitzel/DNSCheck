package de.neitzel.dnscheck.service.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.util.HashSet;
import java.util.Set;

/**
 * Persisted operator account: sign-in hash, multi-factor authentication state, and group membership.
 * Rows are stored in {@code operators} and identified by the generated Panache identifier.
 */
@Entity
@Table(name = "operators")
public class Operator extends PanacheEntity {

    /**
     * Unique operator username. The column is not null. The username identifies this operator's owner folder.
     */
    @Column(nullable = false, unique = true)
    public String username;

    /**
     * Bcrypt hash of the operator's password. The column is not null. The plaintext password is not stored.
     */
    @Column(nullable = false)
    public String passwordHash;

    /**
     * Whether the operator may sign in. The column is not null and defaults to enabled.
     */
    @Column(nullable = false)
    public boolean enabled = true;

    /**
     * DEFAULT is required so schema-update can add this NOT NULL column to databases
     * that already contain operator rows (Hibernate otherwise fails the ALTER and leaves
     * the column missing, which breaks login).
     */
    @Column(name = "mfa_enabled", nullable = false)
    @ColumnDefault("false")
    public boolean mfaEnabled = false;

    /**
     * Secret material for a pending MFA enrollment. The column is a nullable large object.
     * It is cleared when enrollment is confirmed, cancelled, or reset.
     */
    @Lob
    @Column(name = "mfa_pending_secret")
    public String mfaPendingSecret;

    /**
     * Secret material for active multi-factor authentication. The column is a nullable large object.
     * It is absent while multi-factor authentication is disabled.
     */
    @Lob
    @Column(name = "mfa_secret")
    public String mfaSecret;

    /**
     * Bcrypt hashes of unused recovery codes, one hash per line. The column is a nullable large object.
     * Plaintext recovery codes are not stored.
     */
    @Lob
    @Column(name = "mfa_recovery_code_hashes")
    public String mfaRecoveryCodeHashes;

    /**
     * Groups this operator belongs to. This is the inverse side of {@code operator_group_memberships} and is
     * loaded lazily.
     */
    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    public Set<Group> groups = new HashSet<>();

    /**
     * Loads an operator by unique username.
     *
     * @param username Operator username to find.
     * @return The matching operator, or null when no operator has that username.
     */
    public static Operator findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
