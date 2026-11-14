import { describe, expect, it } from "vitest";
import { buildHeaders } from "../../src/api/headers";

describe("buildHeaders", () => {
  it("attaches the bearer token and a trace id", () => {
    const headers = buildHeaders({ token: "abc", method: "get", traceId: "trace-1" });
    expect(headers.Authorization).toBe("Bearer abc");
    expect(headers["X-Trace-Id"]).toBe("trace-1");
    expect(headers["Idempotency-Key"]).toBeUndefined();
  });

  it("adds an idempotency key on writes", () => {
    const headers = buildHeaders({ method: "post", traceId: "trace-2", idempotencyKey: "idem-1" });
    expect(headers.Authorization).toBeUndefined();
    expect(headers["Idempotency-Key"]).toBe("idem-1");
  });
});
