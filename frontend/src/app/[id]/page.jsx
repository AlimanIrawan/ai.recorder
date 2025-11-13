'use client';
import { supabase } from "../../lib/supabase";
import { useEffect, useState } from "react";
import Link from "next/link";

export default function Detail({ params }) {
  const id = Number(params.id);
  const [item, setItem] = useState(null);

  useEffect(() => {
    (async () => {
      const { data, error } = await supabase
        .from("records")
        .select("*")
        .eq("id", id)
        .single();
      if (!error) setItem(data);
    })();
  }, [id]);

  if (!item)
    return (
      <main style={{ maxWidth: 900, margin: "24px auto", padding: "0 16px" }}>
        加载中
      </main>
    );

  return (
    <main
      style={{
        maxWidth: 900,
        margin: "24px auto",
        padding: "0 16px",
        fontFamily:
          "-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica,Arial,sans-serif",
      }}
    >
      <Link href="/">← 返回</Link>
      <h1>{item.title || "无标题"}</h1>
      <div style={{ color: "#666" }}>
        {item.started_at ? new Date(item.started_at).toLocaleString() : ""}
      </div>
      {item.summary && <h2 style={{ fontSize: 18 }}>摘要</h2>}
      {item.summary && <pre style={{ whiteSpace: "pre-wrap" }}>{item.summary}</pre>}
      {Array.isArray(item.tags) && item.tags.length > 0 && (
        <div style={{ marginTop: 8 }}>
          {item.tags.map((t, i) => (
            <span key={i} style={{ display: "inline-block", marginRight: 8, color: "#0a6" }}>
              {t}
            </span>
          ))}
        </div>
      )}
      <h2 style={{ fontSize: 18 }}>全文</h2>
      <pre style={{ whiteSpace: "pre-wrap" }}>{item.text}</pre>
      <h2 style={{ fontSize: 18 }}>元数据</h2>
      <div>语言：{item.language || ""}</div>
      <div>时长（秒）：{item.duration_seconds ?? ""}</div>
      <div>
        坐标：{item.latitude ?? ""}, {item.longitude ?? ""}
      </div>
      {item.accuracy != null && <div>精度（米）：{item.accuracy}</div>}
    </main>
  );
}
