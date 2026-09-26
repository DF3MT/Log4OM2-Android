import type { Metadata } from "next";
import { Source_Code_Pro, Source_Sans_3 } from "next/font/google";
import { AuthProvider } from "@/components/AuthProvider";
import { LocaleProvider } from "@/components/LocaleProvider";
import "./globals.css";

const sans = Source_Sans_3({
  subsets: ["latin"],
  variable: "--font-sans-family",
  weight: ["400", "600", "700"],
});

const mono = Source_Code_Pro({
  subsets: ["latin"],
  variable: "--font-mono-family",
  weight: ["500", "600"],
});

export const metadata: Metadata = {
  title: "Log4OM Portal",
  description: "Multi-tenant Log4OM2 web logbook",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${sans.variable} ${mono.variable} antialiased`}>
        <LocaleProvider>
          <AuthProvider>{children}</AuthProvider>
        </LocaleProvider>
      </body>
    </html>
  );
}
