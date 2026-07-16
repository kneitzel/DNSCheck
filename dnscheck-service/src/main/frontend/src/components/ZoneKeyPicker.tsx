import { useEffect, useState } from "react";
import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import {
  AccountService,
  AdminService,
  KeysService,
  type GroupSummary,
  type SelectableKey,
} from "../generated";

const emptyKeyContent = `key "example.com" {
  algorithm hmac-sha256;
  secret "replace-me";
};`;

type ZoneKeyPickerProps = {
  value: string;
  onChange: (scopedPath: string) => void;
  isAdmin: boolean;
  disabled?: boolean;
  personalOwnerFolder: string;
};

export function ZoneKeyPicker({
  value,
  onChange,
  isAdmin,
  disabled,
  personalOwnerFolder,
}: ZoneKeyPickerProps) {
  const [keys, setKeys] = useState<SelectableKey[]>([]);
  const [groups, setGroups] = useState<GroupSummary[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadName, setUploadName] = useState("");
  const [uploadContent, setUploadContent] = useState(emptyKeyContent);
  const [uploadTarget, setUploadTarget] = useState<"personal" | string>("personal");
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const loadKeys = async () => {
    setLoadError(null);
    try {
      const response = await KeysService.listSelectableKeys();
      setKeys(response.keys);
    } catch {
      setLoadError("Could not load selectable keys.");
      setKeys([]);
    }
  };

  useEffect(() => {
    void loadKeys();
  }, []);

  useEffect(() => {
    if (!isAdmin) {
      setGroups([]);
      return;
    }
    void (async () => {
      try {
        const response = await AdminService.listGroups();
        setGroups(response.groups);
      } catch {
        setGroups([]);
      }
    })();
  }, [isAdmin]);

  const uploadKey = async () => {
    setUploadError(null);
    setUploading(true);
    try {
      let scopedPath: string;
      if (uploadTarget === "personal") {
        const summary = await AccountService.uploadAccountKey({
          keyName: uploadName,
          content: uploadContent,
        });
        scopedPath = summary.scopedPath;
      } else {
        const groupId = Number(uploadTarget);
        const summary = await AdminService.uploadGroupKey(groupId, {
          keyName: uploadName,
          content: uploadContent,
        });
        scopedPath = summary.scopedPath;
      }
      await loadKeys();
      onChange(scopedPath);
      setUploadOpen(false);
      setUploadName("");
      setUploadContent(emptyKeyContent);
      setUploadTarget("personal");
    } catch {
      setUploadError("Could not upload key. Check filename and BIND content.");
    } finally {
      setUploading(false);
    }
  };

  return (
    <Box sx={{ mt: 1 }}>
      <FormControl fullWidth margin="normal" disabled={disabled}>
        <InputLabel id="zone-key-picker-label">Key file</InputLabel>
        <Select
          labelId="zone-key-picker-label"
          label="Key file"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          displayEmpty
        >
          <MenuItem value="">
            <em>Select a key</em>
          </MenuItem>
          {keys.map((key) => (
            <MenuItem key={key.scopedPath} value={key.scopedPath}>
              {key.displayLabel}
            </MenuItem>
          ))}
          {value && !keys.some((key) => key.scopedPath === value) && (
            <MenuItem value={value}>{value}</MenuItem>
          )}
        </Select>
      </FormControl>
      {value && (
        <Typography variant="caption" color="text.secondary" display="block">
          Scoped path: <code>{value}</code>
        </Typography>
      )}
      {loadError && (
        <Typography color="error" variant="body2" sx={{ mt: 1 }}>
          {loadError}
        </Typography>
      )}
      {!uploadOpen ? (
        <Button size="small" sx={{ mt: 1 }} onClick={() => setUploadOpen(true)} disabled={disabled}>
          Upload new key
        </Button>
      ) : (
        <Stack spacing={1} sx={{ mt: 2 }}>
          <Typography variant="subtitle2">Upload key</Typography>
          {isAdmin && (
            <FormControl fullWidth size="small">
              <InputLabel id="upload-target-label">Upload target</InputLabel>
              <Select
                labelId="upload-target-label"
                label="Upload target"
                value={uploadTarget}
                onChange={(event) => setUploadTarget(event.target.value)}
              >
                <MenuItem value="personal">Personal ({personalOwnerFolder})</MenuItem>
                {groups.map((group) => (
                  <MenuItem key={group.id} value={String(group.id)}>
                    Group {group.name}.g
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
          {!isAdmin && (
            <Typography variant="body2" color="text.secondary">
              Uploads go to your personal folder ({personalOwnerFolder}).
            </Typography>
          )}
          <TextField
            label="Key filename"
            size="small"
            value={uploadName}
            onChange={(event) => setUploadName(event.target.value)}
            placeholder="example.com.key"
          />
          <TextField
            label="BIND key content"
            size="small"
            multiline
            minRows={4}
            value={uploadContent}
            onChange={(event) => setUploadContent(event.target.value)}
            inputProps={{ style: { fontFamily: "monospace", fontSize: 12 } }}
          />
          {uploadError && (
            <Typography color="error" variant="body2">
              {uploadError}
            </Typography>
          )}
          <Stack direction="row" spacing={1}>
            <Button
              variant="contained"
              size="small"
              onClick={() => void uploadKey()}
              disabled={uploading || !uploadName || !uploadContent}
            >
              Upload and select
            </Button>
            <Button size="small" onClick={() => setUploadOpen(false)} disabled={uploading}>
              Cancel
            </Button>
          </Stack>
        </Stack>
      )}
    </Box>
  );
}
