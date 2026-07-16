import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, Navigate, useParams } from "react-router-dom";
import { Box, Link, Typography } from "@mui/material";
import {
  AdminService,
  ZonesService,
  type GroupSummary,
  type OperatorSummary,
  type RightsSummaryResponse,
} from "../generated";
import { ApiError } from "../generated/core/ApiError";
import { GroupKeysPanel } from "../components/GroupKeysPanel";
import { OperatorMembershipPanel } from "../components/GroupMembershipPanel";
import { AddZoneGrantControls, RightsSummaryPanel, type ZoneCatalogOption } from "../components/RightsSummaryPanel";

export function GroupDetailPage() {
  const { groupId } = useParams();
  const id = Number(groupId);
  const [group, setGroup] = useState<GroupSummary | null>(null);
  const [rights, setRights] = useState<RightsSummaryResponse | null>(null);
  const [operators, setOperators] = useState<OperatorSummary[]>([]);
  const [zoneOptions, setZoneOptions] = useState<ZoneCatalogOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [forbidden, setForbidden] = useState(false);

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setForbidden(false);
    try {
      const groupResponse = await AdminService.getGroup(id);
      setGroup(groupResponse);
      try {
        await AdminService.listOperators();
        setIsAdmin(true);
        const [rightsResponse, operatorsResponse, zonesResponse] = await Promise.all([
          AdminService.getGroupRights(id),
          AdminService.listOperators(),
          ZonesService.listZones(),
        ]);
        setRights(rightsResponse);
        setOperators(operatorsResponse.operators);
        setZoneOptions(
          zonesResponse.entries.map((entry) => ({
            ownerFolder: entry.ownerFolder,
            zoneName: entry.zone,
          })),
        );
      } catch {
        setIsAdmin(false);
        setRights(null);
        setOperators([]);
        setZoneOptions([]);
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        setForbidden(true);
      }
      setGroup(null);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const addMember = async (operatorId: number) => {
    setLoading(true);
    try {
      await AdminService.addGroupMember(id, operatorId);
      await load();
    } finally {
      setLoading(false);
    }
  };

  const removeMember = async (operatorId: number) => {
    setLoading(true);
    try {
      await AdminService.removeGroupMember(id, operatorId);
      await load();
    } finally {
      setLoading(false);
    }
  };

  const grantGlobal = async (kind: RightsSummaryResponse["globalRights"][number]["kind"]) => {
    setLoading(true);
    try {
      await AdminService.createGlobalGrant({ kind, groupId: id });
      await load();
    } finally {
      setLoading(false);
    }
  };

  const revokeGlobal = async (grantId: number) => {
    setLoading(true);
    try {
      await AdminService.deleteGlobalGrant(grantId);
      await load();
    } finally {
      setLoading(false);
    }
  };

  const grantZone = async (
    ownerFolder: string,
    zoneName: string,
    kind: RightsSummaryResponse["zoneRights"][number]["kind"],
  ) => {
    setLoading(true);
    try {
      await AdminService.createZoneGrant({ ownerFolder, zoneName, kind, groupId: id });
      await load();
    } finally {
      setLoading(false);
    }
  };

  const revokeZone = async (grantId: number) => {
    setLoading(true);
    try {
      await AdminService.deleteZoneGrant(grantId);
      await load();
    } finally {
      setLoading(false);
    }
  };

  if (forbidden) {
    return <Navigate to="/" replace />;
  }

  if (!group) {
    return <Typography>Loading…</Typography>;
  }

  return (
    <Box>
      <Typography variant="body2" sx={{ mb: 1 }}>
        {isAdmin ? (
          <Link component={RouterLink} to="/admin/groups">
            Groups
          </Link>
        ) : (
          "Group"
        )}
        {" / "}
        {group.name}
      </Typography>
      <Typography variant="h4" gutterBottom>
        {group.name}
      </Typography>

      <GroupKeysPanel groupId={id} canManage={isAdmin} />

      {isAdmin && rights && (
        <>
          <OperatorMembershipPanel
            memberUsernames={group.memberUsernames}
            allOperators={operators}
            loading={loading}
            onAdd={(operatorId) => void addMember(operatorId)}
            onRemove={(operatorId) => void removeMember(operatorId)}
          />

          <RightsSummaryPanel
            title="Group rights"
            rights={rights}
            loading={loading}
            onGrantGlobal={(kind) => void grantGlobal(kind)}
            onRevokeGlobal={(grantId) => void revokeGlobal(grantId)}
            onRevokeZone={(grantId) => void revokeZone(grantId)}
            addZoneGrant={
              <AddZoneGrantControls
                zoneOptions={zoneOptions}
                loading={loading}
                onGrant={(ownerFolder, zoneName, kind) => void grantZone(ownerFolder, zoneName, kind)}
              />
            }
          />
        </>
      )}
    </Box>
  );
}
