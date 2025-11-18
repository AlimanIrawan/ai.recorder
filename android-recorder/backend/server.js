const express = require('express')
const cors = require('cors')
const multer = require('multer')
const OpenAI = require('openai')
const { toFile } = require('openai/uploads')
const axios = require('axios')
const fs = require('fs')
const os = require('os')
const path = require('path')
const ffmpeg = require('ffmpeg-static')
const { spawn } = require('child_process')
const crypto = require('crypto')

const app = express()
app.use(cors())
app.use(express.json())
app.use(express.urlencoded({ extended: true }))

const upload = multer({ storage: multer.memoryStorage() })

function getOpenAI() {
  const key = process.env.OPENAI_API_KEY
  let baseURL = process.env.OPENAI_BASE_URL || 'https://api.openai.com/v1'
  baseURL = String(baseURL).replace(/`/g, '').trim()
  if (!/\/v1\/?$/.test(baseURL)) baseURL = baseURL.replace(/\/?$/, '/v1')
  if (!key) throw new Error('OPENAI_API_KEY missing')
  return new OpenAI({ apiKey: key, baseURL })
}

async function deepseekSummarize(text) {
  const key = process.env.DEEPSEEK_API_KEY
  const base = process.env.DEEPSEEK_BASE_URL || 'https://api.deepseek.com'
  const model = process.env.DEEPSEEK_MODEL || 'deepseek-chat'
  if (!key) throw new Error('DEEPSEEK_API_KEY missing')
  const url = `${base}/chat/completions`
  const payload = {
    model,
    messages: [
      { role: 'system', content: '你是一个会议记录助手。请从提供的转录文本中生成一个简洁的标题和详细的会议总结。以JSON返回：{"title":"...","summary":"..."}。' },
      { role: 'user', content: text }
    ]
  }
  const resp = await axios.post(url, payload, { headers: { Authorization: `Bearer ${key}` } })
  const content = resp.data?.choices?.[0]?.message?.content || ''
  try {
    const obj = JSON.parse(content)
    return { title: obj.title || '', summary: obj.summary || content }
  } catch (_) {
    return { title: '', summary: content }
  }
}

app.get('/api/ping', (req, res) => { res.json({ ok: true }) })

// 1) 仅转录
const transcribeHandler = async (req, res) => {
  try {
    const file = req.file
    if (!file) return res.status(400).json({ error: 'file missing' })
    console.log('/api/transcribe', { hasFile: !!file, name: file.originalname, size: file.size, mime: file.mimetype })
    if (!looksLikeAudio(file)) {
      return res.status(400).json({ error: 'unsupported media type: please upload audio file (.m4a/.mp3/.wav/...)' })
    }
    const client = getOpenAI()
    const model = req.body.model || process.env.OPENAI_WHISPER_MODEL || 'whisper-1'
    console.log('openai transcribe', { baseURL: client.baseURL, model })
    const prepared = await prepareParts(file.buffer, file.originalname || 'audio.m4a')
    try {
      const texts = []
      for (const p of prepared.parts) {
        const buf = fs.readFileSync(p)
        const up = await toFile(buf, path.basename(p))
        const tr = await client.audio.transcriptions.create({ file: up, model })
        texts.push(tr.text || '')
      }
      const out = (texts.join('\n') || '').trim()
      if (!out) return res.status(422).json({ error: 'empty_transcription' })
      res.json({ text: out })
    } finally {
      cleanupDir(prepared.tmpDir)
    }
  } catch (e) {
    const code = e.status || 500
    let details = undefined
    try {
      const obj = JSON.parse(JSON.stringify(e, Object.getOwnPropertyNames(e)))
      details = obj?.response?.data ?? obj
    } catch {}
    console.error('/api/transcribe error', e)
    res.status(code).json({ error: e.message, details })
  }
}
app.post('/api/transcribe', upload.single('file'), transcribeHandler)
app.post('/api/transcribe/', upload.single('file'), transcribeHandler)
app.post('/transcribe', upload.single('file'), transcribeHandler)
app.post('/transcribe/', upload.single('file'), transcribeHandler)

// 2) 转录 + 总结
const transcribeAndSummarizeHandler = async (req, res) => {
  try {
    const file = req.file
    if (!file) return res.status(400).json({ error: 'file missing' })
    console.log('/api/transcribe-and-summarize', { hasFile: !!file, name: file.originalname, size: file.size, mime: file.mimetype })
    if (!looksLikeAudio(file)) {
      return res.status(400).json({ error: 'unsupported media type: please upload audio file (.m4a/.mp3/.wav/...)' })
    }
    const maxSync = 8 * 1024 * 1024
    if (file.buffer.length <= maxSync) {
      const client = getOpenAI()
      const ofile = await toFile(file.buffer, file.originalname || 'audio.m4a')
      const model = req.body.model || process.env.OPENAI_WHISPER_MODEL || 'whisper-1'
      console.log('openai transcribe+summarize', { baseURL: client.baseURL, model })
      const tr = await client.audio.transcriptions.create({ file: ofile, model })
      const text = (tr.text || '').trim()
      if (!text) return res.status(422).json({ error: 'empty_transcription' })
      let title = ''
      let summary = ''
      try {
        const s = await deepseekSummarize(text)
        title = s.title
        summary = s.summary
      } catch (_) {}
      return res.json({ text, title, summary })
    }
    const id = crypto.randomUUID()
    jobs.set(id, { status: 'pending', progress: 0, text: '', title: '', summary: '', error: null })
    setImmediate(() => { processJob(id, file.buffer, file.originalname).catch(() => {}) })
    res.status(202).json({ jobId: id })
  } catch (e) {
    const code = e.status || 500
    let details = undefined
    try {
      const obj = JSON.parse(JSON.stringify(e, Object.getOwnPropertyNames(e)))
      details = obj?.response?.data ?? obj
    } catch {}
    console.error('/api/transcribe-and-summarize error', e)
    res.status(code).json({ error: e.message, details })
  }
}
app.post('/api/transcribe-and-summarize', upload.single('file'), transcribeAndSummarizeHandler)
app.post('/api/transcribe-and-summarize/', upload.single('file'), transcribeAndSummarizeHandler)
app.post('/transcribe-and-summarize', upload.single('file'), transcribeAndSummarizeHandler)
app.post('/transcribe-and-summarize/', upload.single('file'), transcribeAndSummarizeHandler)

// 3) 单独总结
app.post('/api/summarize', async (req, res) => {
  try {
    const { text } = req.body || {}
    if (!text || !text.trim()) return res.status(400).json({ error: 'text missing' })
    const s = await deepseekSummarize(text)
    res.json(s)
  } catch (e) {
    const code = e.status || 500
    res.status(code).json({ error: e.message })
  }
})

const port = process.env.PORT || 3000
app.listen(port, () => {})
app.get('/api/transcribe', (req, res) => {
  res.status(405).json({ error: 'use POST' })
})
app.get('/api/transcribe-and-summarize', (req, res) => {
  res.status(405).json({ error: 'use POST' })
})
app.get('/api/jobs/:id', (req, res) => {
  const j = jobs.get(req.params.id)
  if (!j) return res.status(404).json({ error: 'not found' })
  res.json({ id: req.params.id, status: j.status, progress: j.progress, text: j.text, title: j.title, summary: j.summary, error: j.error })
})
function looksLikeAudio(file) {
  if (!file) return false
  const name = String(file.originalname || '').toLowerCase()
  const mime = String(file.mimetype || '')
  if (mime.startsWith('audio/')) return true
  if (/aiff/i.test(mime) || /x-aiff/i.test(mime)) return true
  if (mime === 'application/octet-stream') return true
  return /\.(m4a|mp3|wav|aac|flac|ogg|webm|caf|aiff)$/i.test(name)
}

app.use((req, res) => {
  console.warn('404', req.method, req.path)
  res.status(404).json({ error: 'not found' })
})

async function run(cmd, args) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: 'ignore' })
    p.on('exit', (code) => {
      if (code === 0) resolve()
      else reject(new Error('ffmpeg_error_' + code))
    })
    p.on('error', (err) => reject(err))
  })
}

async function prepareParts(buffer, originalname) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rec-'))
  const inPath = path.join(dir, 'in' + Date.now() + (path.extname(originalname || '') || '.m4a'))
  fs.writeFileSync(inPath, buffer)
  console.log('prepareParts', { inputBytes: buffer.length })
  const max = 24 * 1024 * 1024
  if (buffer.length <= max) return { parts: [inPath], tmpDir: dir }
  const conv = path.join(dir, 'conv_' + Date.now() + '.mp3')
  await run(ffmpeg, ['-i', inPath, '-vn', '-map', '0:a:0', '-ar', '16000', '-ac', '1', '-b:a', '48k', conv])
  try { console.log('converted', { path: conv, size: fs.statSync(conv).size }) } catch {}
  const segDir = path.join(dir, 'seg')
  fs.mkdirSync(segDir)
  const pat = path.join(segDir, 'part_%03d.mp3')
  await run(ffmpeg, ['-i', conv, '-f', 'segment', '-segment_time', '600', '-reset_timestamps', '1', pat])
  const files = fs.readdirSync(segDir).map((n) => path.join(segDir, n)).sort()
  try { console.log('segments', files.map((p) => ({ path: p, size: fs.statSync(p).size }))) } catch {}
  const parts = files.filter((p) => fs.statSync(p).size <= max)
  if (!parts.length) parts.push(conv)
  return { parts, tmpDir: dir }
}

function cleanupDir(dir) {
  try {
    fs.rmSync(dir, { recursive: true, force: true })
  } catch {}
}

const jobs = new Map()

async function processJob(id, buffer, originalname) {
  const client = getOpenAI()
  const model = process.env.OPENAI_WHISPER_MODEL || 'whisper-1'
  const prepared = await prepareParts(buffer, originalname || 'audio.m4a')
  const j = jobs.get(id)
  j.status = 'processing'
  try {
    const texts = []
    let idx = 0
    for (const p of prepared.parts) {
      const buf = fs.readFileSync(p)
      try { console.log('upload_part', { idx, size: buf.length, name: path.basename(p) }) } catch {}
      const up = await toFile(buf, path.basename(p))
      const tr = await client.audio.transcriptions.create({ file: up, model })
      try { console.log('part_text_len', { idx, len: (tr.text || '').length }) } catch {}
      texts.push(tr.text || '')
      idx += 1
      j.progress = Math.round((idx / prepared.parts.length) * 100)
    }
    const text = (texts.join('\n') || '').trim()
    if (!text) {
      j.status = 'error'
      j.error = 'empty_transcription'
      j.progress = 100
      return
    }
    j.text = text
    try {
      const s = await deepseekSummarize(text)
      j.title = s.title
      j.summary = s.summary
    } catch {}
    j.status = 'done'
    j.progress = 100
  } catch (e) {
    j.status = 'error'
    j.error = e.message
  } finally {
    cleanupDir(prepared.tmpDir)
  }
}

process.on('unhandledRejection', (err) => {
  console.error('unhandledRejection', err)
})
process.on('uncaughtException', (err) => {
  console.error('uncaughtException', err)
})
