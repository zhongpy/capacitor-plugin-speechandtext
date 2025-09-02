package com.kingsun.plugins.speechandtext;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
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
    private volatile boolean stopped = false;

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    @PluginMethod
    public void InitSTT(PluginCall call) {
        try {
            if (stt == null) {
                stt = new SpeechToText();
                stt.initModel(21,getContext());
            }
            JSObject ret=new JSObject();
            ret.put("value","Init STT Success!");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to initialize: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startRecording(PluginCall call) {
        if (stt.isRecording()) {
            call.reject("Already recording");
            return;
        }

        if (!stt.checkMicrophonePermission(getContext())) {
            call.reject("Microphone permission required");
            return;
        }

        if (!stt.initMicrophone(getContext(),getActivity())) {
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
                    call.resolve();
                } catch (Exception e) {
                    call.reject("Recognizer failed: " + e.getMessage());
                }
            });
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to start recording: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopRecording(PluginCall call) {
        if (!stt.isRecording()) {
            call.reject("Not recording");
            return;
        }
        try {
            if (recordingExecutor != null) {
                recordingExecutor.shutdown();
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
        boolean hasPermission = stt.checkMicrophonePermission(getContext());
        JSObject result = new JSObject();
        result.put("hasPermission", hasPermission);
        call.resolve(result);
    }

    @PluginMethod
    public void InitTTS(PluginCall call) {
        try {
            if (tts == null) {
                tts = new TextToSpeech();
                tts.initTTS(getContext());
                tts.initAudioTrack();
            }
            JSObject ret=new JSObject();
            ret.put("value","Init TTS Success!");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to initialize: " + e.getMessage());
        }
    }

    @PluginMethod
    public void generateSpeech(PluginCall call) {
        String text = call.getString("text");
        int sid = call.getInt("sid", 0);
        float speed = call.getFloat("speed", 1.0f);

        if (text == null || text.trim().isEmpty()) {
            call.reject("Text cannot be empty");
            return;
        }

        if (tts == null) {
            call.reject("TTS not initialized");
            return;
        }

        stopped = false;

        ttsExecutor = Executors.newSingleThreadExecutor();
        ttsExecutor.execute(() -> {
            try {
                JSObject result = tts.generateSpeech(text, sid, speed,getContext());
                if (result != null) {
                    notifyListeners("onGenerationComplete", result);
                    call.resolve(result);
                }
            } catch (Exception e) {
                call.reject("Generation failed: " + e.getMessage());
            }
        });
    }
}
