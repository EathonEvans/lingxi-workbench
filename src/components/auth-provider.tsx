"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  type AuthSession,
  type LoginCredentials,
  isSessionValid,
  login as requestLogin,
  setUnauthorizedHandler,
} from "@/lib/auth";

type AuthContextValue = {
  login: (credentials: LoginCredentials) => Promise<AuthSession>;
  logout: () => void;
  ready: boolean;
  session: AuthSession | null;
};

const SESSION_STORAGE_KEY = "lingxi-workbench-session";
const AuthContext = createContext<AuthContextValue | null>(null);

function removeStoredSession() {
  try {
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // 浏览器禁用存储时仍然允许使用当前内存会话
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [session, setSession] = useState<AuthSession | null>(null);

  useEffect(() => {
    const animationFrame = window.requestAnimationFrame(() => {
      try {
        const storedSession = window.localStorage.getItem(SESSION_STORAGE_KEY);
        const parsedSession = storedSession
          ? (JSON.parse(storedSession) as AuthSession)
          : null;

        if (isSessionValid(parsedSession)) {
          setSession(parsedSession);
        } else {
          removeStoredSession();
        }
      } catch {
        removeStoredSession();
      } finally {
        setReady(true);
      }
    });

    return () => window.cancelAnimationFrame(animationFrame);
  }, []);

  // 后端返回 401 时统一清空本地 token 缓存；页面由会话驱动的守卫自动跳回登录页。
  useEffect(() => {
    setUnauthorizedHandler(() => {
      removeStoredSession();
      setSession(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      login: async (credentials) => {
        const nextSession = await requestLogin(credentials);
        try {
          window.localStorage.setItem(
            SESSION_STORAGE_KEY,
            JSON.stringify(nextSession),
          );
        } catch {
          // 浏览器禁用存储时，会话仅在当前页面生命周期内有效
        }
        setSession(nextSession);
        return nextSession;
      },
      logout: () => {
        removeStoredSession();
        setSession(null);
      },
      ready,
      session,
    }),
    [ready, session],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth 必须在 AuthProvider 内使用");
  }

  return context;
}
