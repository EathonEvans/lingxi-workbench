export type LoginCredentials = {
  password: string;
  username: string;
};

export type LoginUser = {
  avatarUrl?: string;
  displayName: string;
  id: number;
  username: string;
};

export type AuthSession = {
  expiresAt: number;
  tokenName: string;
  tokenValue: string;
  user: LoginUser;
};

type ApiResponse<T> = {
  code: number;
  data?: T;
  msg: string;
};

type LoginResponse = {
  expiresInSeconds: number;
  tokenName: string;
  tokenValue: string;
  user: LoginUser;
};

export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api").replace(
  /\/$/,
  "",
);

export class AuthenticationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AuthenticationError";
  }
}

export async function login(credentials: LoginCredentials): Promise<AuthSession> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/v1/auth/login`, {
      body: JSON.stringify(credentials),
      headers: { "Content-Type": "application/json" },
      method: "POST",
    });
  } catch {
    throw new AuthenticationError("无法连接服务，请确认用户端服务已经启动");
  }

  const payload = (await response.json().catch(() => null)) as
    | ApiResponse<LoginResponse>
    | null;

  if (!response.ok || payload?.code !== 200 || !payload.data) {
    throw new AuthenticationError(payload?.msg || "登录失败，请稍后重试");
  }

  return {
    expiresAt: Date.now() + payload.data.expiresInSeconds * 1000,
    tokenName: payload.data.tokenName,
    tokenValue: payload.data.tokenValue,
    user: payload.data.user,
  };
}

export function isSessionValid(session: AuthSession | null): session is AuthSession {
  return Boolean(session?.tokenValue && session.expiresAt > Date.now());
}

// ─── 401 统一处理 ────────────────────────────────────────────

/** 后端判定登录失效时的统一回调，由 AuthProvider 注册为「清空本地会话」。 */
let unauthorizedHandler: (() => void) | null = null;

/** 注册 401 处理回调；传入 null 取消注册。 */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

/** 请求层收到 401 时调用，清空本地 token 缓存并让界面回到登录态。 */
export function notifyUnauthorized() {
  unauthorizedHandler?.();
}
