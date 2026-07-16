import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Link,
  Paper,
  TextField,
  Typography,
} from "@mui/material";
import {
  AdminService,
  ZonesService,
  type GroupSummary,
  type OperatorSummary,
  type RightsSummaryResponse,
} from "../generated";
import { formatApiError } from "../apiErrorMessage";
import { GroupMembershipPanel } from "../components/GroupMembershipPanel";
import { AddZoneGrantControls, RightsSummaryPanel, type ZoneCatalogOption } from "../components/RightsSummaryPanel";

export function OperatorDetailPage() {
  const { operatorId } = useParams();
  const id = Number(operatorId);
  const [operator, setOperator] = useState<OperatorSummary | null>(null);
  const [rights, setRights] = useState<RightsSummaryResponse | null>(null);
  const [groups, setGroups] = useState<GroupSummary[]>([]);
  const [zoneOptions, setZoneOptions] = useState<ZoneCatalogOption[]>([]);
  const [newPassword, setNewPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    const [operatorResponse, rightsResponse, groupsResponse, zonesResponse] = await Promise.all([
      AdminService.getOperator(id),
      AdminService.getOperatorRights(id),
      AdminService.listGroups(),
      ZonesService.listZones(),
    ]);
    setOperator(operatorResponse);
    setRights(rightsResponse);
    setGroups(groupsResponse.groups);
    setZoneOptions(
      zonesResponse.entries.map((entry) => ({
        ownerFolder: entry.ownerFolder,
        zoneName: entry.zone,
      })),
    );
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const resetPassword = async () => {
    if (!operator || !newPassword) {
      return;
    }
    setActionError(null);
    setActionSuccess(null);
    setLoading(true);
    try {
      await AdminService.updateOperator(operator.id, { password: newPassword });
      setNewPassword("");
      setActionSuccess("Password updated.");
    } catch (error) {
      setActionError(formatApiError(error));
    } finally {
      setLoading(false);
    }
  };

  const resetMfa = async () => {
    if (!operator) {
      return;
    }
    setActionError(null);
    setActionSuccess(null);
    setLoading(true);
    try {
      await AdminService.resetOperatorMfa(operator.id);
      await load();
      setActionSuccess("MFA enrollment and recovery codes cleared. The operator can sign in with password only until they enroll again.");
    } catch (error) {
      setActionError(formatApiError(error));
    } finally {
      setLoading(false);
    }
  };

  const addToGroup = async (groupId: number) => {
    setLoading(true);
    try {
      await AdminService.addGroupMember(groupId, id);
      await load();
    } finally {
      setLoading(false);
    }
  };

  const removeFromGroup = async (groupId: number) => {
    setLoading(true);
    try {
      await AdminService.removeGroupMember(groupId, id);
      await load();
    } finally {
      setLoading(false);
    }
  };

  const grantGlobal = async (kind: RightsSummaryResponse["globalRights"][number]["kind"]) => {
    setLoading(true);
    try {
      await AdminService.createGlobalGrant({ kind, operatorId: id });
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
      await AdminService.createZoneGrant({ ownerFolder, zoneName, kind, operatorId: id });
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

  if (!operator || !rights) {
    return <Typography>Loading…</Typography>;
  }

  return (
    <Box>
      <Typography variant="body2" sx={{ mb: 1 }}>
        <Link component={RouterLink} to="/admin/operators">
          Operators
        </Link>
        {" / "}
        {operator.username}
      </Typography>
      <Typography variant="h4" gutterBottom>
        {operator.username}
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Reset password
        </Typography>
        <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
          <TextField
            label="New password"
            type="password"
            size="small"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <Button
            variant="contained"
            disabled={loading || !newPassword}
            onClick={() => void resetPassword()}
          >
            Set password
          </Button>
        </Box>
      </Paper>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          MFA reset
        </Typography>
        <Alert severity="warning" sx={{ mb: 2 }}>
          This clears the operator's active MFA enrollment, recovery codes, and pending setup state.
        </Alert>
        {actionError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {actionError}
          </Alert>
        )}
        {actionSuccess && (
          <Alert severity="success" sx={{ mb: 2 }}>
            {actionSuccess}
          </Alert>
        )}
        <Button variant="outlined" color="warning" disabled={loading} onClick={() => void resetMfa()}>
          Reset MFA enrollment
        </Button>
      </Paper>

      <GroupMembershipPanel
        memberGroupNames={operator.groupNames}
        allGroups={groups}
        loading={loading}
        onAdd={(groupId) => void addToGroup(groupId)}
        onRemove={(groupId) => void removeFromGroup(groupId)}
      />

      <RightsSummaryPanel
        title="Effective rights"
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
    </Box>
  );
}
