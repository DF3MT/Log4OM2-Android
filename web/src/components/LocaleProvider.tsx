"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { t, type Locale, type MessageKey } from "@/lib/i18n";

const LocaleContext = createContext<{
  locale: Locale;
  setLocale: (l: Locale) => void;
  tr: (key: MessageKey) => string;
} | null>(null);

const KEY = "log4om_locale";

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>("en");

  useEffect(() => {
    const stored = localStorage.getItem(KEY) as Locale | null;
    if (stored === "de" || stored === "en") setLocaleState(stored);
    else if (navigator.language.toLowerCase().startsWith("de")) {
      setLocaleState("de");
    }
  }, []);

  const setLocale = (l: Locale) => {
    setLocaleState(l);
    localStorage.setItem(KEY, l);
  };

  const value = useMemo(
    () => ({
      locale,
      setLocale,
      tr: (key: MessageKey) => t(locale, key),
    }),
    [locale],
  );

  return (
    <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>
  );
}

export function useLocale() {
  const ctx = useContext(LocaleContext);
  if (!ctx) throw new Error("useLocale outside LocaleProvider");
  return ctx;
}
