"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api } from "@/lib/api";
import {
  clearSession,
  loadSession,
  saveSession,
  type StoredSession,
} from "@/lib/auth-storage";
import type { TokenResponse } from "@/lib/types";

type AuthState = {
  ready: boolean;
  session: StoredSession | null;
  login: (email: string, password: string) => Promise<void>;
  register: (
    email: string,
    password: string,
    displayName?: string,
  ) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

function fromTokens(data: TokenResponse): StoredSession {
  return {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    email: data.email,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [session, setSession] = useState<StoredSession | null>(null);

  useEffect(() => {
    setSession(loadSession());
    setReady(true);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const data = await api.login(email, password);
    const s = fromTokens(data);
    saveSession(s);
    setSession(s);
  }, []);

  const register = useCallback(
    async (email: string, password: string, displayName?: string) => {
      const data = await api.register(email, password, displayName);
      const s = fromTokens(data);
      saveSession(s);
      setSession(s);
    },
    [],
  );

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } finally {
      clearSession();
      setSession(null);
    }
  }, []);

  const value = useMemo(
    () => ({ ready, session, login, register, logout }),
    [ready, session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth outside AuthProvider");
  return ctx;
}
