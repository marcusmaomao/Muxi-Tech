import type { UserProfile } from "./types";

export const SESSION_KEY = "mop.session";

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  user: UserProfile;
  permissions: string[];
}

export function readSession(): StoredSession | null {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as StoredSession;
    if (!parsed.accessToken || !parsed.user) {
      return null;
    }
    parsed.permissions = parsed.permissions ?? [];
    return parsed;
  } catch {
    return null;
  }
}

export function writeSession(session: StoredSession | null): void {
  if (!session) {
    sessionStorage.removeItem(SESSION_KEY);
    return;
  }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
}
