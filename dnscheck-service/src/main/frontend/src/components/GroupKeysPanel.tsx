import { useEffect, useRef, useState } from "react";
import {
  Alert,
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
import { AdminService, type AccountKeyDetail, type AccountKeySummary } from "../generated";
import { ApiError } from "../generated/core/ApiError";

const emptyKeyContent = `key "example.com" {
  algorithm hmac-sha256;
  secret "replace-me";
};`;

type GroupKeysPanelProps = {
  groupId: number;
  canManage: boolean;
};

export function GroupKeysPanel({ groupId, canManage }: GroupKeysPanelProps) {
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
      const response = await AdminService.listGroupKeys(groupId);
      setKeys(response.keys);
    } catch {
      setError("Could not load group keys.");
      setKeys([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadKeys();
  }, [groupId]);

  const uploadKey = async () => {
    setError(null);
    setLoading(true);
    try {
      await AdminService.uploadGroupKey(groupId, {
        keyName: uploadName,
        content: uploadContent,
      });
      setUploadOpen(false);
      setUploadName("");
      setUploadContent(emptyKeyContent);
      await loadKeys();
    } catch {
      setError("Could not upload group key.");
    } finally {
      setLoading(false);
    }
  };

  const openEdit = async (keyName: string) => {
    setError(null);
    setLoading(true);
    try {
      const detail = await AdminService.getGroupKey(groupId, keyName);
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
    setLoading(true);
    try {
      await AdminService.updateGroupKey(groupId, editKey.keyName, { content: editContent });
      setEditKey(null);
      await loadKeys();
    } catch {
      setError("Could not save key.");
    } finally {
      setLoading(false);
    }
  };

  const downloadKey = async (keyName: string) => {
    try {
      const content = await AdminService.downloadGroupKey(groupId, keyName);
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
    setLoading(true);
    try {
      await AdminService.deleteGroupKey(groupId, keyName);
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

  const handleReplaceFile = async (file: File) => {
    if (!replaceTarget) {
      return;
    }
    setLoading(true);
    try {
      const content = await file.text();
      await AdminService.replaceGroupKey(groupId, replaceTarget, { content });
      setReplaceTarget(null);
      await loadKeys();
    } catch {
      setError("Could not replace key.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Paper sx={{ p: 3, mb: 3 }}>
      <Typography variant="h6" gutterBottom>
        Group keys
      </Typography>
      {!canManage && (
        <Typography variant="body2" sx={{ mb: 2 }}>
          Read-only list of shared TSIG keys for this group.
        </Typography>
      )}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {canManage && (
        <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
          <Button variant="contained" disabled={loading} onClick={() => setUploadOpen(true)}>
            Upload key
          </Button>
          <Button variant="outlined" disabled={loading} onClick={() => void loadKeys()}>
            Refresh
          </Button>
        </Stack>
      )}
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Filename</TableCell>
            <TableCell>Scoped path</TableCell>
            {canManage && <TableCell align="right">Actions</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {keys.map((key) => (
            <TableRow key={key.keyName}>
              <TableCell>{key.keyName}</TableCell>
              <TableCell>{key.scopedPath}</TableCell>
              {canManage && (
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
              )}
            </TableRow>
          ))}
          {keys.length === 0 && (
            <TableRow>
              <TableCell colSpan={canManage ? 3 : 2}>No group keys yet.</TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      {canManage && (
        <>
          <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} fullWidth maxWidth="md">
            <DialogTitle>Upload group key</DialogTitle>
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
                        void file.text().then((text) => {
                          setUploadContent(text);
                          if (!uploadName) {
                            setUploadName(file.name);
                          }
                        });
                      }
                    }}
                  />
                </Button>
                <TextField
                  label="Filename"
                  size="small"
                  value={uploadName}
                  onChange={(event) => setUploadName(event.target.value)}
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
              <Button
                variant="contained"
                disabled={loading || !uploadName || !uploadContent}
                onClick={() => void uploadKey()}
              >
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
        </>
      )}
    </Paper>
  );
}
