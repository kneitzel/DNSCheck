import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Link,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import type { ZoneCatalogEntry } from "../generated";
import { catalogRef } from "../zoneCatalog";
import { OwnerFolderLabel } from "./OwnerFolderLabel";
import { ZoneKeyPicker } from "./ZoneKeyPicker";

type ZoneCatalogPanelProps = {
  zones: ZoneCatalogEntry[];
  loading: boolean;
  canManageCatalog: boolean;
  isAdmin: boolean;
  personalOwnerFolder: string;
  canTransferZone: (zone: ZoneCatalogEntry) => boolean;
  createZoneOpen: boolean;
  editZoneOpen: boolean;
  deleteZoneOpen: boolean;
  transferZoneOpen: boolean;
  editingZone: ZoneCatalogEntry | null;
  deletingZone: ZoneCatalogEntry | null;
  transferringZone: ZoneCatalogEntry | null;
  newZoneOwner: string;
  newZoneName: string;
  newZoneNameserver: string;
  newZoneKeyFile: string;
  editZoneNameserver: string;
  editZoneKeyFile: string;
  transferTargetOwner: string;
  createZoneError: string | null;
  editZoneError: string | null;
  deleteZoneError: string | null;
  transferZoneError: string | null;
  onOpenCreateZone: () => void;
  onCloseCreateZone: () => void;
  onOpenEditZone: (zone: ZoneCatalogEntry) => void;
  onCloseEditZone: () => void;
  onOpenDeleteZone: (zone: ZoneCatalogEntry) => void;
  onCloseDeleteZone: () => void;
  onOpenTransferZone: (zone: ZoneCatalogEntry) => void;
  onCloseTransferZone: () => void;
  onNewZoneOwnerChange: (value: string) => void;
  onNewZoneNameChange: (value: string) => void;
  onNewZoneNameserverChange: (value: string) => void;
  onNewZoneKeyFileChange: (value: string) => void;
  onEditZoneNameserverChange: (value: string) => void;
  onEditZoneKeyFileChange: (value: string) => void;
  onTransferTargetOwnerChange: (value: string) => void;
  onCreateZone: () => void;
  onUpdateZone: () => void;
  onDeleteZone: () => void;
  onTransferZone: () => void;
  onSelectZone: (zone: ZoneCatalogEntry) => void;
};

export function ZoneCatalogPanel({
  zones,
  loading,
  canManageCatalog,
  isAdmin,
  personalOwnerFolder,
  canTransferZone,
  createZoneOpen,
  editZoneOpen,
  deleteZoneOpen,
  transferZoneOpen,
  editingZone,
  deletingZone,
  transferringZone,
  newZoneOwner,
  newZoneName,
  newZoneNameserver,
  newZoneKeyFile,
  editZoneNameserver,
  editZoneKeyFile,
  transferTargetOwner,
  createZoneError,
  editZoneError,
  deleteZoneError,
  transferZoneError,
  onOpenCreateZone,
  onCloseCreateZone,
  onOpenEditZone,
  onCloseEditZone,
  onOpenDeleteZone,
  onCloseDeleteZone,
  onOpenTransferZone,
  onCloseTransferZone,
  onNewZoneOwnerChange,
  onNewZoneNameChange,
  onNewZoneNameserverChange,
  onNewZoneKeyFileChange,
  onEditZoneNameserverChange,
  onEditZoneKeyFileChange,
  onTransferTargetOwnerChange,
  onCreateZone,
  onUpdateZone,
  onDeleteZone,
  onTransferZone,
  onSelectZone,
}: ZoneCatalogPanelProps) {
  const showActions = canManageCatalog || zones.some((zone) => canTransferZone(zone));
  return (
    <>
      <Paper sx={{ p: 3, mb: 3 }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
          <Typography variant="h5">Zone catalog</Typography>
          {canManageCatalog && (
            <Button variant="contained" onClick={onOpenCreateZone} disabled={loading}>
              Create zone
            </Button>
          )}
        </Box>
        <Typography color="text.secondary" paragraph>
          Zone definitions loaded from scoped owner folders on disk, including validation errors.
        </Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Owner</TableCell>
              <TableCell>Zone</TableCell>
              <TableCell>File</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Nameserver</TableCell>
              <TableCell>Error</TableCell>
              {showActions && <TableCell align="right">Actions</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {zones.map((zone) => (
              <TableRow key={`${zone.ownerFolder}-${zone.zone}-${zone.filename}`}>
                <TableCell>
                  <OwnerFolderLabel ownerFolder={zone.ownerFolder} />
                </TableCell>
                <TableCell>
                  {zone.valid ? (
                    <Link component="button" variant="body2" onClick={() => onSelectZone(zone)}>
                      {zone.zone}
                    </Link>
                  ) : (
                    zone.zone
                  )}
                </TableCell>
                <TableCell>{zone.filename}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={zone.valid ? "success" : "error"}
                    label={zone.valid ? "Valid" : "Invalid"}
                  />
                </TableCell>
                <TableCell>{zone.nameserver ?? "—"}</TableCell>
                <TableCell>{zone.error ?? "—"}</TableCell>
                {showActions && (
                  <TableCell align="right">
                    {canManageCatalog && (
                      <>
                        <Button size="small" onClick={() => onOpenEditZone(zone)} disabled={loading || !zone.valid}>
                          Edit
                        </Button>
                        <Button
                          size="small"
                          color="error"
                          onClick={() => onOpenDeleteZone(zone)}
                          disabled={loading}
                          sx={{ ml: 1 }}
                        >
                          Delete
                        </Button>
                      </>
                    )}
                    {canTransferZone(zone) && (
                      <Button
                        size="small"
                        onClick={() => onOpenTransferZone(zone)}
                        disabled={loading || !zone.valid}
                        sx={{ ml: 1 }}
                      >
                        Transfer
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      <Dialog open={createZoneOpen} onClose={onCloseCreateZone} fullWidth maxWidth="sm">
        <DialogTitle>Create zone definition</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Choose the owner folder and zone name. The JSON file is stored as{" "}
            <code>{newZoneOwner || "owner.u"}/{newZoneName || "example.com"}.json</code>.
          </Typography>
          <TextField
            label="Owner folder"
            fullWidth
            margin="normal"
            value={newZoneOwner}
            onChange={(event) => onNewZoneOwnerChange(event.target.value)}
            placeholder="admin.u"
          />
          <TextField
            label="Zone name"
            fullWidth
            margin="normal"
            value={newZoneName}
            onChange={(event) => onNewZoneNameChange(event.target.value)}
            placeholder="example.com"
          />
          <TextField
            label="Nameserver"
            fullWidth
            margin="normal"
            value={newZoneNameserver}
            onChange={(event) => onNewZoneNameserverChange(event.target.value)}
            placeholder="ns1.example.com"
          />
          <ZoneKeyPicker
            value={newZoneKeyFile}
            onChange={onNewZoneKeyFileChange}
            isAdmin={isAdmin}
            disabled={loading}
            personalOwnerFolder={personalOwnerFolder}
          />
          {createZoneError && (
            <Typography color="error" variant="body2" sx={{ mt: 1 }}>
              {createZoneError}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onCloseCreateZone}>Cancel</Button>
          <Button
            variant="contained"
            onClick={onCreateZone}
            disabled={loading || !newZoneOwner || !newZoneName || !newZoneNameserver || !newZoneKeyFile}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editZoneOpen} onClose={onCloseEditZone} fullWidth maxWidth="sm">
        <DialogTitle>Edit zone definition</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Update nameserver and key file for <strong>{editingZone ? catalogRef(editingZone) : ""}</strong>. The zone
            name and owner cannot be changed here.
          </Typography>
          <TextField
            label="Nameserver"
            fullWidth
            margin="normal"
            value={editZoneNameserver}
            onChange={(event) => onEditZoneNameserverChange(event.target.value)}
          />
          <ZoneKeyPicker
            value={editZoneKeyFile}
            onChange={onEditZoneKeyFileChange}
            isAdmin={isAdmin}
            disabled={loading}
            personalOwnerFolder={personalOwnerFolder}
          />
          {editZoneError && (
            <Typography color="error" variant="body2" sx={{ mt: 1 }}>
              {editZoneError}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onCloseEditZone}>Cancel</Button>
          <Button
            variant="contained"
            onClick={onUpdateZone}
            disabled={loading || !editZoneNameserver || !editZoneKeyFile}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteZoneOpen} onClose={onCloseDeleteZone} fullWidth maxWidth="sm">
        <DialogTitle>Delete zone definition</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Remove <strong>{deletingZone ? catalogRef(deletingZone) : ""}</strong> from the catalog? This deletes only
            the JSON definition file, not live DNS records.
          </DialogContentText>
          {deleteZoneError && (
            <Typography color="error" variant="body2" sx={{ mt: 2 }}>
              {deleteZoneError}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onCloseDeleteZone}>Cancel</Button>
          <Button variant="contained" color="error" onClick={onDeleteZone} disabled={loading}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={transferZoneOpen} onClose={onCloseTransferZone} fullWidth maxWidth="sm">
        <DialogTitle>Transfer zone ownership</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Move <strong>{transferringZone ? catalogRef(transferringZone) : ""}</strong> to another owner folder.
            Supplemental grants follow the new owner.
          </Typography>
          <TextField
            label="Target owner folder"
            fullWidth
            margin="normal"
            value={transferTargetOwner}
            onChange={(event) => onTransferTargetOwnerChange(event.target.value)}
            placeholder="dns-team.g"
          />
          {transferZoneError && (
            <Typography color="error" variant="body2" sx={{ mt: 1 }}>
              {transferZoneError}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onCloseTransferZone}>Cancel</Button>
          <Button
            variant="contained"
            onClick={onTransferZone}
            disabled={loading || !transferTargetOwner}
          >
            Transfer
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
