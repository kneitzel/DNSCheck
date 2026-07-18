import { Alert, Box, Button, Paper, Typography } from "@mui/material";
import { ScopedPathLabel } from "./OwnerFolderLabel";

type ZoneViewErrorBannerProps = {
  selectedZone: string;
  error: string;
  loading: boolean;
  onRetry: () => void;
};

export function ZoneViewErrorBanner({
  selectedZone,
  error,
  loading,
  onRetry,
}: ZoneViewErrorBannerProps) {
  return (
    <Paper sx={{ p: 3, mb: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography
          variant="h5"
          component="div"
          sx={{ display: "flex", alignItems: "center", gap: 1, flexWrap: "wrap" }}
        >
          Zone view: <ScopedPathLabel scopedPath={selectedZone} />
        </Typography>
        <Button size="small" onClick={onRetry} disabled={loading}>
          Retry
        </Button>
      </Box>
      <Alert severity="error">{error}</Alert>
    </Paper>
  );
}
