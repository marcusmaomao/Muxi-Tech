import axios from "axios";
import { http } from "./http";
import type { ApiEnvelope, LoginResult, UserProfile } from "./types";

export async function login(username: string, password: string): Promise<LoginResult> {
  const response = await http.post<ApiEnvelope<LoginResult>>("/auth/login", { username, password });
  if (!response.data.data) {
    throw new Error("登录响应缺少数据");
  }
  return response.data.data;
}

export async function currentUser(): Promise<UserProfile> {
  const response = await http.get<ApiEnvelope<UserProfile>>("/auth/me");
  if (!response.data.data) {
    throw new Error("用户信息缺失");
  }
  return response.data.data;
}

export async function logout(): Promise<void> {
  await http.post("/auth/logout");
}

export async function healthStatus(): Promise<boolean> {
  try {
    const response = await axios.get<{ status: string }>("/actuator/health", { timeout: 5000 });
    return response.data.status === "UP";
  } catch {
    return false;
  }
}
