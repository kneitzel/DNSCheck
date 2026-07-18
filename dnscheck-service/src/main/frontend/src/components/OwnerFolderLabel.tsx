import { Stack, Typography } from "@mui/material";
import GroupIcon from "@mui/icons-material/Group";
import PersonIcon from "@mui/icons-material/Person";
import type { SvgIconComponent } from "@mui/icons-material";
import {
  parseOwnerFolderDisplay,
  parseScopedPathDisplay,
  type OwnerFolderDisplay,
  type OwnerFolderKindDisplay,
} from "../ownerFolderDisplay";

const KIND_STYLE: Record<
  Exclude<OwnerFolderKindDisplay, "unknown">,
  { Icon: SvgIconComponent; color: string; label: string }
> = {
  user: { Icon: PersonIcon, color: "primary.main", label: "User owner" },
  group: { Icon: GroupIcon, color: "secondary.main", label: "Group owner" },
};

type OwnerFolderLabelProps = {
  ownerFolder: string;
};

function OwnerKindMark({ display }: { display: OwnerFolderDisplay }) {
  if (display.kind === "unknown") {
    return (
      <Typography component="span" variant="body2">
        {display.identifier || "—"}
      </Typography>
    );
  }
  const { Icon, color, label } = KIND_STYLE[display.kind];
  return (
    <>
      <Icon fontSize="small" sx={{ color }} aria-label={label} />
      <Typography component="span" variant="body2">
        {display.identifier}
      </Typography>
    </>
  );
}

export function OwnerFolderLabel({ ownerFolder }: OwnerFolderLabelProps) {
  const display = parseOwnerFolderDisplay(ownerFolder);
  return (
    <Stack
      component="span"
      direction="row"
      spacing={0.75}
      alignItems="center"
      title={display.tooltip}
      sx={{ display: "inline-flex" }}
    >
      <OwnerKindMark display={display} />
    </Stack>
  );
}

type ScopedPathLabelProps = {
  scopedPath: string;
};

export function ScopedPathLabel({ scopedPath }: ScopedPathLabelProps) {
  const display = parseScopedPathDisplay(scopedPath);
  return (
    <Stack
      component="span"
      direction="row"
      spacing={0.75}
      alignItems="center"
      title={display.tooltip}
      sx={{ display: "inline-flex", flexWrap: "wrap" }}
    >
      <OwnerKindMark display={display.owner} />
      {display.basename ? (
        <Typography component="span" variant="body2" sx={{ fontFamily: "monospace" }}>
          / {display.basename}
        </Typography>
      ) : null}
    </Stack>
  );
}
