package de.neitzel.dnscheck.service.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "operator_groups")
public class Group extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String name;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "operator_group_memberships",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "operator_id"))
    public Set<Operator> members = new HashSet<>();

    public static Group findByName(String name) {
        return find("name", name).firstResult();
    }
}
