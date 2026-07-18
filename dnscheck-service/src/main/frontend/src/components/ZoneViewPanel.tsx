import { useState } from "react";
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Menu,
  MenuItem,
  Paper,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import type { ZoneViewRecord, ZoneViewResponse } from "../generated";
import { STATUS_COLORS, STATUS_LABELS } from "../zoneViewConstants";
import { ScopedPathLabel } from "./OwnerFolderLabel";

type ZoneViewPanelProps = {
  selectedZone: string;
  zoneView: ZoneViewResponse;
  loading: boolean;
  canWrite: boolean;
  recordName: string;
  recordType: string;
  recordRdata: string;
  recordTtl: string;
  recordEnforce: boolean;
  modifyRecord: ZoneViewRecord | null;
  modifyRdata: string;
  modifyTtl: string;
  onRefresh: () => void;
  showReconcileActions: boolean;
  onDryRunReconcile: () => void;
  onLiveReconcile: () => void;
  onRecordNameChange: (value: string) => void;
  onRecordTypeChange: (value: string) => void;
  onRecordRdataChange: (value: string) => void;
  onRecordTtlChange: (value: string) => void;
  onRecordEnforceChange: (value: boolean) => void;
  onCreateRecord: () => void;
  onPromote: (record: ZoneViewRecord) => void;
  onDemote: (record: ZoneViewRecord) => void;
  onDelete: (record: ZoneViewRecord) => void;
  onMarkForDeletion: (record: ZoneViewRecord, rdataPattern: string) => void;
  onRemoveMarker: (record: ZoneViewRecord, rdataPattern: string) => void;
  onModifyOpen: (record: ZoneViewRecord) => void;
  onModifyClose: () => void;
  onModifyRdataChange: (value: string) => void;
  onModifyTtlChange: (value: string) => void;
  onSubmitModify: () => void;
};

export function ZoneViewPanel({
  selectedZone,
  zoneView,
  loading,
  canWrite,
  recordName,
  recordType,
  recordRdata,
  recordTtl,
  recordEnforce,
  modifyRecord,
  modifyRdata,
  modifyTtl,
  onRefresh,
  showReconcileActions,
  onDryRunReconcile,
  onLiveReconcile,
  onRecordNameChange,
  onRecordTypeChange,
  onRecordRdataChange,
  onRecordTtlChange,
  onRecordEnforceChange,
  onCreateRecord,
  onPromote,
  onDemote,
  onDelete,
  onMarkForDeletion,
  onRemoveMarker,
  onModifyOpen,
  onModifyClose,
  onModifyRdataChange,
  onModifyTtlChange,
  onSubmitModify,
}: ZoneViewPanelProps) {
  const [markRecord, setMarkRecord] = useState<ZoneViewRecord | null>(null);
  const [markPattern, setMarkPattern] = useState("");

  const openMarkDialog = (record: ZoneViewRecord) => {
    setMarkRecord(record);
    setMarkPattern("");
  };

  return (
    <>
      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
          <Typography variant="h5" component="div" sx={{ display: "flex", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
            Zone view: <ScopedPathLabel scopedPath={selectedZone} />
          </Typography>
          <Box sx={{ display: "flex", gap: 1 }}>
            {showReconcileActions && (
              <>
                <Button size="small" onClick={onDryRunReconcile} disabled={loading}>
                  Dry-run reconcile
                </Button>
                <Button size="small" variant="contained" onClick={onLiveReconcile} disabled={loading}>
                  Reconcile
                </Button>
              </>
            )}
            <Button size="small" onClick={onRefresh} disabled={loading}>
              Refresh view
            </Button>
          </Box>
        </Box>
        <Typography color="text.secondary" paragraph>
          Default TTL: {zoneView.defaultTtl}
        </Typography>
        {canWrite && (
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2, mb: 3 }}>
            <TextField
              label="Name"
              size="small"
              value={recordName}
              onChange={(event) => onRecordNameChange(event.target.value)}
              placeholder="wiki"
            />
            <TextField
              label="Type"
              size="small"
              value={recordType}
              onChange={(event) => onRecordTypeChange(event.target.value)}
              sx={{ width: 100 }}
            />
            <TextField
              label="Rdata"
              size="small"
              value={recordRdata}
              onChange={(event) => onRecordRdataChange(event.target.value)}
              sx={{ minWidth: 220 }}
            />
            <TextField
              label="TTL (optional)"
              size="small"
              value={recordTtl}
              onChange={(event) => onRecordTtlChange(event.target.value)}
              sx={{ width: 140 }}
            />
            <FormControlLabel
              control={
                <Switch
                  checked={recordEnforce}
                  onChange={(event) => onRecordEnforceChange(event.target.checked)}
                />
              }
              label="Enforce in zone definition"
            />
            <Button
              variant="contained"
              onClick={onCreateRecord}
              disabled={loading || !recordName || !recordType || !recordRdata}
            >
              Add record
            </Button>
          </Box>
        )}
        <ZoneRecordsTable
          records={zoneView.records}
          loading={loading}
          canWrite={canWrite}
          onPromote={onPromote}
          onDemote={onDemote}
          onDelete={onDelete}
          onMarkForDeletion={openMarkDialog}
          onRemoveMarker={(record) => onRemoveMarker(record, "")}
          onModify={onModifyOpen}
        />
      </Paper>

      <Dialog open={modifyRecord !== null} onClose={onModifyClose} fullWidth maxWidth="sm">
        <DialogTitle>Edit record</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {modifyRecord?.name} {modifyRecord?.type}
            {modifyRecord?.status === "ad_hoc"
              ? " — live DNS only (not added to the zone definition)."
              : " — updates the zone definition and live DNS."}
          </Typography>
          {(modifyRecord?.status === "enforced"
            || modifyRecord?.status === "drifted"
            || modifyRecord?.status === "missing") && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Expected: {modifyRecord.expectedRdata ?? "—"}
              {modifyRecord.expectedTtl != null ? ` (TTL ${modifyRecord.expectedTtl})` : ""}
            </Typography>
          )}
          <TextField
            label="New rdata"
            fullWidth
            margin="normal"
            value={modifyRdata}
            onChange={(event) => onModifyRdataChange(event.target.value)}
          />
          <TextField
            label="New TTL (optional)"
            fullWidth
            margin="normal"
            value={modifyTtl}
            onChange={(event) => onModifyTtlChange(event.target.value)}
            helperText={
              modifyRecord?.status === "ad_hoc"
                ? "Leave empty to keep the current live TTL."
                : undefined
            }
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={onModifyClose}>Cancel</Button>
          <Button variant="contained" onClick={onSubmitModify} disabled={loading}>
            Save changes
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={markRecord !== null} onClose={() => setMarkRecord(null)} fullWidth maxWidth="sm">
        <DialogTitle>Mark for deletion</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Adds a deletion marker to the zone definition. Reconciliation removes matching live records.
          </Typography>
          <Typography variant="body2" sx={{ mb: 2 }}>
            {markRecord?.name} {markRecord?.type}
          </Typography>
          <TextField
            label="Rdata glob pattern (optional)"
            fullWidth
            margin="normal"
            value={markPattern}
            onChange={(event) => setMarkPattern(event.target.value)}
            placeholder="Leave empty to match all rdata at this name"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMarkRecord(null)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            disabled={loading || markRecord === null}
            onClick={() => {
              if (markRecord) {
                onMarkForDeletion(markRecord, markPattern);
                setMarkRecord(null);
              }
            }}
          >
            Mark for deletion
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function statusHint(record: ZoneViewRecord): string | undefined {
  if (record.status !== "drifted" && record.status !== "missing") {
    return undefined;
  }
  const expectedRdata = record.expectedRdata ?? "—";
  const expectedTtl = record.expectedTtl != null ? String(record.expectedTtl) : "—";
  return `Expected rdata: ${expectedRdata}; expected TTL: ${expectedTtl}`;
}

function ZoneRecordsTable({
  records,
  loading,
  canWrite,
  onPromote,
  onDemote,
  onDelete,
  onMarkForDeletion,
  onRemoveMarker,
  onModify,
}: {
  records: ZoneViewRecord[];
  loading: boolean;
  canWrite: boolean;
  onPromote: (record: ZoneViewRecord) => void;
  onDemote: (record: ZoneViewRecord) => void;
  onDelete: (record: ZoneViewRecord) => void;
  onMarkForDeletion: (record: ZoneViewRecord) => void;
  onRemoveMarker: (record: ZoneViewRecord) => void;
  onModify: (record: ZoneViewRecord) => void;
}) {
  return (
    <TableContainer sx={{ overflowX: "auto" }}>
      <Table size="small" sx={{ minWidth: 560 }}>
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Rdata</TableCell>
            <TableCell>TTL</TableCell>
            {canWrite && <TableCell align="right">Actions</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {records.map((record) => {
            const hint = statusHint(record);
            const chip = (
              <Chip
                size="small"
                color={STATUS_COLORS[record.status] ?? "default"}
                label={STATUS_LABELS[record.status] ?? record.status}
              />
            );
            return (
              <TableRow key={`${record.name}-${record.type}-${record.rdata ?? record.expectedRdata}`}>
                <TableCell>{record.name}</TableCell>
                <TableCell>{record.type}</TableCell>
                <TableCell>{hint ? <Tooltip title={hint}>{chip}</Tooltip> : chip}</TableCell>
                <TableCell
                  sx={{
                    maxWidth: 240,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {record.rdata ?? "—"}
                </TableCell>
                <TableCell>{record.ttl ?? "—"}</TableCell>
                {canWrite && (
                  <TableCell align="right">
                    <RecordActionsMenu
                      record={record}
                      loading={loading}
                      onPromote={onPromote}
                      onDemote={onDemote}
                      onDelete={onDelete}
                      onMarkForDeletion={onMarkForDeletion}
                      onRemoveMarker={onRemoveMarker}
                      onModify={onModify}
                    />
                  </TableCell>
                )}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function RecordActionsMenu({
  record,
  loading,
  onPromote,
  onDemote,
  onDelete,
  onMarkForDeletion,
  onRemoveMarker,
  onModify,
}: {
  record: ZoneViewRecord;
  loading: boolean;
  onPromote: (record: ZoneViewRecord) => void;
  onDemote: (record: ZoneViewRecord) => void;
  onDelete: (record: ZoneViewRecord) => void;
  onMarkForDeletion: (record: ZoneViewRecord) => void;
  onRemoveMarker: (record: ZoneViewRecord) => void;
  onModify: (record: ZoneViewRecord) => void;
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const open = Boolean(anchor);

  const close = () => setAnchor(null);

  const run = (action: () => void) => {
    close();
    action();
  };

  return (
    <>
      <IconButton
        size="small"
        aria-label="Record actions"
        disabled={loading}
        onClick={(event) => setAnchor(event.currentTarget)}
      >
        ⋮
      </IconButton>
      <Menu anchorEl={anchor} open={open} onClose={close}>
        {record.status === "ad_hoc" && (
          <>
            <MenuItem onClick={() => run(() => onModify(record))}>Edit</MenuItem>
            <MenuItem onClick={() => run(() => onPromote(record))}>Promote</MenuItem>
            <MenuItem onClick={() => run(() => onMarkForDeletion(record))}>Mark for deletion</MenuItem>
            <MenuItem onClick={() => run(() => onDelete(record))}>Delete record</MenuItem>
          </>
        )}
        {(record.status === "enforced" || record.status === "drifted") && (
          <>
            <MenuItem onClick={() => run(() => onModify(record))}>Edit</MenuItem>
            <MenuItem onClick={() => run(() => onDemote(record))}>Demote</MenuItem>
            <MenuItem onClick={() => run(() => onDelete(record))}>Delete record</MenuItem>
          </>
        )}
        {record.status === "missing" && (
          <MenuItem onClick={() => run(() => onDemote(record))}>Remove</MenuItem>
        )}
        {record.status === "marked_for_deletion" && (
          <>
            <MenuItem onClick={() => run(() => onRemoveMarker(record))}>Remove marker</MenuItem>
            <MenuItem onClick={() => run(() => onDelete(record))}>Delete record</MenuItem>
          </>
        )}
      </Menu>
    </>
  );
}
