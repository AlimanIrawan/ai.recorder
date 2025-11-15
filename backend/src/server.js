import express from "express";
import multer from "multer";
import fs from "fs";
import path from "path";
import morgan from "morgan";
import { fileURLToPath } from "url";
import OpenAI from "openai";
import { fetch } from "undici";
import { createClient } from "@supabase/supabase-js";
import { google } from "googleapis";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
app.use(morgan("dev"));
app.use((req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization");
  if (req.method === "OPTIONS") {
    res.status(200).end();
    return;
  }
  next();
});

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

function getOAuth2Client() {
  const clientId = process.env.GOOGLE_OAUTH_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  const redirectUri = process.env.GOOGLE_OAUTH_REDIRECT_URI || "https://android-recorder-backend.onrender.com/oauth2callback";
  if (!clientId || !clientSecret || !redirectUri) throw new Error("缺少 OAuth2 配置（GOOGLE_OAUTH_CLIENT_ID/SECRET/REDIRECT_URI）");
  return new google.auth.OAuth2(clientId, clientSecret, redirectUri);
}

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

function normalizeStartedAt(value) {
  if (!value) return new Date().toISOString();
  const d = new Date(value);
  if (!Number.isNaN(d.getTime())) return d.toISOString();
  const m = String(value).match(/(\d{4})年(\d{1,2})月(\d{1,2})日\s+(\d{1,2}):(\d{2})(?::(\d{2}))?/);
  if (m) {
    const y = Number(m[1]);
    const mo = Number(m[2]);
    const da = Number(m[3]);
    const h = Number(m[4]);
    const mi = Number(m[5]);
    const s = m[6] ? Number(m[6]) : 0;
    const dt = new Date(Date.UTC(y, mo - 1, da, h, mi, s));
    return dt.toISOString();
  }
  return new Date().toISOString();
}

function toNumberOrNull(v) {
  if (v === undefined || v === null || v === "") return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

app.get("/", (req, res) => {
  const body = `<h1>音频上传与转录</h1><div>POST 到 <code>/api/upload-audio</code> 以 <code>multipart/form-data</code> 方式上传音频与元数据。</div>`;
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(htmlPage({ title: "音频上传与转录", body }));
});

app.get("/auth/start", (req, res) => {
  res.set("Content-Type", "application/json; charset=utf-8");
  try {
    const oauth2 = getOAuth2Client();
    const scopes = ["https://www.googleapis.com/auth/drive"];
    const url = oauth2.generateAuthUrl({ access_type: "offline", prompt: "consent", scope: scopes });
    res.status(200).send(JSON.stringify({ url }));
  } catch (e) {
    res.status(500).send(JSON.stringify({ error: String(e?.message || e) }));
  }
});

app.get("/oauth2callback", async (req, res) => {
  res.set("Content-Type", "application/json; charset=utf-8");
  try {
    const code = req.query.code;
    if (!code) {
      res.status(400).send(JSON.stringify({ error: "缺少 code" }));
      return;
    }
    const oauth2 = getOAuth2Client();
    const { tokens } = await oauth2.getToken(code);
    res.status(200).send(JSON.stringify({ refresh_token: tokens.refresh_token, access_token: tokens.access_token, expiry_date: tokens.expiry_date }));
  } catch (e) {
    const msg = String(e?.message || e);
    let details = undefined;
    try {
      const obj = JSON.parse(JSON.stringify(e, Object.getOwnPropertyNames(e)));
      details = obj;
    } catch {}
    res.status(500).send(JSON.stringify({ error: msg, details }));
  }
});

app.post("/api/upload-audio", upload.single("file"), async (req, res) => {
  res.set("Content-Type", "text/html; charset=utf-8");
  const file = req.file;
  const { started_at, duration_seconds, latitude, longitude, accuracy, timezone, device_model, openai_key, deepseek_key } = req.body ?? {};
  console.log("/api/upload-audio", {
    contentType: req.headers["content-type"],
    hasFile: !!file,
    started_at,
    latitude,
    longitude,
  });
  if (!file) {
    res.status(400).send(htmlPage({ title: "错误", body: `<h1>缺少音频文件</h1>` }));
    return;
  }
  try {
    const tr = await transcribeFile(file.path, openai_key);
    let summaryBlock = "";
    const deepseekKey = deepseek_key || process.env.DEEPSEEK_API_KEY;
    if (!deepseekKey) throw new Error("缺少 DeepSeek API Key（请在环境变量设置 DEEPSEEK_API_KEY）");
    const ds = await deepseekSummarize({
      text: tr.text,
      meta: { started_at, duration_seconds, latitude, longitude, accuracy },
      apiKey: deepseekKey,
      baseUrl: process.env.DEEPSEEK_BASE_URL,
      model: process.env.DEEPSEEK_MODEL,
    });
    const tagsLine = Array.isArray(ds.tags) ? ds.tags.join(" ") : "";
    let sectionsHtml = "";
    if (Array.isArray(ds.sections) && ds.sections.length > 0) {
      sectionsHtml = ds.sections
        .map((sec) => {
          const bullets = Array.isArray(sec?.bullets) ? sec.bullets.map((b) => `<li>${b}</li>`).join("") : "";
          return `<h2 style=\"font-size:16px;margin:12px 0 4px\">${sec?.heading || "要点"}</h2><ul>${bullets}</ul>`;
        })
        .join("");
    }
    summaryBlock = `<h1>摘要与标题</h1><div><strong>标题：</strong>${ds.title || ""}</div><div><strong>总览：</strong><pre>${ds.summary || ""}</pre>${sectionsHtml}<div><strong>话题：</strong>${tagsLine}</div>`;
    await saveRecordToSupabase({
      text: tr.text,
      language: tr.language || null,
      summary: ds?.summary || null,
      title: ds?.title || null,
      tags: Array.isArray(ds?.tags) ? ds.tags : null,
      started_at: normalizeStartedAt(started_at),
      duration_seconds: toNumberOrNull(duration_seconds),
      latitude: toNumberOrNull(latitude),
      longitude: toNumberOrNull(longitude),
      accuracy: toNumberOrNull(accuracy),
      audio_url: null,
    });
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

app.post("/upload", upload.single("file"), async (req, res) => {
  res.set("Content-Type", "application/json; charset=utf-8");
  try {
    const file = req.file;
    const { folderId, name, mime } = req.body ?? {};
    if (!file || !folderId || !name || !mime) {
      res.status(400).send(JSON.stringify({ error: "invalid input" }));
      return;
    }
    const refreshToken = process.env.GOOGLE_OAUTH_REFRESH_TOKEN;
    if (!refreshToken) {
      res.status(500).send(JSON.stringify({ error: "missing oauth refresh token" }));
      return;
    }
    const oauth2 = getOAuth2Client();
    oauth2.setCredentials({ refresh_token: refreshToken });
    const drive = google.drive({ version: "v3", auth: oauth2 });
    const bodyStream = fs.createReadStream(file.path);
    const resp = await drive.files.create({
      requestBody: { name, parents: [folderId], mimeType: mime },
      media: { mimeType: mime, body: bodyStream },
    });
    res.status(200).send(JSON.stringify({ id: resp?.data?.id }));
  } catch (e) {
    const msg = String(e?.message || e);
    let details = undefined;
    try {
      const obj = JSON.parse(JSON.stringify(e, Object.getOwnPropertyNames(e)));
      details = obj?.response?.data ?? obj;
    } catch {}
    res.status(500).send(JSON.stringify({ error: msg, details }));
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
    const deepseekKey2 = deepseek_key || process.env.DEEPSEEK_API_KEY;
    if (!deepseekKey2) throw new Error("缺少 DeepSeek API Key（请在环境变量设置 DEEPSEEK_API_KEY）");
    const ds2 = await deepseekSummarize({
      text: tr.text,
      meta: { started_at, duration_seconds, latitude, longitude, accuracy },
      apiKey: deepseekKey2,
      baseUrl: process.env.DEEPSEEK_BASE_URL,
      model: process.env.DEEPSEEK_MODEL,
    });
    const tagsLine2 = Array.isArray(ds2.tags) ? ds2.tags.join(" ") : "";
    let sectionsHtml2 = "";
    if (Array.isArray(ds2.sections) && ds2.sections.length > 0) {
      sectionsHtml2 = ds2.sections
        .map((sec) => {
          const bullets = Array.isArray(sec?.bullets) ? sec.bullets.map((b) => `<li>${b}</li>`).join("") : "";
          return `<h2 style=\"font-size:16px;margin:12px 0 4px\">${sec?.heading || "要点"}</h2><ul>${bullets}</ul>`;
        })
        .join("");
    }
    summaryBlock = `<h1>摘要与标题</h1><div><strong>标题：</strong>${ds2.title || ""}</div><div><strong>总览：</strong><pre>${ds2.summary || ""}</pre>${sectionsHtml2}<div><strong>话题：</strong>${tagsLine2}</div>`;
    await saveRecordToSupabase({
      text: tr.text,
      language: tr.language || null,
      summary: ds2?.summary || null,
      title: ds2?.title || null,
      tags: Array.isArray(ds2?.tags) ? ds2.tags : null,
      started_at: normalizeStartedAt(started_at),
      duration_seconds: toNumberOrNull(duration_seconds),
      latitude: toNumberOrNull(latitude),
      longitude: toNumberOrNull(longitude),
      accuracy: toNumberOrNull(accuracy),
      audio_url: audio_url || null,
    });
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

app.all("/api/ping", (req, res) => {
  res.set("Content-Type", "application/json; charset=utf-8");
  res.status(200).send(JSON.stringify({ ok: true, method: req.method }));
});
app.post("/api/update-record", express.json({ limit: "1mb" }), async (req, res) => {
  res.set("Content-Type", "application/json; charset=utf-8");
  try {
    const { id, title, summary } = req.body ?? {};
    const rid = Number(id);
    if (!rid || rid <= 0) throw new Error("缺少有效 id");
    const sb = getSupabase();
    if (!sb) throw new Error("Supabase 未配置");
    const payload = {};
    if (typeof title === "string") payload.title = title;
    if (typeof summary === "string") payload.summary = summary;
    if (!Object.keys(payload).length) throw new Error("缺少可更新字段");
    const { data, error } = await sb.from("records").update(payload).eq("id", rid).select().single();
    if (error) throw new Error(error.message);
    res.status(200).send(JSON.stringify({ ok: true, data }));
  } catch (e) {
    res.status(400).send(JSON.stringify({ ok: false, error: String(e?.message || e) }));
  }
});
app.post("/api/diag/supabase", async (req, res) => {
  res.set("Content-Type", "application/json; charset=utf-8");
  try {
    const sb = getSupabase();
    if (!sb) throw new Error("Supabase 未配置");
    const probe = {
      text: "diag",
      language: null,
      summary: null,
      title: null,
      tags: null,
      started_at: new Date().toISOString(),
      duration_seconds: null,
      latitude: null,
      longitude: null,
      accuracy: null,
      audio_url: null,
    };
    const { data, error } = await sb.from("records").insert(probe).select().single();
    if (error) throw new Error(error.message);
    await sb.from("records").delete().eq("id", data.id);
    res.status(200).send(JSON.stringify({ ok: true }));
  } catch (e) {
    res.status(500).send(JSON.stringify({ ok: false, error: String(e?.message || e) }));
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
  const system = "你是一个信息整理助手。根据给定转录文本，用中文输出结构化总结。严格输出 JSON：{\"title\":\"...\",\"summary\":\"一句话总览\",\"sections\":[{\"heading\":\"部分名称\",\"bullets\":[\"要点1\",\"要点2\"]}],\"tags\":[\"#大类1\",\"#大类2\"]}；不要输出其它内容。要求：1）sections 使用列表项目，覆盖不同主题块；2）bullets 简短且信息密度高；3）tags 仅保留最重要的大类（如 #农业、#财务），数量≤3；4）避免冗长。";
  const user = `转录文本：\n${text}\n\n元数据（可能为空）：${JSON.stringify(meta)}`;
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
    parsed = { summary: content, title: "", tags: [], sections: [] };
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
  let { data, error } = await sb.from("records").insert(record).select().single();
  if (error && (/note_text|drive_file_id|file_name/.test(String(error.message || error)))) {
    const { note_text, drive_file_id, file_name, ...rest } = record || {};
    const r2 = await sb.from("records").insert(rest).select().single();
    if (r2.error) throw new Error(r2.error.message);
    return { id: r2.data?.id };
  }
  if (error) throw new Error(error.message);
  return { id: data?.id };
}

function getDriveContext() {
  const email = process.env.GOOGLE_SERVICE_ACCOUNT_EMAIL;
  const key = process.env.GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY;
  const folderId = process.env.GOOGLE_DRIVE_FOLDER_ID;
  if (!email || !key || !folderId) return null;
  const auth = new google.auth.JWT({ email, key: key.replace(/\\n/g, "\n"), scopes: ["https://www.googleapis.com/auth/drive.readonly"] });
  const drive = google.drive({ version: "v3", auth });
  return { drive, folderId };
}

function getDriveContextOAuth() {
  const folderId = process.env.GOOGLE_DRIVE_FOLDER_ID;
  const clientId = process.env.GOOGLE_OAUTH_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  const refreshToken = process.env.GOOGLE_OAUTH_REFRESH_TOKEN;
  const redirectUri = process.env.GOOGLE_OAUTH_REDIRECT_URI || "https://android-recorder-backend.onrender.com/oauth2callback";
  if (!folderId || !clientId || !clientSecret || !refreshToken) return null;
  const oauth2 = new google.auth.OAuth2(clientId, clientSecret, redirectUri);
  oauth2.setCredentials({ refresh_token: refreshToken });
  const drive = google.drive({ version: "v3", auth: oauth2 });
  return { drive, folderId };
}

function isAudioFile(f) {
  const mt = String(f?.mimeType || "");
  if (mt.startsWith("audio/")) return true;
  const ext = path.extname(String(f?.name || "")).toLowerCase();
  return [".m4a", ".mp3", ".wav", ".aac", ".flac", ".caf", ".m4r", ".ogg"].includes(ext);
}

async function listDriveAudio(drive, folderId, pageSize) {
  const q = `'${folderId}' in parents and trashed = false`;
  const fields = "files(id,name,mimeType,createdTime,modifiedTime,size),nextPageToken";
  const r = await drive.files.list({ q, fields, orderBy: "createdTime desc", pageSize: pageSize || 10 });
  const files = Array.isArray(r?.data?.files) ? r.data.files : [];
  return files.filter(isAudioFile);
}

async function existsDriveRecord(fileId) {
  const sb = getSupabase();
  if (!sb) return false;
  const { data, error } = await sb.from("records").select("id").eq("audio_url", `drive:${fileId}`).limit(1);
  if (error) throw new Error(error.message);
  return Array.isArray(data) && data.length > 0;
}

async function downloadDriveFile(drive, fileId, fileName) {
  const ext = path.extname(fileName || "") || ".m4a";
  const tempPath = path.join(uploadsDir, `${Date.now()}-${Math.random().toString(36).slice(2)}${ext}`);
  const resp = await drive.files.get({ fileId, alt: "media" }, { responseType: "stream" });
  await new Promise((resolve, reject) => {
    const ws = fs.createWriteStream(tempPath);
    resp.data.pipe(ws);
    resp.data.on("error", reject);
    ws.on("finish", resolve);
    ws.on("error", reject);
  });
  return tempPath;
}

async function processDriveFile(ctx, file) {
  const deepseekKey = process.env.DEEPSEEK_API_KEY;
  if (!deepseekKey) throw new Error("缺少 DeepSeek API Key（请在环境变量设置 DEEPSEEK_API_KEY）");
  const done = await existsDriveRecord(file.id);
  if (done) return { skipped: true, id: file.id };
  const temp = await downloadDriveFile(ctx.drive, file.id, file.name);
  try {
    const tr = await transcribeFile(temp);
    const ds = await deepseekSummarize({ text: tr.text, meta: { started_at: file.createdTime }, apiKey: deepseekKey, baseUrl: process.env.DEEPSEEK_BASE_URL, model: process.env.DEEPSEEK_MODEL });
    await saveRecordToSupabase({ text: tr.text, language: tr.language || null, summary: ds?.summary || null, title: ds?.title || null, tags: Array.isArray(ds?.tags) ? ds.tags : null, started_at: normalizeStartedAt(file.createdTime), duration_seconds: null, latitude: null, longitude: null, accuracy: null, audio_url: `drive:${file.id}` });
    return { skipped: false, id: file.id };
  } finally {
    fs.unlink(temp, () => {});
  }
}

// 增强版：同时读取同名 .txt 记录并合并到摘要/标题中
async function processDriveFileWithNote(ctx, file) {
  const deepseekKey = process.env.DEEPSEEK_API_KEY;
  if (!deepseekKey) throw new Error("缺少 DeepSeek API Key（请在环境变量设置 DEEPSEEK_API_KEY）");
  const done = await existsDriveRecord(file.id);
  if (done) return { skipped: true, id: file.id };
  const temp = await downloadDriveFile(ctx.drive, file.id, file.name);
  const base = String(file.name || "").replace(/\.[^./]+$/, "");
  let noteText = null;
  try {
    const q = `'${ctx.folderId}' in parents and trashed = false and name = '${base}.txt'`;
    const r = await ctx.drive.files.list({ q, fields: "files(id,name,mimeType,createdTime)", pageSize: 1 });
    const txtFile = Array.isArray(r?.data?.files) ? r.data.files[0] : null;
    if (txtFile?.id) {
      const streamResp = await ctx.drive.files.get({ fileId: txtFile.id, alt: "media" }, { responseType: "stream" });
      noteText = await new Promise((resolve, reject) => {
        let acc = "";
        streamResp.data.on("data", (chunk) => {
          acc += chunk.toString("utf-8");
        });
        streamResp.data.on("end", () => resolve(acc));
        streamResp.data.on("error", reject);
      });
      if (typeof noteText === "string") noteText = noteText.trim();
    }
  } catch {}
  try {
    const tr = await transcribeFile(temp);
    const ds = await deepseekSummarize({ text: tr.text, meta: { started_at: file.createdTime, note_text: noteText || null }, apiKey: deepseekKey, baseUrl: process.env.DEEPSEEK_BASE_URL, model: process.env.DEEPSEEK_MODEL });
    let titleOut = ds?.title || null;
    if (!titleOut && noteText) {
      const firstLine = noteText.split(/\r?\n/)[0]?.trim();
      if (firstLine) titleOut = firstLine;
    }
    await saveRecordToSupabase({ text: tr.text, language: tr.language || null, summary: ds?.summary || null, title: titleOut, tags: Array.isArray(ds?.tags) ? ds.tags : null, started_at: normalizeStartedAt(file.createdTime), duration_seconds: null, latitude: null, longitude: null, accuracy: null, audio_url: `drive:${file.id}`, note_text: noteText || null, drive_file_id: file.id, file_name: file.name });
    return { skipped: false, id: file.id };
  } finally {
    fs.unlink(temp, () => {});
  }
}

async function syncDriveRun() {
  const ctx = getDriveContext() || getDriveContextOAuth();
  if (!ctx) throw new Error("缺少 Google Drive 配置（服务账号或 OAuth 刷新令牌 + GOOGLE_DRIVE_FOLDER_ID）");
  const limit = Number(process.env.DRIVE_SYNC_MAX_PER_RUN || 3);
  const pageSize = Number(process.env.DRIVE_SYNC_PAGE_SIZE || 10);
  const files = await listDriveAudio(ctx.drive, ctx.folderId, pageSize);
  const targets = files.slice(0, limit);
  const results = [];
  for (const f of targets) {
    try {
      const r = await processDriveFileWithNote(ctx, f);
      results.push({ id: f.id, name: f.name, ok: !r.skipped, skipped: !!r.skipped });
    } catch (e) {
      results.push({ id: f.id, name: f.name, ok: false, error: String(e?.message || e) });
    }
  }
  return { count: results.filter((x) => x.ok).length, processed: results };
}

app.post("/api/sync-drive", async (req, res) => {
  res.set("Content-Type", "application/json; charset=utf-8");
  try {
    const r = await syncDriveRun();
    res.status(200).send(JSON.stringify({ ok: true, ...r }));
  } catch (e) {
    res.status(400).send(JSON.stringify({ ok: false, error: String(e?.message || e) }));
  }
});

let syncLock = false;
const intervalMin = Number(process.env.DRIVE_SYNC_INTERVAL_MINUTES || 0);
if (intervalMin > 0) {
  setInterval(async () => {
    if (syncLock) return;
    syncLock = true;
    try {
      await syncDriveRun();
    } catch {}
    syncLock = false;
  }, intervalMin * 60 * 1000);
}
