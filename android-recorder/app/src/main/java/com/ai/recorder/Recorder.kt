package com.ai.recorder

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        val file = File.createTempFile("recording_", ".m4a", context.cacheDir)
        outputFile = file
        val r = MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128000)
        r.setAudioSamplingRate(44100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        return true
    }

    fun stop(): File? {
        val r = recorder ?: return null
        try {
            r.stop()
        } finally {
            r.reset()
            r.release()
            recorder = null
        }
        return outputFile
    }
}
