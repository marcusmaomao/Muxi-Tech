export interface UserProfile {
  uid: string;
  username: string;
  displayName: string;
  tenantId: string;
  tenantName: string;
}

export interface MenuNode {
  id: string;
  parentId?: string;
  name: string;
  path?: string;
  menuType: string;
  permission?: string;
}

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface SessionProfile {
  user: UserProfile;
  permissions: string[];
  menus: MenuNode[];
}

export interface CaptchaChallenge {
  captchaId: string;
  imageSvg: string;
  debugCode?: string;
}

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data?: T;
  details?: unknown;
  traceId?: string;
}
