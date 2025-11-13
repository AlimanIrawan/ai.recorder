import express from "express";
import multer from "multer";
import fs from "fs";
import path from "path";
import morgan from "morgan";
import { fileURLToPath } from "url";
import OpenAI from "openai";
import { fetch } from "undici";
import { createClient } from "@supabase/supabase-js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
app.use(morgan("dev"));

const uploadsDir = process.env.UPLOADS_DIR || "/tmp";
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir, { recursive: true });
}

const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, uploadsDir);
  },
  filename: function (req, file, cb) {
    const ext = path.extname(file.originalname) || ".m4a";
    const name = `${Date.now()}-${Math.random().toString(36).slice(2)}${ext}`;
    cb(null, name);
  },
});

const upload = multer({
  storage,
  limits: {
    fileSize: 1024 * 1024 * 1024,
  },
});

function getClient(keyOverride) {
  const key = keyOverride || process.env.OPENAI_API_KEY;
  if (!key) throw new Error("缺少 OpenAI API Key（可在表单提供 openai_key 或设置 OPENAI_API_KEY）");
  return new OpenAI({ apiKey: key });
}

function htmlPage({ title, body }) {
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"/><title>${title}</title><style>body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:900px;margin:24px auto;padding:0 16px;line-height:1.6}h1{font-size:20px}pre{white-space:pre-wrap;word-wrap:break-word;background:#f7f7f7;padding:12px;border-radius:8px}code{font-family:Menlo,Monaco,Consolas,monospace}.meta{color:#555}.segment{margin:8px 0;padding:8px;border-left:4px solid #eee;background:#fafafa}.seg-time{color:#666;font-size:12px;margin-bottom:4px}</style></head><body>${body}</body></html>`;
}

function formatMeta({ started_at, duration_seconds, latitude, longitude, accuracy, language }) {
  const items = [
    `开始时间：${started_at ?? ""}`,
    `时长（秒）：${duration_seconds ?? ""}`,
    `坐标：${latitude ?? ""}, ${longitude ?? ""}`,
    accuracy ? `精度（米）：${accuracy}` : null,
    language ? `检测语言：${language}` : null,
  ].filter(Boolean);
  return `<div class="meta">${items.map((x) => `<div>${x}</div>`).join("")}</div>`;
}

async function transcribeFile(filePath, apiKey) {
  const stream = fs.createReadStream(filePath);
  const client = getClient(apiKey);
  const resp = await client.audio.transcriptions.create({
    file: stream,
    model: "whisper-1",
    response_format: "verbose_json",
  });
  return resp;
}

app.get("/", (req, res) => {
  const body = `<h1>音频上传与转录</h1><div>POST 到 <code>/api/upload-audio</code> 以 <code>multipart/form-data</code> 方式上传音频与元数据。</div>`;
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(htmlPage({ title: "音频上传与转录", body }));
});

app.post("/api/upload-audio", upload.single("file"), async (req, res) => {
  res.set("Content-Type", "text/html; charset=utf-8");
  const file = req.file;
  const { started_at, duration_seconds, latitude, longitude, accuracy, timezone, device_model, openai_key, deepseek_key } = req.body ?? {};
  if (!file) {
    res.status(400).send(htmlPage({ title: "错误", body: `<h1>缺少音频文件</h1>` }));
    return;
  }
  try {
    const tr = await transcribeFile(file.path, openai_key);
    let summaryBlock = "";
    if (deepseek_key) {
      const ds = await deepseekSummarize({
        text: tr.text,
        meta: { started_at, duration_seconds, latitude, longitude, accuracy },
        apiKey: deepseek_key,
        baseUrl: process.env.DEEPSEEK_BASE_URL,
        model: process.env.DEEPSEEK_MODEL,
      });
      const tags = Array.isArray(ds.tags) ? ds.tags.join(" ") : "";
      summaryBlock = `<h1>摘要与标题</h1><div><strong>标题：</strong>${ds.title || ""}</div><div><strong>摘要：</strong><pre>${ds.summary || ""}</pre></div><div><strong>话题：</strong>${tags}</div>`;
      try {
        await saveRecordToSupabase({
          text: tr.text,
          language: tr.language || null,
          summary: ds.summary || null,
          title: ds.title || null,
          tags: Array.isArray(ds.tags) ? ds.tags : null,
          started_at: started_at || null,
          duration_seconds: duration_seconds ? Number(duration_seconds) : null,
          latitude: latitude ? Number(latitude) : null,
          longitude: longitude ? Number(longitude) : null,
          accuracy: accuracy ? Number(accuracy) : null,
          audio_url: null,
        });
      } catch (e) {}
    }
    const metaHtml = formatMeta({ started_at, duration_seconds, latitude, longitude, accuracy, language: tr.language });
    const body = `${metaHtml}${summaryBlock}<h1>转录全文</h1><pre>${tr.text}</pre>`;
    res.status(200).send(htmlPage({ title: "转录结果", body }));
  } catch (err) {
    const msg = typeof err?.message === "string" ? err.message : "转录失败";
    let detail = "";
    try {
      const obj = JSON.parse(JSON.stringify(err, Object.getOwnPropertyNames(err)));
      detail = `\n\n详情：\n${JSON.stringify(obj, null, 2)}`;
    } catch {}
    res.status(500).send(htmlPage({ title: "错误", body: `<h1>转录失败</h1><pre>${msg}${detail}</pre>` }));
  } finally {
    if (file?.path) {
      fs.unlink(file.path, () => {});
    }
  }
});

app.post("/api/upload-audio-url", express.json({ limit: "1mb" }), async (req, res) => {
  res.set("Content-Type", "text/html; charset=utf-8");
  const { audio_url, started_at, duration_seconds, latitude, longitude, accuracy, openai_key, deepseek_key } = req.body ?? {};
  if (!audio_url) {
    res.status(400).send(htmlPage({ title: "错误", body: `<h1>缺少 audio_url</h1>` }));
    return;
  }
  const tempPath = path.join(uploadsDir, `${Date.now()}-${Math.random().toString(36).slice(2)}.m4a`);
  try {
    const r = await fetch(audio_url);
    if (!r.ok) throw new Error(`无法下载音频：${r.status}`);
    const fileStream = fs.createWriteStream(tempPath);
    await new Promise((resolve, reject) => {
      r.body.pipe(fileStream);
      r.body.on("error", reject);
      fileStream.on("finish", resolve);
      fileStream.on("error", reject);
    });
    const tr = await transcribeFile(tempPath, openai_key);
    let summaryBlock = "";
    if (deepseek_key) {
      const ds = await deepseekSummarize({
        text: tr.text,
        meta: { started_at, duration_seconds, latitude, longitude, accuracy },
        apiKey: deepseek_key,
        baseUrl: process.env.DEEPSEEK_BASE_URL,
        model: process.env.DEEPSEEK_MODEL,
      });
      const tags = Array.isArray(ds.tags) ? ds.tags.join(" ") : "";
      summaryBlock = `<h1>摘要与标题</h1><div><strong>标题：</strong>${ds.title || ""}</div><div><strong>摘要：</strong><pre>${ds.summary || ""}</pre></div><div><strong>话题：</strong>${tags}</div>`;
      try {
        await saveRecordToSupabase({
          text: tr.text,
          language: tr.language || null,
          summary: ds.summary || null,
          title: ds.title || null,
          tags: Array.isArray(ds.tags) ? ds.tags : null,
          started_at: started_at || null,
          duration_seconds: duration_seconds ? Number(duration_seconds) : null,
          latitude: latitude ? Number(latitude) : null,
          longitude: longitude ? Number(longitude) : null,
          accuracy: accuracy ? Number(accuracy) : null,
          audio_url: audio_url || null,
        });
      } catch (e) {}
    }
    const metaHtml = formatMeta({ started_at, duration_seconds, latitude, longitude, accuracy, language: tr.language });
    const body = `${metaHtml}${summaryBlock}<h1>转录全文</h1><pre>${tr.text}</pre>`;
    res.status(200).send(htmlPage({ title: "转录结果", body }));
  } catch (err) {
    const msg = typeof err?.message === "string" ? err.message : "转录失败";
    let detail = "";
    try {
      const obj = JSON.parse(JSON.stringify(err, Object.getOwnPropertyNames(err)));
      detail = `\n\n详情：\n${JSON.stringify(obj, null, 2)}`;
    } catch {}
    res.status(500).send(htmlPage({ title: "错误", body: `<h1>转录失败</h1><pre>${msg}${detail}</pre>` }));
  } finally {
    fs.unlink(tempPath, () => {});
  }
});

app.get("/test", (req, res) => {
  const body = `
  <h1>上传测试</h1>
  <form method="post" action="/api/upload-audio" enctype="multipart/form-data">
    <div><label>OpenAI API Key：<input name="openai_key" type="password" required placeholder="sk-..."/></label></div>
    <div><label>音频文件（.m4a）：<input name="file" type="file" accept="audio/*" required/></label></div>
    <div><label>DeepSeek API Key：<input name="deepseek_key" type="password" placeholder="ds-..."/></label></div>
    <div><label>开始时间（ISO）：<input name="started_at" type="text" placeholder="2025-11-12T09:32:15+08:00"/></label></div>
    <div><label>时长（秒）：<input name="duration_seconds" type="number" step="0.1"/></label></div>
    <div><label>纬度：<input name="latitude" type="number" step="0.000001"/></label></div>
    <div><label>经度：<input name="longitude" type="number" step="0.000001"/></label></div>
    <div><label>精度（米，可选）：<input name="accuracy" type="number" step="0.1"/></label></div>
    <div style="margin-top:12px"><button type="submit">上传并转录</button></div>
  </form>
  `;
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(htmlPage({ title: "上传测试", body }));
});

const port = process.env.PORT || 3000;
const server = app.listen(port, () => {
  console.log(`Server listening on port ${port}`);
});
server.setTimeout(30 * 60 * 1000);
async function deepseekSummarize({ text, meta, apiKey, baseUrl, model }) {
  const url = `${baseUrl || "https://api.deepseek.com"}/v1/chat/completions`;
  const headers = {
    "Authorization": `Bearer ${apiKey}`,
    "Content-Type": "application/json",
  };
  const system = "你是一个信息整理助手。根据给定转录文本，用中文输出摘要、标题以及多主题标签。严格输出 JSON：{\"summary\":\"...\",\"title\":\"...\",\"tags\":[\"#话题1\",\"#话题2\"]}；不要输出其它内容。标签不超过6个，简短、贴近内容。";
  const user = `转录文本：\n${text}\n\n元数据：${JSON.stringify(meta)}`;
  const body = {
    model: model || "deepseek-chat",
    messages: [
      { role: "system", content: system },
      { role: "user", content: user },
    ],
    temperature: 0.2,
  };
  const r = await fetch(url, { method: "POST", headers, body: JSON.stringify(body) });
  if (!r.ok) throw new Error(`DeepSeek 请求失败：${r.status}`);
  const data = await r.json();
  const content = data?.choices?.[0]?.message?.content ?? "";
  let parsed;
  try {
    parsed = JSON.parse(content);
  } catch {
    parsed = { summary: content, title: "", tags: [] };
  }
  return parsed;
}

function getSupabase() {
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE || process.env.SUPABASE_ANON_KEY;
  if (!url || !key) return null;
  return createClient(url, key);
}

async function saveRecordToSupabase(record) {
  const sb = getSupabase();
  if (!sb) return { skipped: true };
  const { data, error } = await sb.from("records").insert(record).select().single();
  if (error) throw new Error(error.message);
  return { id: data?.id };
}
