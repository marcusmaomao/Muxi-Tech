import axios, { type AxiosError } from "axios";
import type { ApiEnvelope } from "./types";
import { buildHeaders } from "./headers";
import { readSession, writeSession } from "./session";

export class ApiError extends Error {
  code: string;
  traceId?: string;

  constructor(code: string, message: string, traceId?: string) {
    super(message);
    this.code = code;
    this.traceId = traceId;
  }
}

export const http = axios.create({
  baseURL: "/api/v1",
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const session = readSession();
  const traceId = crypto.randomUUID().replace(/-/g, "");
  const headers = buildHeaders({
    token: session?.accessToken,
    method: config.method,
    traceId,
  });
  Object.entries(headers).forEach(([key, value]) => {
    config.headers.set(key, value);
  });
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    const status = error.response?.status;
    const body = error.response?.data;
    const url = error.config?.url ?? "";
    if (status === 401 && !url.includes("/auth/login")) {
      writeSession(null);
      window.dispatchEvent(new CustomEvent("mop:unauthorized"));
    }
    return Promise.reject(
      new ApiError(body?.code ?? "MOP-5000", body?.message ?? "网络异常", body?.traceId),
    );
  },
);
