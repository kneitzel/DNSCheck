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
@Table(name = "global_grants")
public class GlobalGrant extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    public Operator operator;

    @ManyToOne(fetch = FetchType.LAZY)
    public Group group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public GlobalRightKind kind;

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
