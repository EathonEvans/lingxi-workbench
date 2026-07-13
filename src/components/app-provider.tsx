"use client";

import type { ThemeProviderProps } from "@lobehub/ui";
import dynamic from "next/dynamic";
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

const ThemeProvider = dynamic<ThemeProviderProps>(
  () => import("@lobehub/ui").then((module) => module.ThemeProvider),
  { ssr: false },
);

type ThemeMode = "dark" | "light";

type ThemeModeContextValue = {
  appearance: ThemeMode;
  toggleAppearance: () => void;
};

const THEME_STORAGE_KEY = "lingxi-workbench-theme";
const ThemeModeContext = createContext<ThemeModeContextValue | null>(null);

function getInitialAppearance(): ThemeMode {
  if (typeof window === "undefined") return "light";

  try {
    const storedAppearance = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (storedAppearance === "dark" || storedAppearance === "light") {
      return storedAppearance;
    }
  } catch {
    // Storage can be unavailable in privacy-restricted browser contexts.
  }

  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

export function useThemeMode() {
  const context = useContext(ThemeModeContext);

  if (!context) {
    throw new Error("useThemeMode must be used within AppProvider");
  }

  return context;
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [appearance, setAppearance] = useState<ThemeMode>(getInitialAppearance);
  const contextValue = useMemo<ThemeModeContextValue>(
    () => ({
      appearance,
      toggleAppearance: () =>
        setAppearance((current) => (current === "light" ? "dark" : "light")),
    }),
    [appearance],
  );

  useEffect(() => {
    document.documentElement.dataset.theme = appearance;

    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, appearance);
    } catch {
      // The current session can still use the selected appearance.
    }
  }, [appearance]);

  return (
    <ThemeModeContext.Provider value={contextValue}>
      <ThemeProvider
        appearance={appearance}
        customTheme={{ neutralColor: "slate", primaryColor: "blue" }}
        enableCustomFonts={false}
        enableGlobalStyle={false}
      >
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
}
