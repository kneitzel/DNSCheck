import { useEffect, useState } from "react";
import { Alert, Box, Button, Paper, TextField, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { AuthService } from "../generated";
import { RightsSummaryPanel } from "../components/RightsSummaryPanel";

export function AccountPage() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [rights, setRights] = useState<Awaited<ReturnType<typeof AuthService.getMyRights>> | null>(
    null,
  );

  const loadRights = async () => {
    const response = await AuthService.getMyRights();
    setRights(response);
  };

  useEffect(() => {
    void loadRights();
  }, []);

  const changePassword = async () => {
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      await AuthService.changePassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setSuccess("Password updated.");
    } catch {
      setError("Could not change password. Check your current password.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Account
      </Typography>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Change password
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        {success && (
          <Alert severity="success" sx={{ mb: 2 }}>
            {success}
          </Alert>
        )}
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2, alignItems: "center" }}>
          <TextField
            label="Current password"
            type="password"
            size="small"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
          <TextField
            label="New password"
            type="password"
            size="small"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <Button
            variant="contained"
            disabled={loading || !currentPassword || !newPassword}
            onClick={() => void changePassword()}
          >
            Update password
          </Button>
        </Box>
      </Paper>

      {rights && (
        <RightsSummaryPanel title="Your effective rights" rights={rights} loading={loading} readOnly />
      )}

      <Paper sx={{ p: 3, mt: 3 }}>
        <Typography variant="h6" gutterBottom>
          Personal keys
        </Typography>
        <Typography variant="body2" sx={{ mb: 2 }}>
          Manage TSIG key files in your personal owner folder.
        </Typography>
        <Button variant="outlined" component={RouterLink} to="/account/keys">
          Manage keys
        </Button>
      </Paper>
    </Box>
  );
}
