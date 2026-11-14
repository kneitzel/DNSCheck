package de.neitzel.dnscheck.service.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Persisted assignment of a global right (admin or zone-create) to exactly one operator or one group.
 * Rows are stored in {@code global_grants} and identified by the generated Panache identifier.
 */
@Entity
@Table(name = "global_grants")
public class GlobalGrant extends PanacheEntity {

    /**
     * Operator that receives the global right, or null when the grant targets a group.
     * The association is optional and loaded lazily.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    public Operator operator;

    /**
     * Group that receives the global right, or null when the grant targets an operator.
     * The association is optional and loaded lazily.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    public Group group;

    /**
     * Global right that is granted. Stored as the enum name and not null.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public GlobalRightKind kind;

    /**
     * Rejects a row that does not target exactly one operator or one group.
     *
     * @throws IllegalStateException When both grantees are set or neither grantee is set.
     */
    @PrePersist
    @PreUpdate
    void validateGrantee() {
        boolean hasOperator = operator != null;
        boolean hasGroup = group != null;
        if (hasOperator == hasGroup) {
            throw new IllegalStateException("Global grant must target exactly one operator or group");
        }
    }
}
