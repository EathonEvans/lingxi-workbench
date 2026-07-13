"use client";

import type { ThemeProviderProps } from "@lobehub/ui";
import dynamic from "next/dynamic";

const ThemeProvider = dynamic<ThemeProviderProps>(
  () => import("@lobehub/ui").then((module) => module.ThemeProvider),
  { ssr: false },
);

export function AppProvider({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider
      customTheme={{ neutralColor: "slate", primaryColor: "blue" }}
      enableCustomFonts={false}
      enableGlobalStyle={false}
    >
      {children}
    </ThemeProvider>
  );
}
