export const metadata = {
  title: "AI Recorder",
  description: "语音转录与时间轴展示",
};

import SwRegister from "./sw-register";

export default function RootLayout({ children }) {
  return (
    <html lang="zh-CN">
      <head>
        <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover" />
        <meta name="theme-color" content="#111111" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
        <link rel="manifest" href="/manifest.json" />
        <link rel="icon" href="/favicon.svg" type="image/svg+xml" />
        <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png?v=1" />
      </head>
      <body
        style={{
          background: "#f6f7f9",
          color: "#111",
          margin: 0,
          minHeight: "100vh",
        }}
      >
        <SwRegister />
        {children}
      </body>
    </html>
  );
}
