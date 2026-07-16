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
import { AdminService, type GroupSummary } from "../generated";

export function GroupsListPage() {
  const [groups, setGroups] = useState<GroupSummary[]>([]);
  const [newGroupName, setNewGroupName] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    const response = await AdminService.listGroups();
    setGroups(response.groups);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const createGroup = async () => {
    setLoading(true);
    try {
      await AdminService.createGroup({ name: newGroupName });
      setNewGroupName("");
      await load();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Groups
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Create group
        </Typography>
        <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
          <TextField
            label="Group name"
            size="small"
            value={newGroupName}
            onChange={(event) => setNewGroupName(event.target.value)}
          />
          <Button
            variant="contained"
            disabled={loading || !newGroupName}
            onClick={() => void createGroup()}
          >
            Create
          </Button>
        </Box>
      </Paper>

      <Table size="small" component={Paper}>
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Members</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {groups.map((group) => (
            <TableRow key={group.id} hover>
              <TableCell>
                <Link component={RouterLink} to={`/admin/groups/${group.id}`}>
                  {group.name}
                </Link>
              </TableCell>
              <TableCell>{group.memberUsernames.length}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}
