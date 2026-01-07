package com.kingsun.plugins.speechandtext;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "SpeechAndText")
public class SpeechAndTextPlugin extends Plugin {

    private static final String TAG = "SpeechAndTextPlugin";
    private final SpeechAndText implementation = new SpeechAndText();

    private SpeechToText stt = null;
    private TextToSpeech tts = null;

    private ExecutorService ttsExecutor;
    private ExecutorService recordingExecutor;

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    @PluginMethod
    public void InitSTT(PluginCall call) {
        int itype = call.getInt("itype", 21);
        String rootDir = call.getString("rootDir", ""); // NEW: /files/models/stt

        try {
            if (stt == null)
                stt = new SpeechToText();
            stt.initModel(itype, getContext(), rootDir);
            JSObject ret = new JSObject();
            ret.put("value", "Init STT Success!");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to initialize STT: " + e.getMessage());
        }
    }

    @PluginMethod
    public void DestroySTT(PluginCall call) {
        try {
            if (stt != null) {
                stt.onDestroy();
                stt = null;
            }
            call.resolve(new JSObject().put("value", "Destroy STT Success!"));
        } catch (Exception e) {
            call.reject("Failed to destroy stt: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startRecording(PluginCall call) {
        if (stt == null) {
            call.reject("STT not initialized");
            return;
        }
        if (stt.isRecording()) {
            call.reject("Already recording");
            return;
        }
        if (!stt.initMicrophone(getContext(), getActivity())) {
            call.reject("Failed to initialize microphone");
            return;
        }

        try {
            stt.startRecording();

            SpeechToText.RecognizerCallback callback = (text, isEndpoint) -> {
                JSObject result = new JSObject();
                result.put("text", text);
                result.put("isEndpoint", isEndpoint);
                notifyListeners("onRecognizerResult", result);
            };

            recordingExecutor = Executors.newSingleThreadExecutor();
            recordingExecutor.execute(() -> {
                try {
                    stt.processSamples(callback);
                } catch (Exception e) {
                    // 录音线程内不要 call.reject（call 可能已 resolve）
                    JSObject err = new JSObject();
                    err.put("error", "Recognizer failed: " + e.getMessage());
                    notifyListeners("onRecognizerError", err);
                }
            });

            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to start recording: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopRecording(PluginCall call) {
        if (stt == null) {
            call.reject("STT not initialized");
            return;
        }
        if (!stt.isRecording()) {
            call.reject("Not recording");
            return;
        }
        try {
            if (recordingExecutor != null) {
                recordingExecutor.shutdownNow();
                recordingExecutor = null;
            }
            stt.stopRecording();
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to stop recording: " + e.getMessage());
        }
    }

    @PluginMethod
    public void checkPermission(PluginCall call) {
        if (stt == null) {
            call.resolve(new JSObject().put("hasPermission", false));
            return;
        }
        boolean hasPermission = stt.checkMicrophonePermission(getContext());
        call.resolve(new JSObject().put("hasPermission", hasPermission));
    }

    @PluginMethod
    public void InitTTS(PluginCall call) {
        int itype = call.getInt("itype", 0);
        String rootDir = call.getString("rootDir", ""); // NEW: /files/models/tts

        try {
            if (tts != null) {
                tts.onDestroy();
                tts = null;
            }
            tts = new TextToSpeech();
            tts.initTTS(itype, getContext(), rootDir);

            call.resolve(new JSObject().put("value", "Init TTS Success!"));
        } catch (Exception e) {
            call.reject("Failed to initialize TTS: " + e.getMessage());
        }
    }

    @PluginMethod
    public void generateSpeech(PluginCall call) {
        String text = call.getString("text");
        String wavName = call.getString("wavName");
        int sid = call.getInt("sid", 0);
        float speed = call.getFloat("speed", 1.0f);

        if (text == null || text.trim().isEmpty()) {
            call.reject("Text cannot be empty");
            return;
        }
        if (wavName == null || wavName.trim().isEmpty()) {
            wavName = UUID.randomUUID().toString();
        }
        if (tts == null) {
            call.reject("TTS not initialized");
            return;
        }

        if (ttsExecutor != null)
            ttsExecutor.shutdownNow();
        ttsExecutor = Executors.newSingleThreadExecutor();

        final String finalWavName = wavName;
        ttsExecutor.execute(() -> {
            try {
                JSObject result = tts.generateSpeech(text, finalWavName, sid, speed, getContext());
                if (result != null) {
                    notifyListeners("onGenerationComplete", result);
                } else {
                    JSObject err = new JSObject();
                    err.put("error", "Generation failed: empty result");
                    notifyListeners("onGenerationError", err);
                }
            } catch (Exception e) {
                JSObject err = new JSObject();
                err.put("error", "Generation failed: " + e.getMessage());
                notifyListeners("onGenerationError", err);
            }
        });

        // 立即 resolve，结果通过事件回调
        call.resolve(new JSObject().put("value", "Generation started"));
    }
}
