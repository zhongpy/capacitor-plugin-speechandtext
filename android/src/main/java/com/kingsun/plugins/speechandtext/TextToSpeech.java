package com.kingsun.plugins.speechandtext;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextToSpeech {

    private static final String TAG = "TextToSpeech";
    private OfflineTts tts;
    private AudioTrack track;
    private ExecutorService ttsExecutor;
    private final String outputFilename = "generated.wav";
    private volatile boolean stopped = false;

    private String copyDataDir(String dataDir, Context context) {
        // 递归拷贝 assets/dataDir 到 files/dataDir
        copyAssets(dataDir, context);
        return new File(context.getFilesDir(), dataDir).getAbsolutePath();
    }

    private void copyAssets(String path, Context context) {
        AssetManager am = context.getAssets();
        try {
            // 1) 先尝试当“文件”打开
            try (InputStream is = am.open(path)) {
                // 能打开 -> 这是文件，直接复制
                copyFileStream(path, is, context);
                return;
            } catch (FileNotFoundException fnf) {
                // 打不开，可能是目录；继续判断
            } catch (IOException openAsFileFailedButNotFound) {
                // 其他 IO 错误也按不是文件处理，继续走目录逻辑
            }

            // 2) 再尝试当“目录”列出
            String[] list = am.list(path);
            if (list != null && list.length > 0) {
                createDirectory(path, context);
                for (String child : list) {
                    String childPath = path.isEmpty() ? child : (path + "/" + child);
                    copyAssets(childPath, context);
                }
            } else {
                // 有些设备/路径上 list 可能返回空但也不是可打开文件
                // 为稳妥：再尝试一次按文件复制（会抛异常就记录日志）
                try (InputStream is = am.open(path)) {
                    copyFileStream(path, is, context);
                } catch (Exception e) {
                    Log.e(TAG, "copyAssets: neither file nor dir for " + path + " -> " + e);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "copyAssets failed for " + path + ": " + e);
        }
    }

    private void createDirectory(String path, Context context) {
        File dir = new File(context.getFilesDir(), path);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "createDirectory: failed to mkdirs " + dir.getAbsolutePath());
        }
    }

    private void copyFileStream(String assetPath, InputStream is, Context context) throws IOException {
        File out = new File(context.getFilesDir(), assetPath);

        // 若之前误创建成“同名目录”，先删掉再写文件
        if (out.exists() && out.isDirectory()) {
            deleteRecursively(out);
        }

        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) os.write(buf, 0, n);
            os.flush();
        }
        Log.i(TAG, "Copied file: " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursively(c);
            }
        }
        if (!f.delete()) {
            Log.w(TAG, "deleteRecursively: failed to delete " + f.getAbsolutePath());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private OfflineTtsConfig getOfflineTtsConfig(
        String modelDir,
        String modelName, // VITS / Kokoro / Kitten 用
        String acousticModelName, // Matcha 用
        String vocoder, // Matcha 用
        String voices, // Kokoro / Kitten 用
        String lexicon,
        String dataDir,
        String dictDir,
        String ruleFsts,
        String ruleFars,
        Integer numThreads,
        boolean isKitten
    ) {
        modelDir = nz(modelDir);
        modelName = nz(modelName);
        acousticModelName = nz(acousticModelName);
        vocoder = nz(vocoder);
        voices = nz(voices);
        lexicon = nz(lexicon);
        dataDir = nz(dataDir);
        dictDir = nz(dictDir);
        ruleFsts = nz(ruleFsts);
        ruleFars = nz(ruleFars);

        // 线程数
        int numberOfThreads = (numThreads != null) ? numThreads : (!voices.isEmpty() ? 4 : 2);

        // 至少要给一种模型
        if (modelName.isEmpty() && acousticModelName.isEmpty()) {
            throw new IllegalArgumentException("Please specify a TTS model");
        }
        // 不能混用 VITS 和 Matcha
        if (!modelName.isEmpty() && !acousticModelName.isEmpty()) {
            throw new IllegalArgumentException("Please specify either a VITS or a Matcha model, but not both");
        }
        // Matcha 必须有 vocoder
        if (!acousticModelName.isEmpty() && vocoder.isEmpty()) {
            throw new IllegalArgumentException("Please provide vocoder for Matcha TTS");
        }

        // --- VITS ---
        OfflineTtsVitsModelConfig vits;
        if (!modelName.isEmpty() && voices.isEmpty()) {
            // lexicon 可为空串（不要 null）
            vits = OfflineTtsVitsModelConfig.builder()
                .setModel(modelDir + "/" + modelName)
                .setLexicon(lexicon.isEmpty() ? "" : (lexicon.contains(",") ? lexicon : (modelDir + "/" + lexicon)))
                .setTokens(modelDir + "/tokens.txt")
                .setDataDir(dataDir) // 允许空串
                .setDictDir(dictDir) // 允许空串
                .build();
        } else {
            vits = OfflineTtsVitsModelConfig.builder().build();
        }

        // --- Matcha ---
        OfflineTtsMatchaModelConfig matcha;
        if (!acousticModelName.isEmpty()) {
            matcha = OfflineTtsMatchaModelConfig.builder()
                .setAcousticModel(modelDir + "/" + acousticModelName)
                .setVocoder(vocoder) // 非空校验已在上面
                .setLexicon(lexicon.isEmpty() ? "" : (lexicon.contains(",") ? lexicon : (modelDir + "/" + lexicon)))
                .setTokens(modelDir + "/tokens.txt")
                .setDictDir(dictDir)
                .setDataDir(dataDir)
                .build();
        } else {
            matcha = OfflineTtsMatchaModelConfig.builder().build();
        }

        // --- Kokoro ---
        OfflineTtsKokoroModelConfig kokoro;
        if (!voices.isEmpty() && !isKitten) {
            String lexiconPath = lexicon.isEmpty() ? "" : (lexicon.contains(",") ? lexicon : (modelDir + "/" + lexicon));
            kokoro = OfflineTtsKokoroModelConfig.builder()
                .setModel(modelDir + "/" + modelName)
                .setVoices(modelDir + "/" + voices)
                .setTokens(modelDir + "/tokens.txt")
                .setDataDir(dataDir)
                .setLexicon(lexiconPath)
                .setDictDir(dictDir)
                .build();
        } else {
            kokoro = OfflineTtsKokoroModelConfig.builder().build();
        }

        // --- Kitten ---
        OfflineTtsKittenModelConfig kitten;
        if (isKitten) {
            kitten = OfflineTtsKittenModelConfig.builder()
                .setModel(modelDir + "/" + modelName)
                .setVoices(modelDir + "/" + voices)
                .setTokens(modelDir + "/tokens.txt")
                .setDataDir(dataDir)
                .build();
        } else {
            kitten = OfflineTtsKittenModelConfig.builder().build();
        }

        OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
            .setVits(vits)
            .setMatcha(matcha)
            .setKokoro(kokoro)
            .setKitten(kitten)
            .setNumThreads(numberOfThreads)
            .setDebug(true)
            .setProvider("cpu")
            .build();

        return OfflineTtsConfig.builder()
            .setModel(modelConfig)
            .setRuleFsts(ruleFsts) // 空串 OK，不能传 null
            .setRuleFars(ruleFars) // 空串 OK，不能传 null
            .build();
    }

    private int audioCallback(float[] samples) {
        if (!stopped) {
            //track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);

            // Send progress update
            //JSObject progress = new JSObject();
            //progress.put("samplesGenerated", samples.length);
            //notifyListeners("onGenerationProgress", progress);

            return 1;
        } else {
            //track.stop();
            return 0;
        }
    }

    public void initTTS(Integer itype, Context context) {
        // 以 piper 的 VITS 英文模型为例
        String vitsName = "en_US-kristin-medium";
        switch (itype) {
            case 0: {
                vitsName = "en_US-kristin-medium";
            }
            case 1: {
                vitsName = "en_US-bryce-medium";
            }
            case 2: {
                vitsName = "en_GB-alan-medium";
            }
            case 3: {
                vitsName = "en_GB-cori-medium";
            }
            case 4: {
                vitsName = "zh_CN-huayan-medium";
            }
            case 5: {
                vitsName = "fr_FR-siwis-medium";
            }
            case 6: {
                vitsName = "fr_FR-tom-medium";
            }
            default:
                vitsName = "en_US-kristin-medium";
        }
        Log.i(TAG, "initTTS type:" + itype + " vitsName:" + vitsName);
        String modelDir = "vits-piper-" + vitsName;
        String modelName = vitsName + ".onnx";
        String dataDir = "vits-piper-" + vitsName + "/espeak-ng-data"; // 目录
        String ruleFsts = null; // 会在 getOfflineTtsConfig 内转成 ""
        String ruleFars = null;
        String lexicon = ""; // 英文 piper 通常不需要 lexicon，显式设为空串
        String dictDir = ""; // 无则传空串
        String acousticModelName = "";
        String vocoder = "";
        String voices = "";
        boolean isKitten = false;

        if (context == null) throw new IllegalStateException("Context is null");

        // 拷贝 modelDir
        modelDir = copyDataDir(modelDir, context); // -> /data/user/0/<pkg>/files/vits-piper-en_US-miro-high
        // 拷贝 dataDir
        dataDir = copyDataDir(dataDir, context);

        // 自检：必须是文件
        String modelPath = modelDir + "/" + modelName;
        String tokensPath = modelDir + "/tokens.txt";
        assertIsFile("TTS model", modelPath);
        assertIsFile("TTS tokens", tokensPath);
        assertIsDir("TTS data", dataDir);

        OfflineTtsConfig config = getOfflineTtsConfig(
            modelDir,
            modelName,
            acousticModelName,
            vocoder,
            voices,
            lexicon,
            dataDir,
            dictDir,
            ruleFsts,
            ruleFars,
            null,
            isKitten
        );

        tts = new OfflineTts(config);
    }

    private static void assertIsFile(String label, String path) {
        File f = new File(path);
        Log.i(TAG, label + " exists=" + f.exists() + " isFile=" + f.isFile() + " size=" + (f.exists() ? f.length() : -1) + " path=" + path);
        if (!f.exists() || !f.isFile() || f.length() < 128) {
            // 小于 128 字节大概率是 LFS 指针或损坏
            throw new IllegalStateException(label + " invalid: " + path);
        }
    }

    private static void assertIsDir(String label, String path) {
        File f = new File(path);
        Log.i(TAG, label + " exists=" + f.exists() + " isDir=" + f.isDirectory() + " path=" + path);
        if (!f.exists() || !f.isDirectory()) {
            throw new IllegalStateException(label + " directory invalid: " + path);
        }
    }

    public void initAudioTrack() {
        if (tts == null) {
            throw new IllegalStateException("TTS not initialized");
        }

        int sampleRate = tts.getSampleRate();
        int bufLength = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);

        Log.i(TAG, "sampleRate: " + sampleRate + ", buffLength: " + bufLength);

        AudioAttributes attr = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();

        AudioFormat format = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build();

        track = new AudioTrack(attr, format, bufLength, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        track.play();
    }

    public JSObject generateSpeech(String text, int sid, float speed, Context context) {
        //track.pause();
        //track.flush();
        //track.play();

        GeneratedAudio audio = tts.generateWithCallback(text, sid, speed, this::audioCallback);

        String filename = context.getFilesDir().getAbsolutePath() + "/" + outputFilename;
        boolean success = audio.getSamples().length > 0 && audio.save(filename);
        if (success) {
            JSObject result = new JSObject();
            result.put("filePath", filename);
            result.put("sampleRate", tts.getSampleRate());
            result.put("numSamples", audio.getSamples().length);

            return result;
        } else {
            return null;
        }
    }

    public void onDestroy() {
        stopped = true;

        if (track != null) {
            track.stop();
            track.release();
            track = null;
        }
        if (tts != null) {
            tts.release();
            tts = null;
        }
    }
}
