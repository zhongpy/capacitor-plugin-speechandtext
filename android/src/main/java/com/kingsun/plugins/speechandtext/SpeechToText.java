package com.kingsun.plugins.speechandtext;

import com.getcapacitor.Plugin;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig;
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig;
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig;
import com.k2fsa.sherpa.onnx.EndpointConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;

public class SpeechToText {
    public interface RecognizerCallback {
        void onMessage(String text,boolean isEndpoint);
    }
    private OnlineRecognizer recognizer;
    private Integer sampleRateInHz = 16000;
    private static final String TAG = "SpeechToText";
    private static final Integer REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String[] permissions = {Manifest.permission.RECORD_AUDIO};
    private AudioRecord audioRecord;
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private final int audioSource = MediaRecorder.AudioSource.MIC;

    private int idx = 0;
    private String lastText = "";
    private volatile Boolean isRecording = false;

    private OnlineRecognizerConfig OnlineRconfig = null;

    public boolean checkMicrophonePermission() {
        Context context = getAppContext();
        if (context == null) return false;

        return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }
    public void initModel(Integer type){
        String ruleFsts = null;

        boolean useHr = false;
        HomophoneReplacerConfig.Builder builder=HomophoneReplacerConfig.builder();
        builder.setDictDir("dict");
        builder.setLexicon("lexicon.txt");
        builder.setRuleFsts("replace.fst");
        HomophoneReplacerConfig hr = builder.build();

        Log.i(TAG, "Select model type " + type);
        OnlineRecognizerConfig.Builder orbuilder=OnlineRecognizerConfig.builder();
        FeatureConfig fconfig=FeatureConfig.builder().setSampleRate(sampleRateInHz).setFeatureDim(80).build();
        orbuilder.setFeatureConfig(fconfig);
        orbuilder.setOnlineModelConfig(getModelConfig(type));
        EndpointConfig econfig=EndpointConfig.builder().build();
        orbuilder.setEndpointConfig(econfig);
        if (ruleFsts != null) {
            orbuilder.setRuleFsts(ruleFsts);
        }
        OnlineRconfig = orbuilder.build();

        if (useHr) {
            if (!hr.getDictDir().isEmpty() && hr.getDictDir().charAt(0) != '/') {
                // We need to copy it from the assets directory to some path
                String newDir = copyDataDir(hr.getDictDir());
                builder.setDictDir(newDir + "/" + hr.getDictDir());
                hr = builder.build();
            }
            orbuilder.setHr(hr);
            OnlineRconfig = orbuilder.build();
        }

        recognizer = new OnlineRecognizer(OnlineRconfig);
    }

    public boolean initMicrophone() {
        Context context = getAppContext();
        Activity activity = getAppActivity();

        if (context == null || activity == null) {
            Log.e(TAG, "Context or Activity is null");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            // 请求权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return false;
        }

        try {
            int numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            Log.i(TAG, "buffer size in milliseconds: " + (numBytes * 1000.0f / sampleRateInHz));

            audioRecord = new AudioRecord(
                    audioSource,
                    sampleRateInHz,
                    channelConfig,
                    audioFormat,
                    numBytes * 2
            );

            return audioRecord.getState() == AudioRecord.STATE_INITIALIZED;

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AudioRecord: " + e.getMessage());
            return false;
        }
    }

    public void processSamples(RecognizerCallback callback) {
        if (recognizer == null) {
            Log.e(TAG, "Recognizer not initialized");
            return;
        }

        var stream = recognizer.createStream();
        float interval = 0.1f; // 100 ms
        int bufferSize = (int) (interval * sampleRateInHz);
        short[] buffer = new short[bufferSize];

        while (isRecording) {
            try {
                int ret = audioRecord.read(buffer, 0, buffer.length);
                if (ret > 0) {
                    float[] samples = new float[ret];
                    for (int i = 0; i < ret; i++) {
                        samples[i] = buffer[i] / 32768.0f;
                    }

                    stream.acceptWaveform(samples, sampleRateInHz);

                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream);
                    }

                    boolean isEndpoint = recognizer.isEndpoint(stream);
                    String text = recognizer.getResult(stream).getText();

                    // Handle paraformer model
                    if (isEndpoint && !OnlineRconfig.getModelConfig().getParaformer().getEncoder().isEmpty()) {
                        float[] tailPaddings = new float[(int) (0.8 * sampleRateInHz)];
                        stream.acceptWaveform(tailPaddings, sampleRateInHz);
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream);
                        }
                        text = recognizer.getResult(stream).getText();
                    }

                    String textToDisplay = lastText;

                    if (text != null && !text.isEmpty()) {
                        if (lastText.isEmpty()) {
                            textToDisplay = idx + ": " + text;
                        } else {
                            textToDisplay = lastText + "\n" + idx + ": " + text;
                        }
                    }

                    if (isEndpoint) {
                        recognizer.reset(stream);
                        if (text != null && !text.isEmpty()) {
                            lastText = lastText + "\n" + idx + ": " + text;
                            textToDisplay = lastText;
                            idx++;
                        }
                    }
                    callback.onMessage(textToDisplay,isEndpoint);

                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing samples", e);
            }
        }

        stream.release();
    }

    public boolean isRecording(){
        return isRecording;
    }

    public void startRecording(){
        audioRecord.startRecording();
        isRecording = true;
        idx = 0;
        lastText = "";
    }

    public void stopRecording(){
        if(!isRecording){
            return;
        }
        isRecording = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio record", e);
            }
        }
    }

    public void onDestroy() {
        isRecording = false;

        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        if (recognizer != null) {
            recognizer.release();
            recognizer=null;
        }
    }

    private Context getAppContext() {
        return Plugin.getContext(); // Capacitor Plugin 提供的 getContext() 方法
    }

    private Activity getAppActivity() {
        return Plugin.getActivity(); // Capacitor Plugin 提供的 getActivity() 方法
    }
    private String copyDataDir(String dataDir) {
        Log.i(TAG, "data dir is " + dataDir);
        copyAssets(dataDir);

        File externalDir = getAppContext().getExternalFilesDir(null);
        if (externalDir == null) {
            externalDir = getAppContext().getFilesDir();
        }
        String newDataDir = externalDir.getAbsolutePath();
        Log.i(TAG, "newDataDir: " + newDataDir);
        return newDataDir;
    }

    private void copyAssets(String path) {
        try {
            String[] assets = getAppContext().getAssets().list(path);
            if (assets == null) {
                // 可能是文件
                copyFile(path);
            } else if (assets.length == 0) {
                // 空目录
                createDirectory(path);
            } else {
                // 目录，递归复制
                createDirectory(path);
                for (String asset : assets) {
                    String newPath = path.equals("") ? asset : path + "/" + asset;
                    copyAssets(newPath);
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "Failed to copy " + path + ". " + ex);
        }
    }

    private void createDirectory(String path) {
        String fullPath = getAppContext().getExternalFilesDir(null) + "/" + path;
        File dir = new File(fullPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private void copyFile(String filename) {
        InputStream istream = null;
        OutputStream ostream = null;
        try {
            istream = getAppContext().getAssets().open(filename);
            String newFilename = getAppContext().getExternalFilesDir(null) + "/" + filename;

            // 确保父目录存在
            File outFile = new File(newFilename);
            File parentDir = outFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            ostream = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = istream.read(buffer)) != -1) {
                ostream.write(buffer, 0, read);
            }
            ostream.flush();
            Log.i(TAG, "Copied: " + filename);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to copy " + filename + ", " + ex);
        } finally {
            try {
                if (istream != null) {
                    istream.close();
                }
                if (ostream != null) {
                    ostream.close();
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error closing streams: " + ex);
            }
        }
    }

    private OnlineModelConfig getModelConfig(int type) {
        switch (type) {
            case 0: {
                String modelDir = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-99-avg-1.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-99-avg-1.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-99-avg-1.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .build();
            }

            case 1: {
                String modelDir = "sherpa-onnx-lstm-zh-2023-02-20";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-11-avg-1.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-11-avg-1.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-11-avg-1.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("lstm")
                        .build();
            }

            case 2: {
                String modelDir = "sherpa-onnx-lstm-en-2023-02-17";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-99-avg-1.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-99-avg-1.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-99-avg-1.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("lstm")
                        .build();
            }

            case 3: {
                String modelDir = "icefall-asr-zipformer-streaming-wenetspeech-20230615";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/exp/encoder-epoch-12-avg-4-chunk-16-left-128.int8.onnx")
                                .setDecoder(modelDir + "/exp/decoder-epoch-12-avg-4-chunk-16-left-128.onnx")
                                .setJoiner(modelDir + "/exp/joiner-epoch-12-avg-4-chunk-16-left-128.onnx")
                                .build())
                        .setTokens(modelDir + "/data/lang_char/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 4: {
                String modelDir = "icefall-asr-zipformer-streaming-wenetspeech-20230615";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/exp/encoder-epoch-12-avg-4-chunk-16-left-128.onnx")
                                .setDecoder(modelDir + "/exp/decoder-epoch-12-avg-4-chunk-16-left-128.onnx")
                                .setJoiner(modelDir + "/exp/joiner-epoch-12-avg-4-chunk-16-left-128.onnx")
                                .build())
                        .setTokens(modelDir + "/data/lang_char/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 5: {
                String modelDir = "sherpa-onnx-streaming-paraformer-bilingual-zh-en";
                return OnlineModelConfig.builder()
                        .setParaformer(OnlineParaformerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.int8.onnx")
                                .setDecoder(modelDir + "/decoder.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("paraformer")
                        .build();
            }

            case 6: {
                String modelDir = "sherpa-onnx-streaming-zipformer-en-2023-06-26";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-99-avg-1-chunk-16-left-128.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-99-avg-1-chunk-16-left-128.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 7: {
                String modelDir = "sherpa-onnx-streaming-zipformer-fr-2023-04-14";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-29-avg-9-with-averaged-model.int8.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-29-avg-9-with-averaged-model.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-29-avg-9-with-averaged-model.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .build();
            }

            case 8: {
                String modelDir = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-99-avg-1.int8.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-99-avg-1.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-99-avg-1.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .build();
            }

            case 9: {
                String modelDir = "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-99-avg-1.int8.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-99-avg-1.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-99-avg-1.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .build();
            }

            case 10: {
                String modelDir = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-99-avg-1.int8.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-99-avg-1.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-99-avg-1.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .build();
            }

            case 11: {
                String modelDir = "sherpa-onnx-nemo-streaming-fast-conformer-ctc-en-80ms";
                return OnlineModelConfig.builder()
                        .setNeMoCtc(OnlineNeMoCtcModelConfig.builder()
                                .setModel(modelDir + "/model.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .build();
            }

            case 12: {
                String modelDir = "sherpa-onnx-nemo-streaming-fast-conformer-ctc-en-480ms";
                return OnlineModelConfig.builder()
                        .setNeMoCtc(OnlineNeMoCtcModelConfig.builder()
                                .setModel(modelDir + "/model.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .build();
            }

            case 13: {
                String modelDir = "sherpa-onnx-nemo-streaming-fast-conformer-ctc-en-1040ms";
                return OnlineModelConfig.builder()
                        .setNeMoCtc(OnlineNeMoCtcModelConfig.builder()
                                .setModel(modelDir + "/model.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .build();
            }

            case 14: {
                String modelDir = "sherpa-onnx-streaming-zipformer-korean-2024-06-16";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder-epoch-99-avg-1.int8.onnx")
                                .setDecoder(modelDir + "/decoder-epoch-99-avg-1.onnx")
                                .setJoiner(modelDir + "/joiner-epoch-99-avg-1.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .build();
            }

            case 15: {
                String modelDir = "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01";
                return OnlineModelConfig.builder()
                        .setZipformer2Ctc(OnlineZipformer2CtcModelConfig.builder()
                                .setModel(modelDir + "/model.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .build();
            }

            case 16: {
                String modelDir = "sherpa-onnx-streaming-zipformer-small-ctc-zh-2025-04-01";
                return OnlineModelConfig.builder()
                        .setZipformer2Ctc(OnlineZipformer2CtcModelConfig.builder()
                                .setModel(modelDir + "/model.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .build();
            }

            case 17: {
                String modelDir = "sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30";
                return OnlineModelConfig.builder()
                        .setZipformer2Ctc(OnlineZipformer2CtcModelConfig.builder()
                                .setModel(modelDir + "/model.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .build();
            }

            case 18: {
                String modelDir = "sherpa-onnx-streaming-zipformer-ctc-zh-2025-06-30";
                return OnlineModelConfig.builder()
                        .setZipformer2Ctc(OnlineZipformer2CtcModelConfig.builder()
                                .setModel(modelDir + "/model.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 19: {
                String modelDir = "sherpa-onnx-streaming-zipformer-ctc-zh-fp16-2025-06-30";
                return OnlineModelConfig.builder()
                        .setZipformer2Ctc(OnlineZipformer2CtcModelConfig.builder()
                                .setModel(modelDir + "/model.fp16.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 20: {
                String modelDir = "sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.int8.onnx")
                                .setDecoder(modelDir + "/decoder.onnx")
                                .setJoiner(modelDir + "/joiner.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 21: {
                String modelDir = "sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.onnx")
                                .setDecoder(modelDir + "/decoder.onnx")
                                .setJoiner(modelDir + "/joiner.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 22: {
                String modelDir = "sherpa-onnx-streaming-zipformer-es-kroko-2025-08-06";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.onnx")
                                .setDecoder(modelDir + "/decoder.onnx")
                                .setJoiner(modelDir + "/joiner.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 23: {
                String modelDir = "sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.onnx")
                                .setDecoder(modelDir + "/decoder.onnx")
                                .setJoiner(modelDir + "/joiner.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 24: {
                String modelDir = "sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.onnx")
                                .setDecoder(modelDir + "/decoder.onnx")
                                .setJoiner(modelDir + "/joiner.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 25: {
                String modelDir = "sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.int8.onnx")
                                .setDecoder(modelDir + "/decoder.onnx")
                                .setJoiner(modelDir + "/joiner.int8.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 26: {
                String modelDir = "sherpa-onnx-streaming-zipformer-small-ru-vosk-2025-08-16";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.onnx")
                                .setDecoder(modelDir + "/decoder.onnx")
                                .setJoiner(modelDir + "/joiner.onnx")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer2")
                        .build();
            }

            case 1000: {
                String modelDir = "sherpa-onnx-rk3588-streaming-zipformer-bilingual-zh-en-2023-02-20";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.rknn")
                                .setDecoder(modelDir + "/decoder.rknn")
                                .setJoiner(modelDir + "/joiner.rknn")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .setProvider("rknn")
                        .build();
            }

            case 1001: {
                String modelDir = "sherpa-onnx-rk3588-streaming-zipformer-small-bilingual-zh-en-2023-02-16";
                return OnlineModelConfig.builder()
                        .setTransducer(OnlineTransducerModelConfig.builder()
                                .setEncoder(modelDir + "/encoder.rknn")
                                .setDecoder(modelDir + "/decoder.rknn")
                                .setJoiner(modelDir + "/joiner.rknn")
                                .build())
                        .setTokens(modelDir + "/tokens.txt")
                        .setModelType("zipformer")
                        .setProvider("rknn")
                        .build();
            }

            default:
                return null;
        }
    }
}
