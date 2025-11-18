const express = require('express')
const cors = require('cors')
const multer = require('multer')
const OpenAI = require('openai')
const { toFile } = require('openai/uploads')
const axios = require('axios')

const app = express()
app.use(cors())
app.use(express.json())

const upload = multer({ storage: multer.memoryStorage() })

function getOpenAI() {
  const key = process.env.OPENAI_API_KEY
  const baseURL = process.env.OPENAI_BASE_URL || 'https://api.openai.com'
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
app.post('/api/transcribe', upload.single('file'), async (req, res) => {
  try {
    const file = req.file
    if (!file) return res.status(400).json({ error: 'file missing' })
    console.log('/api/transcribe', { hasFile: !!file, name: file.originalname, size: file.size })
    const client = getOpenAI()
    const ofile = await toFile(file.buffer, file.originalname || 'audio.m4a')
    const model = req.body.model || process.env.OPENAI_WHISPER_MODEL || 'whisper-1'
    const tr = await client.audio.transcriptions.create({ file: ofile, model })
    res.json({ text: tr.text || '' })
  } catch (e) {
    const code = e.status || 500
    res.status(code).json({ error: e.message })
  }
})

// 2) 转录 + 总结
app.post('/api/transcribe-and-summarize', upload.single('file'), async (req, res) => {
  try {
    const file = req.file
    if (!file) return res.status(400).json({ error: 'file missing' })
    console.log('/api/transcribe-and-summarize', { hasFile: !!file, name: file.originalname, size: file.size })
    const client = getOpenAI()
    const ofile = await toFile(file.buffer, file.originalname || 'audio.m4a')
    const model = req.body.model || process.env.OPENAI_WHISPER_MODEL || 'whisper-1'
    const tr = await client.audio.transcriptions.create({ file: ofile, model })
    const text = tr.text || ''
    let title = ''
    let summary = ''
    try {
      const s = await deepseekSummarize(text)
      title = s.title
      summary = s.summary
    } catch (_) {}
    res.json({ text, title, summary })
  } catch (e) {
    const code = e.status || 500
    res.status(code).json({ error: e.message })
  }
})

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
