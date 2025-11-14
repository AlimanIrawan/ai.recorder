package com.ai.recorder.sync

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import javax.net.ssl.HttpsURLConnection
import java.net.URL

class DriveAuth(private val context: Context) {
    fun getAccessToken(): String {
        val sa = readServiceAccount()
        val now = System.currentTimeMillis() / 1000
        val exp = now + 3600
        val header = base64UrlEncode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}")
        val claim = "{" +
                "\"iss\":\"${sa.email}\"," +
                "\"scope\":\"https://www.googleapis.com/auth/drive\"," +
                "\"aud\":\"https://oauth2.googleapis.com/token\"," +
                "\"iat\":$now," +
                "\"exp\":$exp" +
                "}"
        val payload = base64UrlEncode(claim)
        val signingInput = "$header.$payload"
        val signatureBytes = sign(signingInput.toByteArray(), sa.privateKey)
        val assertion = "$signingInput.${base64UrlEncode(signatureBytes)}"
        val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${assertion}"
        val url = URL("https://oauth2.googleapis.com/token")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            Log.e("DriveAuth", "token error code=$code body=$err")
            throw RuntimeException("token error $code")
        }
        val resp = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        Log.d("DriveAuth", "token ok")
        val json = JSONObject(resp)
        return json.getString("access_token")
    }

    private fun readServiceAccount(): ServiceAccountInfo {
        val isr: InputStream = context.assets.open("sa.json")
        val txt = isr.readBytes().toString(Charsets.UTF_8)
        val obj = JSONObject(txt)
        val email = obj.getString("client_email")
        val pk = obj.getString("private_key")
        val privateKey = parsePk(pk)
        Log.d("DriveAuth", "loaded service account: $email")
        return ServiceAccountInfo(email, privateKey)
    }

    private fun parsePk(pkPem: String): PrivateKey {
        val clean = pkPem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
        val bytes = Base64.getDecoder().decode(clean)
        val spec = PKCS8EncodedKeySpec(bytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }

    private fun sign(data: ByteArray, key: PrivateKey): ByteArray {
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(key)
        sig.update(data)
        return sig.sign()
    }

    private fun base64UrlEncode(b: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    private fun base64UrlEncode(s: String): String = base64UrlEncode(s.toByteArray())

    data class ServiceAccountInfo(val email: String, val privateKey: PrivateKey)
}
