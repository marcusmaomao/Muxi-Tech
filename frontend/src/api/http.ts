import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { ApiEnvelope } from "./types";
import { buildHeaders } from "./headers";
import { readSession, writeSession } from "./session";
import type { LoginResult } from "./types";

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
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const status = error.response?.status;
    const body = error.response?.data;
    const url = error.config?.url ?? "";
    const config = error.config as InternalAxiosRequestConfig & { retried?: boolean };
    if (status === 401 && config && !config.retried && !url.includes("/auth/login") && !url.includes("/auth/refresh")) {
      const session = readSession();
      if (session?.refreshToken) {
        try {
          const refreshed = await axios.post<ApiEnvelope<LoginResult>>("/api/v1/auth/refresh", {
            refreshToken: session.refreshToken,
          });
          const next = refreshed.data.data;
          if (!next) {
            throw new Error("刷新失败");
          }
          writeSession({
            accessToken: next.accessToken,
            refreshToken: next.refreshToken,
            user: next.user,
            permissions: session.permissions,
          });
          config.retried = true;
          config.headers.set("Authorization", `Bearer ${next.accessToken}`);
          return http.request(config);
        } catch {
          writeSession(null);
          window.dispatchEvent(new CustomEvent("mop:unauthorized"));
        }
      }
    }
    if (status === 401 && !url.includes("/auth/login")) {
      writeSession(null);
      window.dispatchEvent(new CustomEvent("mop:unauthorized"));
    }
    return Promise.reject(
      new ApiError(body?.code ?? "MOP-5000", body?.message ?? "网络异常", body?.traceId),
    );
  },
);
