import type { Metadata } from "next";
import { AppProvider } from "@/components/app-provider";
import "./globals.css";

export const metadata: Metadata = {
  title: "灵析 AI Workbench",
  description: "面向企业知识与智能协作的 AI 工作台",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body>
        <AppProvider>{children}</AppProvider>
      </body>
    </html>
  );
}
