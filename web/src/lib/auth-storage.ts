const ACCESS = "log4om_access";
const REFRESH = "log4om_refresh";
const EMAIL = "log4om_email";

export type StoredSession = {
  accessToken: string;
  refreshToken: string;
  email: string;
};

export function loadSession(): StoredSession | null {
  if (typeof window === "undefined") return null;
  const accessToken = localStorage.getItem(ACCESS);
  const refreshToken = localStorage.getItem(REFRESH);
  const email = localStorage.getItem(EMAIL) ?? "";
  if (!accessToken || !refreshToken) return null;
  return { accessToken, refreshToken, email };
}

export function saveSession(session: StoredSession) {
  localStorage.setItem(ACCESS, session.accessToken);
  localStorage.setItem(REFRESH, session.refreshToken);
  localStorage.setItem(EMAIL, session.email);
}

export function clearSession() {
  localStorage.removeItem(ACCESS);
  localStorage.removeItem(REFRESH);
  localStorage.removeItem(EMAIL);
}
