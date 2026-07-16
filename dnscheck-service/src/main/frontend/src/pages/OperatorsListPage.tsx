import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Box,
  Button,
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
import { AdminService, type OperatorSummary } from "../generated";

export function OperatorsListPage() {
  const [operators, setOperators] = useState<OperatorSummary[]>([]);
  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    const response = await AdminService.listOperators();
    setOperators(response.operators);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const createOperator = async () => {
    setLoading(true);
    try {
      await AdminService.createOperator({ username: newUsername, password: newPassword });
      setNewUsername("");
      setNewPassword("");
      await load();
    } finally {
      setLoading(false);
    }
  };

  const toggleEnabled = async (operator: OperatorSummary) => {
    setLoading(true);
    try {
      await AdminService.updateOperator(operator.id, { enabled: !operator.enabled });
      await load();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Operators
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Create operator
        </Typography>
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2 }}>
          <TextField
            label="Username"
            size="small"
            value={newUsername}
            onChange={(event) => setNewUsername(event.target.value)}
          />
          <TextField
            label="Password"
            type="password"
            size="small"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <Button
            variant="contained"
            disabled={loading || !newUsername || !newPassword}
            onClick={() => void createOperator()}
          >
            Create
          </Button>
        </Box>
      </Paper>

      <Table size="small" component={Paper}>
        <TableHead>
          <TableRow>
            <TableCell>Username</TableCell>
            <TableCell>Enabled</TableCell>
            <TableCell>Groups</TableCell>
            <TableCell>Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {operators.map((operator) => (
            <TableRow key={operator.id} hover>
              <TableCell>
                <Link component={RouterLink} to={`/admin/operators/${operator.id}`}>
                  {operator.username}
                </Link>
              </TableCell>
              <TableCell>{operator.enabled ? "Yes" : "No"}</TableCell>
              <TableCell>{operator.groupNames.length}</TableCell>
              <TableCell>
                <Button size="small" disabled={loading} onClick={() => void toggleEnabled(operator)}>
                  {operator.enabled ? "Disable" : "Enable"}
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}
