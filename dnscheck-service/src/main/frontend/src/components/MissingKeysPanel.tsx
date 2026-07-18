import { useEffect, useState } from "react";
import {
  Alert,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { ApiError } from "../generated/core/ApiError";
import { KeysService, type MissingKeyEntry } from "../generated";
import { formatApiError } from "../apiErrorMessage";

function missingKeysLoadError(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return "Could not load missing key summary (not authenticated). Sign in again and refresh.";
  }
  const detail = formatApiError(error);
  if (detail && detail !== "An unexpected error occurred.") {
    return `Could not load missing key summary: ${detail}`;
  }
  return "Could not load missing key summary.";
}

export function MissingKeysPanel() {
  const [missing, setMissing] = useState<MissingKeyEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    void (async () => {
      try {
        const response = await KeysService.listMissingKeys();
        setMissing(response.missing ?? []);
        setError(null);
      } catch (loadError) {
        setError(missingKeysLoadError(loadError));
        setMissing([]);
      } finally {
        setLoaded(true);
      }
    })();
  }, []);

  if (!loaded) {
    return null;
  }

  if (error) {
    return (
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h5" gutterBottom>
          Missing keys
        </Typography>
        <Alert severity="error">{error}</Alert>
      </Paper>
    );
  }

  if (missing.length === 0) {
    return (
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h5" gutterBottom>
          Missing keys
        </Typography>
        <Typography color="text.secondary">
          No missing key files for zones you can read.
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 3, mb: 3 }}>
      <Typography variant="h5" gutterBottom>
        Missing keys
      </Typography>
      <Typography color="text.secondary" paragraph>
        Zone catalog entries reference these key files, but the files are not present on disk.
      </Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Scoped key path</TableCell>
            <TableCell align="right">Affected entries</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {missing.map((entry) => (
            <TableRow key={entry.scopedKeyPath}>
              <TableCell>
                <code>{entry.scopedKeyPath}</code>
              </TableCell>
              <TableCell align="right">{entry.affectedEntryCount}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
}
