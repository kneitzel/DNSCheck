export const STATUS_LABELS: Record<string, string> = {
  enforced: "Enforced",
  ad_hoc: "Ad-hoc",
  drifted: "Drifted",
  missing: "Missing",
  marked_for_deletion: "Marked for deletion",
};

export const STATUS_COLORS: Record<string, "success" | "default" | "warning" | "error" | "info"> = {
  enforced: "success",
  ad_hoc: "default",
  drifted: "warning",
  missing: "error",
  marked_for_deletion: "info",
};
