export type OwnerFolderKindDisplay = "user" | "group" | "unknown";

export type OwnerFolderDisplay = {
  kind: OwnerFolderKindDisplay;
  identifier: string;
  tooltip: string;
};

export type ScopedPathDisplay = {
  owner: OwnerFolderDisplay;
  basename: string;
  tooltip: string;
};

const OWNER_FOLDER_PATTERN = /^([a-zA-Z0-9._-]+)\.(u|g)$/;

export function parseOwnerFolderDisplay(ownerFolder: string | null | undefined): OwnerFolderDisplay {
  if (ownerFolder == null) {
    return { kind: "unknown", identifier: "", tooltip: "" };
  }
  const trimmed = ownerFolder.trim();
  if (trimmed.length === 0) {
    return { kind: "unknown", identifier: "", tooltip: "" };
  }
  const match = OWNER_FOLDER_PATTERN.exec(trimmed);
  if (!match) {
    return { kind: "unknown", identifier: trimmed, tooltip: trimmed };
  }
  return {
    kind: match[2] === "u" ? "user" : "group",
    identifier: match[1],
    tooltip: trimmed,
  };
}

export function parseScopedPathDisplay(scopedPath: string | null | undefined): ScopedPathDisplay {
  if (scopedPath == null) {
    return { owner: parseOwnerFolderDisplay(""), basename: "", tooltip: "" };
  }
  const trimmed = scopedPath.trim();
  if (trimmed.length === 0) {
    return { owner: parseOwnerFolderDisplay(""), basename: "", tooltip: "" };
  }
  const slash = trimmed.indexOf("/");
  if (slash <= 0) {
    return {
      owner: parseOwnerFolderDisplay(trimmed),
      basename: "",
      tooltip: trimmed,
    };
  }
  const ownerPart = trimmed.substring(0, slash);
  const basename = trimmed.substring(slash + 1);
  return {
    owner: parseOwnerFolderDisplay(ownerPart),
    basename,
    tooltip: trimmed,
  };
}
