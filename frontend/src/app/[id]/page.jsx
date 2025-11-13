'use client';
import { supabase } from "../../lib/supabase";
import { useEffect, useState } from "react";
import Link from "next/link";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL;

export default function Detail({ params }) {
  const id = Number(params.id);
  const [item, setItem] = useState(null);
  const [editTitle, setEditTitle] = useState("");
  const [editSummary, setEditSummary] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      const { data, error } = await supabase
        .from("records")
        .select("*")
        .eq("id", id)
        .single();
      if (!error) {
        setItem(data);
        setEditTitle(data?.title || "");
        setEditSummary(data?.summary || "");
      }
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
      <div style={{ marginTop: 16, borderTop: '1px solid #eee', paddingTop: 12 }}>
        <h2 style={{ fontSize: 18 }}>编辑</h2>
        {!BACKEND && <div style={{ color: '#b00' }}>缺少后端地址环境变量 NEXT_PUBLIC_BACKEND_URL</div>}
        <div>
          <label>标题：<input value={editTitle} onChange={(e) => setEditTitle(e.target.value)} style={{ width: '100%' }} /></label>
        </div>
        <div style={{ marginTop: 8 }}>
          <label>摘要：<textarea value={editSummary} onChange={(e) => setEditSummary(e.target.value)} rows={6} style={{ width: '100%' }} /></label>
        </div>
        <div style={{ marginTop: 8 }}>
          <button disabled={saving || !BACKEND} onClick={async () => {
            setSaving(true);
            try {
              const r = await fetch(`${BACKEND}/api/update-record`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id, title: editTitle, summary: editSummary }),
              });
              const j = await r.json();
              if (j?.ok) setItem(j.data);
            } finally {
              setSaving(false);
            }
          }}>保存</button>
        </div>
      </div>
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
