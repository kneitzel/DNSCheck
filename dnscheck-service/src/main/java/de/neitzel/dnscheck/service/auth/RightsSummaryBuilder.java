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

    private RightsSummaryBuilder() {}

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

    public record RightsSummary(List<GlobalRightEntry> globalRights, List<ZoneRightEntry> zoneRights) {}

    public record GlobalRightEntry(GlobalRightKind kind, boolean granted, List<RightSource> sources) {}

    public record ZoneRightEntry(
            String ownerFolder, String zoneName, ZoneRightKind kind, List<RightSource> sources) {}

    public record RightSource(RightSourceType type, String groupName, Long grantId, boolean editable) {}

    public enum RightSourceType {
        DIRECT,
        GROUP
    }

    record ZoneRightKey(String ownerFolder, String zoneName, ZoneRightKind kind) {}
}
