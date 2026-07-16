import { ApiError } from "./generated/core/ApiError";

export function formatApiError(error: unknown): string {
  if (error instanceof ApiError) {
    const body = error.body;
    if (body && typeof body === "object") {
      const record = body as Record<string, unknown>;
      if (typeof record.message === "string" && record.message.length > 0) {
        return record.message;
      }
      if (typeof record.detail === "string" && record.detail.length > 0) {
        return record.detail;
      }
      if (typeof record.title === "string" && typeof record.detail === "string") {
        return `${record.title}: ${record.detail}`;
      }
    }
    if (typeof body === "string" && body.length > 0) {
      return body;
    }
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "An unexpected error occurred.";
}
