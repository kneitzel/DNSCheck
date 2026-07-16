import type { ZoneCatalogEntry } from "./generated";

export type SelectedZoneRef = {
  owner: string;
  zoneName: string;
};

export function catalogRef(entry: Pick<ZoneCatalogEntry, "ownerFolder" | "zone">): string {
  return `${entry.ownerFolder}/${entry.zone}`;
}

export function parseCatalogRef(ref: string): SelectedZoneRef {
  const slash = ref.indexOf("/");
  if (slash <= 0) {
    throw new Error(`Invalid catalog ref: ${ref}`);
  }
  return {
    owner: ref.substring(0, slash),
    zoneName: ref.substring(slash + 1),
  };
}

export function defaultOwnerFolder(username: string): string {
  return `${username}.u`;
}

export function canManageZone(ownerFolder: string, username: string, isAdmin: boolean): boolean {
  if (isAdmin) {
    return true;
  }
  if (ownerFolder === defaultOwnerFolder(username)) {
    return true;
  }
  return ownerFolder.endsWith(".g");
}

export function canTransferZone(ownerFolder: string, username: string, isAdmin: boolean): boolean {
  if (isAdmin) {
    return true;
  }
  return ownerFolder === defaultOwnerFolder(username);
}

export function hasSupplementalWrite(
  ownerFolder: string,
  zoneName: string,
  zoneRights: Array<{ ownerFolder: string; zoneName: string; kind: string }>,
): boolean {
  return zoneRights.some(
    (entry) =>
      entry.ownerFolder === ownerFolder && entry.zoneName === zoneName && entry.kind === "write",
  );
}
