const express = require('express')
const cors = require('cors')
const multer = require('multer')
const { google } = require('googleapis')
const stream = require('stream')

const app = express()
app.use(cors())
app.use(express.json())

const upload = multer({ storage: multer.memoryStorage() })

function getOAuth2Client() {
  const clientId = process.env.GOOGLE_OAUTH_CLIENT_ID
  const clientSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET
  const redirectUri = process.env.GOOGLE_OAUTH_REDIRECT_URI || 'https://android-recorder-backend.onrender.com/oauth2callback'
  if (!clientId || !clientSecret || !redirectUri) throw new Error('missing oauth client config')
  return new google.auth.OAuth2(clientId, clientSecret, redirectUri)
}

app.get('/', (req, res) => { res.json({ ok: true }) })

app.get('/auth/start', (req, res) => {
  try {
    const oauth2 = getOAuth2Client()
    const scopes = ['https://www.googleapis.com/auth/drive']
    const url = oauth2.generateAuthUrl({ access_type: 'offline', prompt: 'consent', scope: scopes })
    res.json({ url })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

app.get('/oauth2callback', async (req, res) => {
  try {
    const code = req.query.code
    if (!code) return res.status(400).json({ error: 'missing code' })
    const oauth2 = getOAuth2Client()
    const { tokens } = await oauth2.getToken(code)
    res.json({ refresh_token: tokens.refresh_token, access_token: tokens.access_token, expiry_date: tokens.expiry_date })
  } catch (e) {
    const code = e.code || 500
    const details = e.response && e.response.data ? e.response.data : undefined
    res.status(code).json({ error: e.message, details })
  }
})

app.post('/upload', upload.single('file'), async (req, res) => {
  try {
    const { folderId, name, mime } = req.body
    const file = req.file
    if (!folderId || !name || !mime || !file) return res.status(400).json({ error: 'invalid input' })
    const refreshToken = process.env.GOOGLE_OAUTH_REFRESH_TOKEN
    if (!refreshToken) return res.status(500).json({ error: 'missing oauth refresh token' })
    const oauth2 = getOAuth2Client()
    oauth2.setCredentials({ refresh_token: refreshToken })
    const drive = google.drive({ version: 'v3', auth: oauth2 })
    const body = stream.Readable.from(file.buffer)
    const resp = await drive.files.create({
      requestBody: { name, parents: [folderId], mimeType: mime },
      media: { mimeType: mime, body }
    })
    res.json({ id: resp.data.id })
  } catch (e) {
    const code = e.code || 500
    const details = e.response && e.response.data ? e.response.data : undefined
    res.status(code).json({ error: e.message, details })
  }
})

const port = process.env.PORT || 3000
app.listen(port, () => {})
