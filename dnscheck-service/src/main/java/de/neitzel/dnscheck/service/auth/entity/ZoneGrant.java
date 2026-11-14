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
 * Supplemental zone grant of a zone right (read or write) on one zone catalog entry.
 * The grant is assigned to exactly one operator or one group. Rows are stored in {@code zone_grants} and
 * identified by the generated Panache identifier.
 */
@Entity
@Table(name = "zone_grants")
public class ZoneGrant extends PanacheEntity {

    /**
     * Operator that receives the supplemental zone grant, or null when the grant targets a group.
     * The association is optional and loaded lazily.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    public Operator operator;

    /**
     * Group that receives the supplemental zone grant, or null when the grant targets an operator.
     * The association is optional and loaded lazily.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    public Group group;

    /**
     * Zone name of the zone catalog entry. The column is not null.
     */
    @Column(nullable = false)
    public String zoneName;

    /**
     * Owner folder for supplemental grants ({@code admin.u} / {@code group.g}).
     */
    @Column(name = "owner_folder", nullable = false)
    public String ownerFolder;

    /**
     * Zone right (read or write) that is granted. Stored as the enum name and not null.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ZoneRightKind kind;

    /**
     * Rejects a supplemental zone grant that lacks exactly one grantee, an owner folder, or a zone name.
     *
     * @throws IllegalStateException When the grantee is not exactly one operator or group, or the owner folder
     *     or zone name is blank.
     */
    @PrePersist
    @PreUpdate
    void validateGrantee() {
        boolean hasOperator = operator != null;
        boolean hasGroup = group != null;
        if (hasOperator == hasGroup) {
            throw new IllegalStateException("Zone grant must target exactly one operator or group");
        }
        if (ownerFolder == null || ownerFolder.isBlank()) {
            throw new IllegalStateException("Zone grant requires ownerFolder");
        }
        if (zoneName == null || zoneName.isBlank()) {
            throw new IllegalStateException("Zone grant requires zoneName");
        }
    }
}
