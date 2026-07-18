import { describe, expect, it } from "vitest";
import { parseOwnerFolderDisplay, parseScopedPathDisplay } from "./ownerFolderDisplay";

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

describe("parseScopedPathDisplay", () => {
  it("parses a user-owned scoped key path", () => {
    expect(parseScopedPathDisplay("admin.u/dnscheck.key")).toEqual({
      owner: {
        kind: "user",
        identifier: "admin",
        tooltip: "admin.u",
      },
      basename: "dnscheck.key",
      tooltip: "admin.u/dnscheck.key",
    });
  });

  it("parses a group-owned scoped key path", () => {
    expect(parseScopedPathDisplay("dns-team.g/shared.key")).toEqual({
      owner: {
        kind: "group",
        identifier: "dns-team",
        tooltip: "dns-team.g",
      },
      basename: "shared.key",
      tooltip: "dns-team.g/shared.key",
    });
  });

  it("parses a catalog entry ref the same way as a scoped path", () => {
    expect(parseScopedPathDisplay("admin.u/example.com")).toEqual({
      owner: {
        kind: "user",
        identifier: "admin",
        tooltip: "admin.u",
      },
      basename: "example.com",
      tooltip: "admin.u/example.com",
    });
  });

  it("leaves paths without a slash as unknown owner display", () => {
    expect(parseScopedPathDisplay("not-a-path")).toEqual({
      owner: {
        kind: "unknown",
        identifier: "not-a-path",
        tooltip: "not-a-path",
      },
      basename: "",
      tooltip: "not-a-path",
    });
  });
});
