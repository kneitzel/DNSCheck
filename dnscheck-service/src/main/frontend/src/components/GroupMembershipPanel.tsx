import {
  Box,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Typography,
} from "@mui/material";
import type { GroupSummary, OperatorSummary } from "../generated";

type GroupMembershipPanelProps = {
  memberGroupNames: string[];
  allGroups: GroupSummary[];
  loading: boolean;
  onAdd: (groupId: number) => void;
  onRemove: (groupId: number) => void;
};

export function GroupMembershipPanel({
  memberGroupNames,
  allGroups,
  loading,
  onAdd,
  onRemove,
}: GroupMembershipPanelProps) {
  const memberGroups = allGroups.filter((group) => memberGroupNames.includes(group.name));
  const availableGroups = allGroups.filter((group) => !memberGroupNames.includes(group.name));

  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Typography variant="h6" gutterBottom>
        Group membership
      </Typography>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mb: 2 }}>
        {memberGroups.length === 0 ? (
          <Typography color="text.secondary">Not a member of any group.</Typography>
        ) : (
          memberGroups.map((group) => (
            <Chip
              key={group.id}
              label={group.name}
              onDelete={() => onRemove(group.id)}
              disabled={loading}
            />
          ))
        )}
      </Box>
      {availableGroups.length > 0 && (
        <FormControl size="small" sx={{ minWidth: 220 }}>
          <InputLabel id="add-group-label">Add to group</InputLabel>
          <Select
            labelId="add-group-label"
            label="Add to group"
            value=""
            disabled={loading}
            onChange={(event) => {
              const groupId = Number(event.target.value);
              if (groupId) {
                onAdd(groupId);
              }
            }}
          >
            {availableGroups.map((group) => (
              <MenuItem key={group.id} value={group.id}>
                {group.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      )}
    </Paper>
  );
}

type OperatorMembershipPanelProps = {
  memberUsernames: string[];
  allOperators: OperatorSummary[];
  loading: boolean;
  onAdd: (operatorId: number) => void;
  onRemove: (operatorId: number) => void;
};

export function OperatorMembershipPanel({
  memberUsernames,
  allOperators,
  loading,
  onAdd,
  onRemove,
}: OperatorMembershipPanelProps) {
  const members = allOperators.filter((operator) => memberUsernames.includes(operator.username));
  const available = allOperators.filter((operator) => !memberUsernames.includes(operator.username));

  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Typography variant="h6" gutterBottom>
        Members
      </Typography>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mb: 2 }}>
        {members.length === 0 ? (
          <Typography color="text.secondary">No members.</Typography>
        ) : (
          members.map((operator) => (
            <Chip
              key={operator.id}
              label={operator.username}
              onDelete={() => onRemove(operator.id)}
              disabled={loading}
            />
          ))
        )}
      </Box>
      {available.length > 0 && (
        <FormControl size="small" sx={{ minWidth: 220 }}>
          <InputLabel id="add-operator-label">Add operator</InputLabel>
          <Select
            labelId="add-operator-label"
            label="Add operator"
            value=""
            disabled={loading}
            onChange={(event) => {
              const operatorId = Number(event.target.value);
              if (operatorId) {
                onAdd(operatorId);
              }
            }}
          >
            {available.map((operator) => (
              <MenuItem key={operator.id} value={operator.id}>
                {operator.username}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      )}
    </Paper>
  );
}
