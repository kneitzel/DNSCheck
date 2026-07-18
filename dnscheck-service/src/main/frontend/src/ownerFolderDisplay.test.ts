import { describe, expect, it } from "vitest";
import { parseOwnerFolderDisplay } from "./ownerFolderDisplay";

describe("parseOwnerFolderDisplay", () => {
  it("parses a user owner folder into bare identifier and tooltip", () => {
    expect(parseOwnerFolderDisplay("admin.u")).toEqual({
      kind: "user",
      identifier: "admin",
      tooltip: "admin.u",
    });
  });

  it("parses a group owner folder into bare identifier and tooltip", () => {
    expect(parseOwnerFolderDisplay("dns-team.g")).toEqual({
      kind: "group",
      identifier: "dns-team",
      tooltip: "dns-team.g",
    });
  });

  it("leaves malformed owner folders as raw text without inventing a kind", () => {
    expect(parseOwnerFolderDisplay("legacy-flat")).toEqual({
      kind: "unknown",
      identifier: "legacy-flat",
      tooltip: "legacy-flat",
    });
  });

  it("treats blank owner folders as unknown empty display", () => {
    expect(parseOwnerFolderDisplay("")).toEqual({
      kind: "unknown",
      identifier: "",
      tooltip: "",
    });
    expect(parseOwnerFolderDisplay("   ")).toEqual({
      kind: "unknown",
      identifier: "",
      tooltip: "",
    });
    expect(parseOwnerFolderDisplay(null)).toEqual({
      kind: "unknown",
      identifier: "",
      tooltip: "",
    });
    expect(parseOwnerFolderDisplay(undefined)).toEqual({
      kind: "unknown",
      identifier: "",
      tooltip: "",
    });
  });
});
