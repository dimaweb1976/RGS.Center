import type { NextConfig } from "next";

const production = process.env.NODE_ENV === "production";

const nextConfig: NextConfig = {
  ...(production
    ? {
        output: "export" as const,
        trailingSlash: true
      }
    : {}),

  images: {
    unoptimized: true
  },

  async rewrites() {
    if (production) {
      return [];
    }

    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8080/api/:path*"
      }
    ];
  }
};

export default nextConfig;