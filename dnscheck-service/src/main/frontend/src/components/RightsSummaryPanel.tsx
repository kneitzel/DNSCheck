import { useEffect, useState } from "react";
import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import {
  AdminZoneRight,
  type GlobalRightEntry,
  type RightSource,
  type RightsSummaryResponse,
  type ZoneRightEntry,
} from "../generated";

export type ZoneCatalogOption = {
  ownerFolder: string;
  zoneName: string;
};

function catalogOptionKey(option: ZoneCatalogOption): string {
  return `${option.ownerFolder}/${option.zoneName}`;
}

function formatSources(sources: RightSource[]): string {
  if (sources.length === 0) {
    return "—";
  }
  return sources
    .map((source) => (source.type === "direct" ? "Direct" : `Group: ${source.groupName ?? "?"}`))
    .join(", ");
}

type RightsSummaryPanelProps = {
  title: string;
  rights: RightsSummaryResponse;
  loading: boolean;
  readOnly?: boolean;
  onGrantGlobal?: (kind: GlobalRightEntry["kind"]) => void;
  onRevokeGlobal?: (grantId: number) => void;
  onRevokeZone?: (grantId: number) => void;
  addZoneGrant?: React.ReactNode;
};

export function RightsSummaryPanel({
  title,
  rights,
  loading,
  readOnly = false,
  onGrantGlobal,
  onRevokeGlobal,
  onRevokeZone,
  addZoneGrant,
}: RightsSummaryPanelProps) {
  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Typography variant="h6" gutterBottom>
        {title}
      </Typography>

      <Typography variant="subtitle1" sx={{ mt: 2 }}>
        Global rights
      </Typography>
      <Table size="small" sx={{ mb: 2 }}>
        <TableHead>
          <TableRow>
            <TableCell>Right</TableCell>
            <TableCell>Granted</TableCell>
            <TableCell>Sources</TableCell>
            {!readOnly && <TableCell>Actions</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {rights.globalRights.map((entry) => {
            const directSource = entry.sources.find(
              (source) => source.type === "direct" && source.editable && source.grantId,
            );
            return (
              <TableRow key={entry.kind}>
                <TableCell>{entry.kind}</TableCell>
                <TableCell>{entry.granted ? "Yes" : "No"}</TableCell>
                <TableCell>{formatSources(entry.sources)}</TableCell>
                {!readOnly && (
                  <TableCell>
                    {onGrantGlobal && !directSource && (
                      <Button size="small" disabled={loading} onClick={() => onGrantGlobal(entry.kind)}>
                        Grant direct
                      </Button>
                    )}
                    {onRevokeGlobal && directSource?.grantId != null && (
                      <Button
                        size="small"
                        color="error"
                        disabled={loading}
                        onClick={() => onRevokeGlobal(directSource.grantId!)}
                      >
                        Revoke direct
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      <Typography variant="subtitle1">Zone rights</Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Owner</TableCell>
            <TableCell>Zone</TableCell>
            <TableCell>Right</TableCell>
            <TableCell>Sources</TableCell>
            {!readOnly && <TableCell>Actions</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {rights.zoneRights.length === 0 ? (
            <TableRow>
              <TableCell colSpan={readOnly ? 4 : 5}>
                <Typography color="text.secondary">No zone rights granted.</Typography>
              </TableCell>
            </TableRow>
          ) : (
            rights.zoneRights.map((entry) => {
              const directSource = entry.sources.find(
                (source) => source.type === "direct" && source.editable && source.grantId,
              );
              return (
                <TableRow key={`${entry.ownerFolder}-${entry.zoneName}-${entry.kind}`}>
                  <TableCell>{entry.ownerFolder}</TableCell>
                  <TableCell>{entry.zoneName}</TableCell>
                  <TableCell>{entry.kind}</TableCell>
                  <TableCell>{formatSources(entry.sources)}</TableCell>
                  {!readOnly && (
                    <TableCell>
                      {onRevokeZone && directSource?.grantId != null && (
                        <Button
                          size="small"
                          color="error"
                          disabled={loading}
                          onClick={() => onRevokeZone(directSource.grantId!)}
                        >
                          Revoke direct
                        </Button>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              );
            })
          )}
        </TableBody>
      </Table>

      {!readOnly && addZoneGrant}
    </Paper>
  );
}

type AddZoneGrantControlsProps = {
  zoneOptions: ZoneCatalogOption[];
  loading: boolean;
  onGrant: (ownerFolder: string, zoneName: string, kind: ZoneRightEntry["kind"]) => void;
};

export function AddZoneGrantControls({ zoneOptions, loading, onGrant }: AddZoneGrantControlsProps) {
  const [selected, setSelected] = useState(zoneOptions[0] ? catalogOptionKey(zoneOptions[0]) : "");
  const [kind, setKind] = useState<AdminZoneRight>(AdminZoneRight.READ);

  useEffect(() => {
    const keys = zoneOptions.map(catalogOptionKey);
    if (keys.length > 0 && !keys.includes(selected)) {
      setSelected(keys[0]);
    }
  }, [zoneOptions, selected]);

  if (zoneOptions.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
        No zones in the catalog yet.
      </Typography>
    );
  }

  const selectedOption = zoneOptions.find((option) => catalogOptionKey(option) === selected);

  return (
    <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mt: 2, alignItems: "center" }}>
      <Typography variant="subtitle2">Add zone right</Typography>
      <FormControl size="small" sx={{ minWidth: 240 }}>
        <InputLabel id="zone-grant-zone">Catalog entry</InputLabel>
        <Select
          labelId="zone-grant-zone"
          label="Catalog entry"
          value={selected}
          onChange={(event) => setSelected(event.target.value)}
        >
          {zoneOptions.map((option) => (
            <MenuItem key={catalogOptionKey(option)} value={catalogOptionKey(option)}>
              {catalogOptionKey(option)}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
      <FormControl size="small" sx={{ minWidth: 120 }}>
        <InputLabel id="zone-grant-kind">Kind</InputLabel>
        <Select
          labelId="zone-grant-kind"
          label="Kind"
          value={kind}
          onChange={(event) => setKind(event.target.value as AdminZoneRight)}
        >
          <MenuItem value={AdminZoneRight.READ}>read</MenuItem>
          <MenuItem value={AdminZoneRight.WRITE}>write</MenuItem>
        </Select>
      </FormControl>
      <Button
        size="small"
        variant="contained"
        disabled={loading || !selectedOption}
        onClick={() => {
          if (selectedOption) {
            onGrant(selectedOption.ownerFolder, selectedOption.zoneName, kind);
          }
        }}
      >
        Grant
      </Button>
    </Box>
  );
}
