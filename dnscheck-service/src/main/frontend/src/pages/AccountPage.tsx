import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { QRCodeSVG } from "qrcode.react";
import { AccountService, AuthService, type AccountSecurityResponse } from "../generated";
import { RightsSummaryPanel } from "../components/RightsSummaryPanel";

export function AccountPage() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [mfaCode, setMfaCode] = useState("");
  const [mfaMessage, setMfaMessage] = useState<string | null>(null);
  const [mfaRecoveryCodes, setMfaRecoveryCodes] = useState<string[]>([]);
  const [security, setSecurity] = useState<AccountSecurityResponse | null>(null);
  const [cancelConfirmOpen, setCancelConfirmOpen] = useState(false);
  const [rights, setRights] = useState<Awaited<ReturnType<typeof AuthService.getMyRights>> | null>(
    null,
  );

  const loadSecurity = async () => {
    const response = await AccountService.getAccountSecurity();
    setSecurity(response);
    if (response.mfaStatus !== "pending") {
      setMfaRecoveryCodes([]);
    }
  };

  const loadRights = async () => {
    const response = await AuthService.getMyRights();
    setRights(response);
  };

  useEffect(() => {
    void loadRights();
    void loadSecurity();
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

  const startMfaEnrollment = async () => {
    setError(null);
    setSuccess(null);
    setMfaMessage(null);
    setLoading(true);
    try {
      const response = await AccountService.startAccountMfaEnrollment();
      setSecurity(response);
      setMfaRecoveryCodes(response.recoveryCodes ?? []);
      setMfaCode("");
      setMfaMessage("MFA enrollment started. Scan the QR code or enter the setup key, then confirm the code.");
    } catch {
      setError("Could not start MFA enrollment.");
    } finally {
      setLoading(false);
    }
  };

  const confirmMfaEnrollment = async () => {
    setError(null);
    setSuccess(null);
    setMfaMessage(null);
    setLoading(true);
    try {
      const response = await AccountService.confirmAccountMfaEnrollment({ code: mfaCode });
      setSecurity(response);
      setMfaCode("");
      setMfaRecoveryCodes([]);
      setMfaMessage("MFA enrollment confirmed.");
    } catch {
      setError("Could not confirm MFA enrollment. Check the verification code.");
    } finally {
      setLoading(false);
    }
  };

  const cancelMfaEnrollment = async () => {
    setError(null);
    setSuccess(null);
    setMfaMessage(null);
    setLoading(true);
    try {
      const response = await AccountService.cancelAccountMfaEnrollment();
      setSecurity(response);
      setMfaCode("");
      setMfaRecoveryCodes([]);
      setCancelConfirmOpen(false);
      setMfaMessage("MFA enrollment cancelled.");
    } catch {
      setError("Could not cancel MFA enrollment.");
    } finally {
      setLoading(false);
    }
  };

  let statusLabel = "Disabled";
  if (security?.mfaStatus === "active") {
    statusLabel = "Active";
  } else if (security?.mfaStatus === "pending") {
    statusLabel = "Pending";
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Account
      </Typography>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Account security
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mb: 2 }} alignItems="center">
          <Typography variant="body2">MFA status:</Typography>
          <Chip label={statusLabel} color={security?.mfaStatus === "active" ? "success" : "default"} />
        </Stack>
        {security?.mfaStatus === "pending" && security.setupKey && (
          <Stack spacing={2} sx={{ mb: 2 }}>
            {security.otpauthUri && (
              <Box>
                <Typography variant="body2" sx={{ mb: 1 }}>
                  Scan this QR code with your authenticator app:
                </Typography>
                <Box
                  sx={{
                    display: "inline-flex",
                    p: 1.5,
                    bgcolor: "common.white",
                    border: 1,
                    borderColor: "divider",
                  }}
                  aria-label="MFA enrollment QR code"
                >
                  <QRCodeSVG value={security.otpauthUri} size={180} marginSize={1} />
                </Box>
              </Box>
            )}
            <Box>
              <Typography variant="body2" sx={{ mb: 1 }}>
                Or enter this setup key manually:
              </Typography>
              <Paper variant="outlined" sx={{ p: 2, fontFamily: "monospace", overflowX: "auto" }}>
                {security.setupKey}
              </Paper>
            </Box>
          </Stack>
        )}
        {mfaRecoveryCodes.length > 0 && (
          <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Typography variant="subtitle2" gutterBottom>
              Recovery codes
            </Typography>
            <Typography variant="body2" sx={{ mb: 1 }}>
              Save these now. They are shown once and can be used instead of your authenticator app.
            </Typography>
            <Stack spacing={0.5}>
              {mfaRecoveryCodes.map((code) => (
                <Typography key={code} variant="body2" sx={{ fontFamily: "monospace" }}>
                  {code}
                </Typography>
              ))}
            </Stack>
          </Paper>
        )}
        {mfaMessage && (
          <Alert severity="success" sx={{ mb: 2 }}>
            {mfaMessage}
          </Alert>
        )}
        <Stack direction="row" spacing={2} sx={{ flexWrap: "wrap", alignItems: "center" }}>
          <Button
            variant="outlined"
            disabled={loading || security?.mfaStatus === "pending" || security?.mfaStatus === "active"}
            onClick={() => void startMfaEnrollment()}
          >
            Start MFA enrollment
          </Button>
          <TextField
            label="Verification code"
            size="small"
            value={mfaCode}
            onChange={(event) => setMfaCode(event.target.value)}
            disabled={loading || security?.mfaStatus !== "pending"}
          />
          <Button
            variant="contained"
            disabled={loading || security?.mfaStatus !== "pending" || !mfaCode}
            onClick={() => void confirmMfaEnrollment()}
          >
            Confirm enrollment
          </Button>
          <Button
            variant="outlined"
            color="warning"
            disabled={loading || security?.mfaStatus !== "pending"}
            onClick={() => setCancelConfirmOpen(true)}
          >
            Cancel enrollment
          </Button>
        </Stack>
      </Paper>

      <Dialog open={cancelConfirmOpen} onClose={() => setCancelConfirmOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Cancel MFA enrollment?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            This abandons setup and invalidates the recovery codes shown. Continue?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelConfirmOpen(false)} disabled={loading}>
            Keep enrollment
          </Button>
          <Button color="warning" variant="contained" disabled={loading} onClick={() => void cancelMfaEnrollment()}>
            Cancel enrollment
          </Button>
        </DialogActions>
      </Dialog>

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
