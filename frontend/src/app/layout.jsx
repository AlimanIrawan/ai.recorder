export const metadata = {
  title: "AI Recorder",
  description: "语音转录与时间轴展示",
};

export default function RootLayout({ children }) {
  return (
    <html lang="zh-CN">
      <body
        style={{
          background: "#f6f7f9",
          color: "#111",
          margin: 0,
          minHeight: "100vh",
        }}
      >
        {children}
      </body>
    </html>
  );
}
