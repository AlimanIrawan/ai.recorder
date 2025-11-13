'use client';
import { supabase } from "../lib/supabase";
import Link from "next/link";
import { useEffect, useState } from "react";

function formatTime(t) {
  return t ? new Date(t).toLocaleString() : "";
}

export default function Page() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      const { data, error } = await supabase
        .from("records")
        .select("*")
        .order("started_at", { ascending: false })
        .limit(50);
      if (!error) setItems(data || []);
      setLoading(false);
    })();
  }, []);

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
      <h1>记录</h1>
      {loading && <div>加载中</div>}
      {items.map((x) => (
        <article
          key={x.id}
          style={{
            background: "#fff",
            border: "1px solid #eee",
            borderRadius: 12,
            padding: 16,
            margin: "12px 0",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
            <h2 style={{ margin: 0, fontSize: 18 }}>{x.title || "无标题"}</h2>
            <div style={{ color: "#666" }}>{formatTime(x.started_at)}</div>
          </div>
          {x.summary && <p style={{ whiteSpace: "pre-wrap" }}>{x.summary}</p>}
          {Array.isArray(x.tags) && x.tags.length > 0 && (
            <div style={{ marginTop: 8 }}>
              {x.tags.map((t, i) => (
                <span key={i} style={{ display: "inline-block", marginRight: 8, color: "#0a6" }}>
                  {t}
                </span>
              ))}
            </div>
          )}
          <div style={{ marginTop: 12 }}>
            <Link href={`/${x.id}`}>查看详情</Link>
          </div>
        </article>
      ))}
    </main>
  );
}
