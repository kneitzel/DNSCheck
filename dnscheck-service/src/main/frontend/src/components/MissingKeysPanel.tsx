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
import { KeysService, type MissingKeyEntry } from "../generated";

export function MissingKeysPanel() {
  const [missing, setMissing] = useState<MissingKeyEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const response = await KeysService.listMissingKeys();
        setMissing(response.missing);
        setError(null);
      } catch {
        setError("Could not load missing key summary.");
        setMissing([]);
      }
    })();
  }, []);

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
    return null;
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
