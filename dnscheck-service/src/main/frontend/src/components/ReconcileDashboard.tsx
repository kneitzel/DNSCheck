import {
  Alert,
  Box,
  Button,
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import {
  ReconciliationStatusResponse,
  ZoneOutcome,
  type ReconciliationStatusResponse as ReconciliationStatusResponseType,
  type ZoneOutcome as ZoneOutcomeType,
} from "../generated";

type ReconcileDashboardProps = {
  status: ReconciliationStatusResponseType | null;
  loading: boolean;
  onTriggerReconcile: () => void;
};

const OUTCOME_LABELS: Record<ZoneOutcomeType.outcome, string> = {
  [ZoneOutcome.outcome.SUCCESS]: "Success",
  [ZoneOutcome.outcome.RECONCILED]: "Reconciled",
  [ZoneOutcome.outcome.DRIFT_DETECTED]: "Drift detected",
  [ZoneOutcome.outcome.FAILED]: "Failed",
};

const OUTCOME_COLORS: Record<
  ZoneOutcomeType.outcome,
  "default" | "success" | "warning" | "error" | "info"
> = {
  [ZoneOutcome.outcome.SUCCESS]: "success",
  [ZoneOutcome.outcome.RECONCILED]: "info",
  [ZoneOutcome.outcome.DRIFT_DETECTED]: "warning",
  [ZoneOutcome.outcome.FAILED]: "error",
};

function formatRunStatus(status: ReconciliationStatusResponseType): string {
  switch (status.status) {
    case ReconciliationStatusResponse.status.NEVER_RUN:
      return "Never run";
    case ReconciliationStatusResponse.status.COMPLETED:
      return "Completed";
    case ReconciliationStatusResponse.status.FAILED:
      return "Failed";
    default:
      return status.status;
  }
}

export function ReconcileDashboard({ status, loading, onTriggerReconcile }: ReconcileDashboardProps) {
  const outcomes = status?.zoneOutcomes ?? [];
  const failedOutcomes = outcomes.filter((outcome) => outcome.outcome === ZoneOutcome.outcome.FAILED);
  const successCount = outcomes.length - failedOutcomes.length;

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        Zone reconciliation
      </Typography>
      <Typography color="text.secondary" paragraph>
        DNSCheck guards managed DNS records against drift and reconciles via BIND nsupdate.
      </Typography>
      <Button variant="contained" onClick={onTriggerReconcile} disabled={loading} sx={{ mb: 2 }}>
        {loading ? "Reconciling..." : "Dry-run reconcile"}
      </Button>

      {status && (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Run status: {formatRunStatus(status)}
            {status.startedAt ? ` · started ${status.startedAt}` : ""}
            {` · ${status.dryRun ? "dry-run" : "live"}`}
          </Typography>

          {failedOutcomes.length > 0 && (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
              {failedOutcomes.map((outcome) => (
                <Alert key={outcome.zone} severity="error">
                  <strong>{outcome.zone}</strong>
                  {outcome.error ? `: ${outcome.error}` : ""}
                </Alert>
              ))}
            </Box>
          )}

          {successCount > 0 && (
            <Typography variant="body2">
              {successCount} zone{successCount === 1 ? "" : "s"} succeeded
              {failedOutcomes.length > 0 ? " (see table below for all outcomes)" : ""}.
            </Typography>
          )}

          {outcomes.length > 0 && (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Zone</TableCell>
                  <TableCell>Outcome</TableCell>
                  <TableCell>Error</TableCell>
                  <TableCell>Planned changes</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {outcomes.map((outcome) => (
                  <TableRow key={outcome.zone}>
                    <TableCell>{outcome.zone}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        color={OUTCOME_COLORS[outcome.outcome]}
                        label={OUTCOME_LABELS[outcome.outcome]}
                      />
                    </TableCell>
                    <TableCell>{outcome.error ?? "—"}</TableCell>
                    <TableCell>
                      {outcome.plannedChanges.length > 0 ? outcome.plannedChanges.join("; ") : "—"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Box>
      )}
    </Paper>
  );
}
