package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds effective-rights summaries for operators and groups.
 */
public final class RightsSummaryBuilder {

    /**
     * Prevents instantiation.
     */
    private RightsSummaryBuilder() {}

    /**
     * Builds the global rights and supplemental zone grants effective for an operator, including rights inherited
     * from the operator's groups.
     *
     * @param operator Operator whose grants are summarized.
     * @param readOnly When {@code true}, sources are marked not editable and a group's zone grants are attributed
     *     to the group. When {@code false}, a persisted grant is cited by its identifier and marked editable.
     * @return The effective global rights and zone rights.
     */
    public static RightsSummary forOperator(Operator operator, boolean readOnly) {
        List<GlobalRightEntry> globalRights = new ArrayList<>();
        for (GlobalRightKind kind : GlobalRightKind.values()) {
            globalRights.add(buildGlobalEntryForOperator(operator, kind, readOnly));
        }
        globalRights.sort(Comparator.comparing(entry -> entry.kind().name()));

        Map<ZoneRightKey, List<RightSource>> zoneSources = new TreeMap<>(Comparator
                .comparing(ZoneRightKey::ownerFolder)
                .thenComparing(ZoneRightKey::zoneName)
                .thenComparing(key -> key.kind().name()));
        addOperatorZoneSources(zoneSources, operator, readOnly);
        for (Group group : operator.groups) {
            addGroupZoneSources(zoneSources, group, readOnly);
        }

        List<ZoneRightEntry> zoneRights = zoneSources.entrySet().stream()
                .map(entry -> new ZoneRightEntry(
                        entry.getKey().ownerFolder(),
                        entry.getKey().zoneName(),
                        entry.getKey().kind(),
                        entry.getValue()))
                .toList();
        return new RightsSummary(globalRights, zoneRights);
    }

    /**
     * Builds the global rights and supplemental zone grants assigned directly to a group.
     * Direct grants are marked editable and include the persisted grant identifier.
     *
     * @param group Group whose grants are summarized.
     * @return The group's global rights and zone rights.
     */
    public static RightsSummary forGroup(Group group) {
        List<GlobalRightEntry> globalRights = new ArrayList<>();
        for (GlobalRightKind kind : GlobalRightKind.values()) {
            List<RightSource> sources = GlobalGrant.<GlobalGrant>list("group = ?1 and kind = ?2", group, kind)
                    .stream()
                    .map(grant -> new RightSource(RightSourceType.DIRECT, null, grant.id, true))
                    .toList();
            globalRights.add(new GlobalRightEntry(kind, !sources.isEmpty(), sources));
        }
        globalRights.sort(Comparator.comparing(entry -> entry.kind().name()));

        Map<ZoneRightKey, List<RightSource>> zoneSources = new LinkedHashMap<>();
        addGroupZoneSources(zoneSources, group, false);
        List<ZoneRightEntry> zoneRights = zoneSources.entrySet().stream()
                .map(entry -> new ZoneRightEntry(
                        entry.getKey().ownerFolder(),
                        entry.getKey().zoneName(),
                        entry.getKey().kind(),
                        entry.getValue()))
                .toList();
        return new RightsSummary(globalRights, zoneRights);
    }

    /**
     * Collects direct and group sources for one global right of an operator.
     * A direct grant is editable only when {@code readOnly} is false. A group grant is never editable in this entry.
     *
     * @param operator Operator whose global right is summarized.
     * @param kind Global right (admin or zone-create) to describe.
     * @param readOnly When {@code true}, direct grants are marked not editable.
     * @return The global-right entry, including whether it is granted.
     */
    private static GlobalRightEntry buildGlobalEntryForOperator(
            Operator operator, GlobalRightKind kind, boolean readOnly) {
        List<RightSource> sources = new ArrayList<>();
        for (GlobalGrant grant : GlobalGrant.<GlobalGrant>list("operator = ?1 and kind = ?2", operator, kind)) {
            sources.add(new RightSource(RightSourceType.DIRECT, null, grant.id, !readOnly));
        }
        for (Group group : operator.groups) {
            for (GlobalGrant grant : GlobalGrant.<GlobalGrant>list("group = ?1 and kind = ?2", group, kind)) {
                sources.add(new RightSource(RightSourceType.GROUP, group.name, null, false));
            }
        }
        return new GlobalRightEntry(kind, !sources.isEmpty(), sources);
    }

    /**
     * Adds the operator's supplemental zone grants as direct sources.
     * Grants with a blank owner folder are omitted. Each source is editable only when {@code readOnly} is false.
     *
     * @param zoneSources Accumulator keyed by owner folder, zone name, and zone right.
     * @param operator Operator whose supplemental zone grants are added.
     * @param readOnly When {@code true}, the added sources are marked not editable.
     */
    private static void addOperatorZoneSources(
            Map<ZoneRightKey, List<RightSource>> zoneSources, Operator operator, boolean readOnly) {
        for (ZoneGrant grant : ZoneGrant.<ZoneGrant>list("operator = ?1", operator)) {
            if (grant.ownerFolder == null || grant.ownerFolder.isBlank()) {
                continue;
            }
            ZoneRightKey key = new ZoneRightKey(grant.ownerFolder, grant.zoneName, grant.kind);
            zoneSources
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new RightSource(RightSourceType.DIRECT, null, grant.id, !readOnly));
        }
    }

    /**
     * Adds a group's supplemental zone grants to the summary.
     * When {@code readOnly} is true, each source is attributed to the group and is not editable. Otherwise the
     * source cites the persisted grant and is editable. Grants with a blank owner folder are omitted.
     *
     * @param zoneSources Accumulator keyed by owner folder, zone name, and zone right.
     * @param group Group whose supplemental zone grants are added.
     * @param readOnly When {@code true}, sources are group attributions. When {@code false}, they are direct grants.
     */
    private static void addGroupZoneSources(
            Map<ZoneRightKey, List<RightSource>> zoneSources, Group group, boolean readOnly) {
        for (ZoneGrant grant : ZoneGrant.<ZoneGrant>list("group = ?1", group)) {
            if (grant.ownerFolder == null || grant.ownerFolder.isBlank()) {
                continue;
            }
            ZoneRightKey key = new ZoneRightKey(grant.ownerFolder, grant.zoneName, grant.kind);
            List<RightSource> sources = zoneSources.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (readOnly) {
                sources.add(new RightSource(RightSourceType.GROUP, group.name, null, false));
            } else {
                sources.add(new RightSource(RightSourceType.DIRECT, null, grant.id, true));
            }
        }
    }

    /**
     * Effective global rights and supplemental zone grants for one operator or group.
     */
    public record RightsSummary(
            /**
             * Global rights (admin and zone-create) and the grants that provide them.
             */
            List<GlobalRightEntry> globalRights,
            /**
             * Supplemental zone grants grouped by owner folder, zone name, and zone right.
             */
            List<ZoneRightEntry> zoneRights) {}

    /**
     * Whether one global right is granted, and which persisted grants provide it.
     */
    public record GlobalRightEntry(
            /**
             * Global right (admin or zone-create) described by this entry.
             */
            GlobalRightKind kind,
            /**
             * Whether at least one source grants {@code kind}.
             */
            boolean granted,
            /**
             * Direct and group grants that provide {@code kind}.
             */
            List<RightSource> sources) {}

    /**
     * Supplemental zone grants that provide one zone right on one zone catalog entry.
     */
    public record ZoneRightEntry(
            /**
             * Owner folder of the zone owner that holds the zone definition.
             */
            String ownerFolder,
            /**
             * Zone name of the zone catalog entry.
             */
            String zoneName,
            /**
             * Zone right (read or write) summarized for that entry.
             */
            ZoneRightKind kind,
            /**
             * Grants that provide {@code kind}.
             */
            List<RightSource> sources) {}

    /**
     * One persisted grant that contributes to an effective global right or zone right.
     */
    public record RightSource(
            /**
             * Whether the grant is assigned directly or inherited from a group.
             */
            RightSourceType type,
            /**
             * Name of the contributing group, or null for a direct grant.
             */
            String groupName,
            /**
             * Persisted identifier of the grant, or null when the summary does not cite a single row.
             */
            Long grantId,
            /**
             * Whether this summary marks the grant as removable.
             */
            boolean editable) {}

    /**
     * Whether a summarized right comes from a direct grant or from a group the operator belongs to.
     */
    public enum RightSourceType {
        /**
         * The grant is assigned directly to the operator or group being summarized.
         */
        DIRECT,

        /**
         * The grant is assigned to a group the operator belongs to.
         */
        GROUP
    }

    /**
     * Lookup key for supplemental zone grants that share an owner folder, zone name, and zone right.
     */
    record ZoneRightKey(
            /**
             * Owner folder of the zone owner that holds the zone definition.
             */
            String ownerFolder,
            /**
             * Zone name of the zone catalog entry.
             */
            String zoneName,
            /**
             * Zone right (read or write) on that entry.
             */
            ZoneRightKind kind) {}
}
