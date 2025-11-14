import { ImageResponse } from "next/og";

export const runtime = "edge";
export const size = { width: 167, height: 167 };
export const contentType = "image/png";

export default function GET() {
  return new ImageResponse(
    (
      <div
        style={{
          width: 167,
          height: 167,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#111",
          borderRadius: 30,
          color: "#9cf",
          fontSize: 66,
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
