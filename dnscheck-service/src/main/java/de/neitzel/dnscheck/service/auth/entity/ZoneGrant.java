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

@Entity
@Table(name = "zone_grants")
public class ZoneGrant extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    public Operator operator;

    @ManyToOne(fetch = FetchType.LAZY)
    public Group group;

    @Column(nullable = false)
    public String zoneName;

    /**
     * Owner folder for supplemental grants ({@code admin.u} / {@code group.g}).
     */
    @Column(name = "owner_folder", nullable = false)
    public String ownerFolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ZoneRightKind kind;

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
