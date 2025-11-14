const express = require('express')
const cors = require('cors')
const multer = require('multer')
const { google } = require('googleapis')
const stream = require('stream')

const app = express()
app.use(cors())
app.use(express.json())

const upload = multer({ storage: multer.memoryStorage() })

function getJwtClient() {
  let clientEmail, privateKey
  const json = process.env.GOOGLE_SERVICE_ACCOUNT_JSON
  if (json) {
    const o = JSON.parse(json)
    clientEmail = o.client_email
    privateKey = o.private_key
  } else {
    clientEmail = process.env.GOOGLE_CLIENT_EMAIL
    privateKey = process.env.GOOGLE_PRIVATE_KEY
  }
  if (!clientEmail || !privateKey) throw new Error('missing service account credentials')
  const scopes = ['https://www.googleapis.com/auth/drive']
  return new google.auth.JWT(clientEmail, null, privateKey, scopes)
}

app.get('/', (req, res) => { res.json({ ok: true }) })

app.post('/upload', upload.single('file'), async (req, res) => {
  try {
    const { folderId, name, mime } = req.body
    const file = req.file
    if (!folderId || !name || !mime || !file) return res.status(400).json({ error: 'invalid input' })
    const auth = getJwtClient()
    await auth.authorize()
    const drive = google.drive({ version: 'v3', auth })
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
