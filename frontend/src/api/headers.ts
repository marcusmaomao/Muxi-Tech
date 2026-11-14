const WRITE_METHODS = new Set(["post", "put", "patch", "delete"]);

export function buildHeaders(input: {
  token?: string | null;
  method?: string;
  traceId: string;
  idempotencyKey?: string;
}): Record<string, string> {
  const headers: Record<string, string> = {
    "X-Trace-Id": input.traceId,
  };
  if (input.token) {
    headers.Authorization = `Bearer ${input.token}`;
  }
  const method = (input.method ?? "get").toLowerCase();
  if (WRITE_METHODS.has(method)) {
    headers["Idempotency-Key"] = input.idempotencyKey ?? input.traceId;
  }
  return headers;
}
