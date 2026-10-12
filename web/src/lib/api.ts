import { clearSession, loadSession, saveSession } from "@/lib/auth-storage";
import type {
  DbConfig,
  DbConfigRequest,
  Logbook,
  LogbookWrite,
  LogFilter,
  SyncResult,
  LookupCredentials,
  LookupTestRequest,
  LookupTestResult,
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
  signal?: AbortSignal;
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
      signal: opts.signal,
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
  return parseJson(text) as T;
}

/** Log4OM qsoid values are 17 digits and are not safe JSON numbers in JS. */
function parseJson(text: string): unknown {
  const safe = text.replace(
    /([:[,]\s*)(-?\d{16,})(?=\s*[,}\]])/g,
    '$1"$2"',
  );
  return JSON.parse(safe);
}

function asQso(qso: Qso): Qso {
  if (qso.qsoid == null || qso.qsoid === "") return qso;
  return { ...qso, qsoid: String(qso.qsoid) };
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
  testDbConfig(body: DbConfigRequest) {
    return apiFetch<{ ok: boolean; message: string }>("/me/db-config/test", {
      method: "POST",
      body,
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
  testLookup(body: LookupTestRequest) {
    return apiFetch<LookupTestResult>("/me/lookup-credentials/test", {
      method: "POST",
      body,
    });
  },
  listQsos(filter: LogFilter, limit = 50, offset = 0, signal?: AbortSignal) {
    return apiFetch<Qso[]>(`/qsos${filterQuery(filter, limit, offset)}`, {
      signal,
    }).then((rows) => rows.map(asQso));
  },
  countQsos(filter: LogFilter, signal?: AbortSignal) {
    return apiFetch<{ count: number }>(`/qsos/count${filterQuery(filter)}`, { signal });
  },
  getQso(id: string) {
    return apiFetch<Qso>(`/qsos/${id}`).then(asQso);
  },
  createQso(body: Qso) {
    return apiFetch<Qso>("/qsos", { method: "POST", body }).then(asQso);
  },
  updateQso(id: string, body: Qso) {
    return apiFetch<Qso>(`/qsos/${id}`, { method: "PUT", body }).then(asQso);
  },
  deleteQso(id: string) {
    return apiFetch<void>(`/qsos/${id}`, { method: "DELETE" });
  },
  async exportAdif(ids: string[]) {
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
  listLogbooks() {
    return apiFetch<Logbook[]>("/me/logbooks");
  },
  createLogbook(body: LogbookWrite) {
    return apiFetch<Logbook>("/me/logbooks", { method: "POST", body });
  },
  updateLogbook(id: string, body: LogbookWrite) {
    return apiFetch<Logbook>(`/me/logbooks/${id}`, { method: "PUT", body });
  },
  deleteLogbook(id: string) {
    return apiFetch<void>(`/me/logbooks/${id}`, { method: "DELETE" });
  },
  activateLogbook(id: string) {
    return apiFetch<Logbook>(`/me/logbooks/${id}/activate`, { method: "POST" });
  },
  testLogbook(id: string | null, body: LogbookWrite) {
    const path = id ? `/me/logbooks/${id}/test` : "/me/logbooks/connection-test";
    return apiFetch<{ ok: boolean; message: string }>(path, { method: "POST", body });
  },
  async exportLogbookAdif(id: string) {
    const res = await apiFetch<Response>(`/me/logbooks/${id}/adif`, { raw: true });
    const disposition = res.headers.get("Content-Disposition") || "";
    const match = /filename="([^"]+)"/.exec(disposition);
    return { blob: await res.blob(), filename: match?.[1] || "logbook.adi" };
  },
  syncQrz(id: string) {
    return apiFetch<SyncResult>(`/me/logbooks/${id}/sync/qrz`, { method: "POST" });
  },
  syncEqsl(id: string) {
    return apiFetch<SyncResult>(`/me/logbooks/${id}/sync/eqsl`, { method: "POST" });
  },
};
