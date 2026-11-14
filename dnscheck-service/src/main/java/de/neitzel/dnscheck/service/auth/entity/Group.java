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

/**
 * Named group of operators. Members inherit the group's global rights and supplemental zone grants.
 * Rows are stored in {@code operator_groups} and identified by the generated Panache identifier.
 */
@Entity
@Table(name = "operator_groups")
public class Group extends PanacheEntity {

    /**
     * Unique group name. The column is not null. The name is the identifier of this group's owner folder.
     */
    @Column(nullable = false, unique = true)
    public String name;

    /**
     * Operators that belong to this group. Membership is stored in {@code operator_group_memberships} and loaded
     * lazily.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "operator_group_memberships",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "operator_id"))
    public Set<Operator> members = new HashSet<>();

    /**
     * Loads a group by its unique name.
     *
     * @param name Group name to find.
     * @return The matching group, or null when no group has that name.
     */
    public static Group findByName(String name) {
        return find("name", name).firstResult();
    }
}
