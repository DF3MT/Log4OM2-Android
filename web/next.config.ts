import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  // API is proxied at runtime by src/app/backend/[...path]/route.ts (reads API_URL).
};

export default nextConfig;
