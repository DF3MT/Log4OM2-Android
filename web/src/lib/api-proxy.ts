import { NextRequest, NextResponse } from "next/server";

function upstreamBase(): string {
  return (
    process.env.API_URL?.replace(/\/$/, "") ||
    process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "") ||
    "http://api:8080"
  );
}

/** Forward /api/* and /backend/* to the Spring API, dropping that prefix. */
export async function proxyApi(req: NextRequest, path: string[]) {
  const targetPath = path.join("/");
  const url = new URL(req.url);
  const target = `${upstreamBase()}/${targetPath}${url.search}`;

  const headers = new Headers();
  const pass = ["authorization", "content-type", "accept", "cache-control"] as const;
  for (const h of pass) {
    const v = req.headers.get(h);
    if (v) headers.set(h, v);
  }

  let body: ArrayBuffer | undefined;
  if (req.method !== "GET" && req.method !== "HEAD") {
    body = await req.arrayBuffer();
  }

  try {
    const upstream = await fetch(target, {
      method: req.method,
      headers,
      body,
      cache: "no-store",
      redirect: "manual",
    });

    const outHeaders = new Headers();
    for (const h of ["content-type", "content-disposition", "cache-control", "pragma"]) {
      const v = upstream.headers.get(h);
      if (v) outHeaders.set(h, v);
    }
    outHeaders.set("Cache-Control", "no-store");

    return new NextResponse(upstream.body, {
      status: upstream.status,
      headers: outHeaders,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : "proxy error";
    return NextResponse.json(
      { message: `API proxy failed (${upstreamBase()}): ${message}` },
      { status: 502 },
    );
  }
}
