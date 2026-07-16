import { useEffect, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import {
  AccountService,
  type AccountKeyDetail,
  type AccountKeySummary,
} from "../generated";
import { ApiError } from "../generated/core/ApiError";

const emptyKeyContent = `key "example.com" {
  algorithm hmac-sha256;
  secret "replace-me";
};`;

export function AccountKeysPage() {
  const [keys, setKeys] = useState<AccountKeySummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadName, setUploadName] = useState("");
  const [uploadContent, setUploadContent] = useState(emptyKeyContent);
  const [editKey, setEditKey] = useState<AccountKeyDetail | null>(null);
  const [editContent, setEditContent] = useState("");
  const replaceInputRef = useRef<HTMLInputElement | null>(null);
  const [replaceTarget, setReplaceTarget] = useState<string | null>(null);

  const loadKeys = async () => {
    setError(null);
    setLoading(true);
    try {
      const response = await AccountService.listAccountKeys();
      setKeys(response.keys);
    } catch {
      setError("Could not load personal keys.");
      setKeys([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadKeys();
  }, []);

  const uploadKey = async () => {
    setError(null);
    setLoading(true);
    try {
      await AccountService.uploadAccountKey({
        keyName: uploadName,
        content: uploadContent,
      });
      setUploadOpen(false);
      setUploadName("");
      setUploadContent(emptyKeyContent);
      await loadKeys();
    } catch {
      setError("Could not upload key. Check the filename and BIND content.");
    } finally {
      setLoading(false);
    }
  };

  const openEdit = async (keyName: string) => {
    setError(null);
    setLoading(true);
    try {
      const detail = await AccountService.getAccountKey(keyName);
      setEditKey(detail);
      setEditContent(detail.content);
    } catch {
      setError("Could not load key for editing.");
    } finally {
      setLoading(false);
    }
  };

  const saveEdit = async () => {
    if (!editKey) {
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await AccountService.updateAccountKey(editKey.keyName, { content: editContent });
      setEditKey(null);
      await loadKeys();
    } catch {
      setError("Could not save key. Check BIND syntax.");
    } finally {
      setLoading(false);
    }
  };

  const downloadKey = async (keyName: string) => {
    setError(null);
    try {
      const content = await AccountService.downloadAccountKey(keyName);
      const blob = new Blob([content], { type: "text/plain" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = keyName;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch {
      setError("Could not download key.");
    }
  };

  const deleteKey = async (keyName: string) => {
    setError(null);
    setLoading(true);
    try {
      await AccountService.deleteAccountKey(keyName);
      await loadKeys();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        const body = err.body as { referencedByCount?: number; message?: string } | undefined;
        const count = body?.referencedByCount;
        setError(
          count != null
            ? `Cannot delete key: referenced by ${count} zone catalog entr${count === 1 ? "y" : "ies"}.`
            : (body?.message ?? "Cannot delete key: it is still referenced by zone definitions."),
        );
      } else {
        setError("Could not delete key.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleUploadFile = async (file: File) => {
    const text = await file.text();
    setUploadContent(text);
    if (!uploadName) {
      setUploadName(file.name);
    }
  };

  const handleReplaceFile = async (file: File) => {
    if (!replaceTarget) {
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const content = await file.text();
      await AccountService.replaceAccountKey(replaceTarget, { content });
      setReplaceTarget(null);
      await loadKeys();
    } catch {
      setError("Could not replace key.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Personal keys
      </Typography>
      <Typography variant="body2" sx={{ mb: 2 }}>
        Manage TSIG keys in your <code>{`{username}.u/`}</code> folder.{" "}
        <Button component={RouterLink} to="/account" size="small">
          Back to account
        </Button>
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2}>
          <Button variant="contained" disabled={loading} onClick={() => setUploadOpen(true)}>
            Upload key
          </Button>
          <Button variant="outlined" disabled={loading} onClick={() => void loadKeys()}>
            Refresh
          </Button>
        </Stack>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Filename</TableCell>
              <TableCell>Scoped path</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {keys.map((key) => (
              <TableRow key={key.keyName}>
                <TableCell>{key.keyName}</TableCell>
                <TableCell>{key.scopedPath}</TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button size="small" onClick={() => void openEdit(key.keyName)}>
                      Edit
                    </Button>
                    <Button
                      size="small"
                      onClick={() => {
                        setReplaceTarget(key.keyName);
                        replaceInputRef.current?.click();
                      }}
                    >
                      Replace
                    </Button>
                    <Button size="small" onClick={() => void downloadKey(key.keyName)}>
                      Download
                    </Button>
                    <Button size="small" color="error" onClick={() => void deleteKey(key.keyName)}>
                      Delete
                    </Button>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {keys.length === 0 && (
              <TableRow>
                <TableCell colSpan={3}>No personal keys yet.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>

      <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Upload personal key</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Button variant="outlined" component="label">
              Choose file
              <input
                hidden
                type="file"
                accept=".key,text/plain"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) {
                    void handleUploadFile(file);
                  }
                }}
              />
            </Button>
            <TextField
              label="Filename"
              size="small"
              value={uploadName}
              onChange={(event) => setUploadName(event.target.value)}
              helperText="Must end with .key"
            />
            <TextField
              label="BIND key content"
              multiline
              minRows={8}
              value={uploadContent}
              onChange={(event) => setUploadContent(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUploadOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={loading || !uploadName || !uploadContent} onClick={() => void uploadKey()}>
            Upload
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editKey !== null} onClose={() => setEditKey(null)} fullWidth maxWidth="md">
        <DialogTitle>Edit {editKey?.keyName}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            minRows={10}
            value={editContent}
            onChange={(event) => setEditContent(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditKey(null)}>Cancel</Button>
          <Button variant="contained" disabled={loading} onClick={() => void saveEdit()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <input
        ref={replaceInputRef}
        hidden
        type="file"
        accept=".key,text/plain"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) {
            void handleReplaceFile(file);
          }
          event.target.value = "";
        }}
      />
    </Box>
  );
}
