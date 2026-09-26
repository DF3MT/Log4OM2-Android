import { clearSession, loadSession, saveSession } from "@/lib/auth-storage";
import type {
  DbConfig,
  DbConfigRequest,
  LogFilter,
  LookupCredentials,
  Qso,
  StationProfile,
  TokenResponse,
} from "@/lib/types";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

function apiBase(): string {
  // Prefer same-origin Next.js rewrite proxy (no CORS).
  if (typeof window !== "undefined") {
    return `${window.location.origin}/backend`;
  }
  const env = process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "") || "";
  return env || "http://127.0.0.1:8080";
}

type RequestOpts = {
  method?: string;
  body?: unknown;
  auth?: boolean;
  formData?: FormData;
  raw?: boolean;
};

let refreshPromise: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  const session = loadSession();
  if (!session?.refreshToken) return false;
  try {
    const res = await fetch(`${apiBase()}/auth/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "no-store",
      },
      cache: "no-store",
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    });
    if (!res.ok) {
      clearSession();
      return false;
    }
    const data = (await res.json()) as TokenResponse;
    saveSession({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      email: data.email || session.email,
    });
    return true;
  } catch {
    clearSession();
    return false;
  }
}

async function apiFetch<T>(path: string, opts: RequestOpts = {}): Promise<T> {
  const doFetch = async (): Promise<Response> => {
    const headers: Record<string, string> = {
      "Cache-Control": "no-store",
    };
    if (!opts.formData) headers["Content-Type"] = "application/json";
    if (opts.auth !== false) {
      const session = loadSession();
      if (session?.accessToken) {
        headers.Authorization = `Bearer ${session.accessToken}`;
      }
    }
    return fetch(`${apiBase()}${path}`, {
      method: opts.method ?? (opts.body || opts.formData ? "POST" : "GET"),
      headers,
      body: opts.formData
        ? opts.formData
        : opts.body !== undefined
          ? JSON.stringify(opts.body)
          : undefined,
      cache: "no-store",
    });
  };

  let res = await doFetch();
  if (res.status === 401 && opts.auth !== false) {
    if (!refreshPromise) refreshPromise = tryRefresh().finally(() => {
      refreshPromise = null;
    });
    const ok = await refreshPromise;
    if (ok) res = await doFetch();
  }

  if (!res.ok) {
    let message = res.statusText;
    try {
      const j = (await res.json()) as { message?: string; error?: string };
      message = j.message || j.error || message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, message);
  }

  if (opts.raw) return res as unknown as T;
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

function filterQuery(filter: LogFilter, limit?: number, offset?: number): string {
  const q = new URLSearchParams();
  Object.entries(filter).forEach(([k, v]) => {
    if (v !== undefined && v !== "") q.set(k, String(v));
  });
  if (limit !== undefined) q.set("limit", String(limit));
  if (offset !== undefined) q.set("offset", String(offset));
  const s = q.toString();
  return s ? `?${s}` : "";
}

export const api = {
  register(email: string, password: string, displayName?: string) {
    return apiFetch<TokenResponse>("/auth/register", {
      method: "POST",
      auth: false,
      body: { email, password, displayName },
    });
  },
  login(email: string, password: string) {
    return apiFetch<TokenResponse>("/auth/login", {
      method: "POST",
      auth: false,
      body: { email, password },
    });
  },
  logout() {
    return apiFetch<void>("/auth/logout", { method: "POST" }).catch(() => undefined);
  },
  getStation() {
    return apiFetch<StationProfile>("/me/station");
  },
  putStation(body: StationProfile) {
    return apiFetch<StationProfile>("/me/station", { method: "PUT", body });
  },
  getDbConfig() {
    return apiFetch<DbConfig>("/me/db-config");
  },
  putDbConfig(body: DbConfigRequest) {
    return apiFetch<DbConfig>("/me/db-config", { method: "PUT", body });
  },
  testDbConfig() {
    return apiFetch<{ ok: boolean; message: string }>("/me/db-config/test", {
      method: "POST",
    });
  },
  getLookup() {
    return apiFetch<LookupCredentials>("/me/lookup-credentials");
  },
  putLookup(body: Partial<LookupCredentials>) {
    return apiFetch<LookupCredentials>("/me/lookup-credentials", {
      method: "PUT",
      body,
    });
  },
  listQsos(filter: LogFilter, limit = 50, offset = 0) {
    return apiFetch<Qso[]>(`/qsos${filterQuery(filter, limit, offset)}`);
  },
  countQsos(filter: LogFilter) {
    return apiFetch<{ count: number }>(`/qsos/count${filterQuery(filter)}`);
  },
  getQso(id: number) {
    return apiFetch<Qso>(`/qsos/${id}`);
  },
  createQso(body: Qso) {
    return apiFetch<Qso>("/qsos", { method: "POST", body });
  },
  updateQso(id: number, body: Qso) {
    return apiFetch<Qso>(`/qsos/${id}`, { method: "PUT", body });
  },
  deleteQso(id: number) {
    return apiFetch<void>(`/qsos/${id}`, { method: "DELETE" });
  },
  async exportAdif(ids: number[]) {
    const res = await apiFetch<Response>("/adif/export", {
      method: "POST",
      body: { ids },
      raw: true,
    });
    return res.blob();
  },
  importAdif(file: File) {
    const fd = new FormData();
    fd.append("file", file);
    return apiFetch<{ inserted: number; skipped: number; parsed: number }>(
      "/adif/import",
      { method: "POST", formData: fd },
    );
  },
};
