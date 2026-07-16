import { AppBar, Box, Button, Container, Toolbar, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import type { ReactNode } from "react";

type AppShellProps = {
  loggedInUser: string;
  isAdmin: boolean;
  loading: boolean;
  onRefresh: () => void;
  onLogout: () => void;
  children: ReactNode;
};

export function AppShell({
  loggedInUser,
  isAdmin,
  loading,
  onRefresh,
  onLogout,
  children,
}: AppShellProps) {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            DNSCheck
          </Typography>
          <Button color="inherit" component={RouterLink} to="/">
            Dashboard
          </Button>
          <Button color="inherit" component={RouterLink} to="/account">
            Account
          </Button>
          {isAdmin && (
            <>
              <Button color="inherit" component={RouterLink} to="/admin/operators">
                Operators
              </Button>
              <Button color="inherit" component={RouterLink} to="/admin/groups">
                Groups
              </Button>
            </>
          )}
          <Typography variant="body2" sx={{ mx: 2 }}>
            {loggedInUser}
          </Typography>
          <Button color="inherit" onClick={onRefresh} disabled={loading}>
            Refresh
          </Button>
          <Button color="inherit" onClick={onLogout} disabled={loading}>
            Log out
          </Button>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ mt: 4 }}>
        {children}
      </Container>
    </Box>
  );
}
