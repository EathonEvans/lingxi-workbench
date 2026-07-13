import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  transpilePackages: [
    "@lobehub/fluent-emoji",
    "@lobehub/icons",
    "@lobehub/ui",
  ],
};

export default nextConfig;
