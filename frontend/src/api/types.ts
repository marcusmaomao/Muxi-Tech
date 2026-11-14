export interface UserProfile {
  uid: string;
  username: string;
  displayName: string;
  tenantId: string;
  tenantName: string;
}

export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data?: T;
  details?: unknown;
  traceId?: string;
}
