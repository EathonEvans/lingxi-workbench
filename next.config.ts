import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  transpilePackages: [
    "@lobehub/fluent-emoji",
    "@lobehub/icons",
    "@lobehub/ui",
  ],
  async rewrites() {
    return [
      {
        destination: `${process.env.LINGXI_API_BASE_URL ?? "http://localhost:8080/api"}/:path*`,
        source: "/api/:path*",
      },
    ];
  },
};

export default nextConfig;
