#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <cstring>
#include <android/log.h>
#include <unistd.h>
#include "whisper.h"

#define LOG_TAG "WhisperBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void progress_cb(struct whisper_context *, struct whisper_state *, int, void *);

static bool read_wav_16le(const std::string & path, std::vector<float> & pcmf32, int & sampleRateOut) {
    std::ifstream f(path, std::ios::binary);
    if (!f.good()) return false;
    char riff[4]; f.read(riff, 4); if (std::strncmp(riff, "RIFF", 4) != 0) return false;
    f.seekg(22, std::ios::beg); // channels
    uint16_t channels = 0; f.read(reinterpret_cast<char*>(&channels), 2);
    uint32_t sampleRate = 0; f.read(reinterpret_cast<char*>(&sampleRate), 4);
    sampleRateOut = (int) sampleRate;
    f.seekg(34, std::ios::beg); // bits per sample
    uint16_t bps = 0; f.read(reinterpret_cast<char*>(&bps), 2);
    if (bps != 16) return false;
    // find 'data' chunk
    f.seekg(36, std::ios::beg);
    char tag[4]; uint32_t size = 0;
    while (f.read(tag, 4)) {
        f.read(reinterpret_cast<char*>(&size), 4);
        if (std::strncmp(tag, "data", 4) == 0) break; else f.seekg(size, std::ios::cur);
    }
    if (size == 0) return false;
    std::vector<int16_t> pcm16(size/2);
    f.read(reinterpret_cast<char*>(pcm16.data()), size);
    pcmf32.resize(pcm16.size());
    for (size_t i = 0; i < pcm16.size(); ++i) pcmf32[i] = pcm16[i] / 32768.0f;
    return true;
}

static void resample_linear(const std::vector<float> & in, int src_sr, int dst_sr, std::vector<float> & out) {
    if (src_sr == dst_sr) { out = in; return; }
    const double ratio = (double) dst_sr / (double) src_sr;
    const size_t out_n = (size_t) std::max(1.0, std::floor(in.size() * ratio));
    out.resize(out_n);
    for (size_t i = 0; i < out_n; ++i) {
        double src_pos = i / ratio;
        size_t i0 = (size_t) std::floor(src_pos);
        size_t i1 = std::min(i0 + 1, in.size() - 1);
        double t = src_pos - i0;
        float v = (float) ((1.0 - t) * in[i0] + t * in[i1]);
        out[i] = v;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ai_recorder_engine_WhisperBridge_nativeTranscribe(JNIEnv *env, jclass,
                                                           jstring jAudioPath,
                                                           jstring jModelPath,
                                                           jstring jLang) {
    const char *audioPath = env->GetStringUTFChars(jAudioPath, nullptr);
    const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char *lang = env->GetStringUTFChars(jLang, nullptr);
    LOGI("nativeTranscribe start audio=%s model=%s lang=%s", audioPath, modelPath, lang);

    std::vector<float> pcmf32;
    int src_sr = 0;
    if (!read_wav_16le(audioPath, pcmf32, src_sr)) {
        LOGE("read_wav_16le failed");
        env->ReleaseStringUTFChars(jAudioPath, audioPath);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jLang, lang);
        return env->NewStringUTF("");
    }
    std::vector<float> pcm16k;
    resample_linear(pcmf32, src_sr, 16000, pcm16k);

    whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context * ctx = whisper_init_from_file_with_params(modelPath, cparams);
    if (!ctx) {
        LOGE("whisper_init_from_file_with_params failed");
        env->ReleaseStringUTFChars(jAudioPath, audioPath);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jLang, lang);
        return env->NewStringUTF("");
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false; wparams.print_realtime = false; wparams.print_timestamps = false;
    wparams.translate = false; wparams.no_context = true; wparams.single_segment = false; wparams.temperature = 0.2f;
    wparams.speed_up = true;
    int cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    if (cores < 1) cores = 1;
    wparams.n_threads = cores;
    wparams.progress_callback = progress_cb;
    wparams.progress_callback_user_data = nullptr;
    if (std::strlen(lang) > 0) { wparams.language = lang; wparams.detect_language = false; } else { wparams.detect_language = true; }
    LOGI("whisper_full samples=%zu threads=%d", pcm16k.size(), wparams.n_threads);
    if (whisper_full(ctx, wparams, pcm16k.data(), pcm16k.size()) != 0) {
        LOGE("whisper_full failed");
        whisper_free(ctx);
        env->ReleaseStringUTFChars(jAudioPath, audioPath);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jLang, lang);
        return env->NewStringUTF("");
    }

    std::string text;
    int n = whisper_full_n_segments(ctx);
    for (int i = 0; i < n; ++i) {
        const char * seg = whisper_full_get_segment_text(ctx, i);
        text += seg;
    }
    whisper_free(ctx);
    LOGI("nativeTranscribe done len=%zu", text.size());

    env->ReleaseStringUTFChars(jAudioPath, audioPath);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    env->ReleaseStringUTFChars(jLang, lang);
    return env->NewStringUTF(text.c_str());
}
static void progress_cb(struct whisper_context *, struct whisper_state *, int progress, void *) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "progress=%d%%", progress);
}
