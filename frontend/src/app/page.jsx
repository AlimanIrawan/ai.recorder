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
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState(null);
  const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL;

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
      <div style={{ margin: "8px 0" }}>
        {!BACKEND && <span style={{ color: "#b00" }}>缺少后端地址环境变量 NEXT_PUBLIC_BACKEND_URL</span>}
        {BACKEND && (
          <button disabled={syncing} onClick={async () => {
            setSyncing(true);
            setSyncResult(null);
            try {
              const r = await fetch(`${BACKEND}/api/sync-drive`, { method: 'POST' });
              const j = await r.json();
              setSyncResult(j);
              const { data } = await supabase
                .from("records")
                .select("*")
                .order("started_at", { ascending: false })
                .limit(50);
              setItems(data || []);
            } finally {
              setSyncing(false);
            }
          }}>手动同步</button>
        )}
      </div>
      {syncResult && (
        <div style={{ background: '#fafafa', border: '1px solid #eee', borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <div>同步结果：{syncResult.ok ? `成功 ${syncResult.count} 条` : `失败：${syncResult.error || ''}`}</div>
          {Array.isArray(syncResult.processed) && syncResult.processed.length > 0 && (
            <div style={{ marginTop: 8 }}>
              {syncResult.processed.map((p) => (
                <div key={p.id} style={{ color: p.ok ? '#090' : '#b00' }}>
                  {p.name} — {p.ok ? '已处理' : (p.skipped ? '已跳过' : `失败：${p.error || ''}`)}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
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
