import { ImageResponse } from "next/og";

export const runtime = "edge";
export const size = { width: 152, height: 152 };
export const contentType = "image/png";

export default function GET() {
  return new ImageResponse(
    (
      <div
        style={{
          width: 152,
          height: 152,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#111",
          borderRadius: 28,
          color: "#9cf",
          fontSize: 60,
          fontWeight: 700,
          fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial",
          letterSpacing: 2,
        }}
      >
        AI
      </div>
    ),
    { ...size }
  );
}
