import {
  AppBar,
  Box,
  Button,
  Container,
  Paper,
  TextField,
  Toolbar,
  Typography,
} from "@mui/material";

type LoginPanelProps = {
  username: string;
  password: string;
  authError: string | null;
  loading: boolean;
  onUsernameChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onLogin: () => void;
};

export function LoginPanel({
  username,
  password,
  authError,
  loading,
  onUsernameChange,
  onPasswordChange,
  onLogin,
}: LoginPanelProps) {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div">
            DNSCheck
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ mt: 8 }}>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h5" gutterBottom>
            Sign in
          </Typography>
          <TextField
            label="Username"
            fullWidth
            margin="normal"
            value={username}
            onChange={(event) => onUsernameChange(event.target.value)}
          />
          <TextField
            label="Password"
            type="password"
            fullWidth
            margin="normal"
            value={password}
            onChange={(event) => onPasswordChange(event.target.value)}
          />
          {authError && (
            <Typography color="error" variant="body2" sx={{ mt: 1 }}>
              {authError}
            </Typography>
          )}
          <Button variant="contained" onClick={onLogin} disabled={loading} sx={{ mt: 2 }}>
            {loading ? "Signing in..." : "Sign in"}
          </Button>
        </Paper>
      </Container>
    </Box>
  );
}
