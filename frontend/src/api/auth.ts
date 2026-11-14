import axios from "axios";
import { http } from "./http";
import type { ApiEnvelope, CaptchaChallenge, LoginResult, SessionProfile } from "./types";

export async function fetchCaptcha(): Promise<CaptchaChallenge> {
  const response = await http.get<ApiEnvelope<CaptchaChallenge>>("/auth/captcha");
  if (!response.data.data) {
    throw new Error("验证码缺失");
  }
  return response.data.data;
}

export async function login(
  username: string,
  password: string,
  captchaId: string,
  captchaCode: string,
): Promise<LoginResult> {
  const response = await http.post<ApiEnvelope<LoginResult>>("/auth/login", {
    username,
    password,
    captchaId,
    captchaCode,
  });
  if (!response.data.data) {
    throw new Error("登录响应缺少数据");
  }
  return response.data.data;
}

export async function refreshSession(refreshToken: string): Promise<LoginResult> {
  const response = await axios.post<ApiEnvelope<LoginResult>>("/api/v1/auth/refresh", { refreshToken });
  if (!response.data.data) {
    throw new Error("刷新失败");
  }
  return response.data.data;
}

export async function currentUser(): Promise<SessionProfile> {
  const response = await http.get<ApiEnvelope<SessionProfile>>("/auth/me");
  if (!response.data.data) {
    throw new Error("用户信息缺失");
  }
  return response.data.data;
}

export async function logout(refreshToken?: string): Promise<void> {
  await http.post("/auth/logout", refreshToken ? { refreshToken } : {});
}

export async function healthStatus(): Promise<boolean> {
  try {
    const response = await axios.get<{ status: string }>("/actuator/health", { timeout: 5000 });
    return response.data.status === "UP";
  } catch {
    return false;
  }
}
