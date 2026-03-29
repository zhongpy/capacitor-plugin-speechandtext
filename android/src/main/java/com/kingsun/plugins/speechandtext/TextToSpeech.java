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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextToSpeech {

    private static final String TAG = "TextToSpeech";
    private static final String AIGC_CHUNK_ID = "AIGC";
    private static final double EXPLICIT_MARKER_UNIT_SECONDS = 0.10d;
    private static final double EXPLICIT_MARKER_TONE_FREQUENCY_HZ = 1000.0d;
    private static final double EXPLICIT_MARKER_FADE_SECONDS = 0.008d;
    private OfflineTts tts;
    private AudioTrack track;
    private ExecutorService ttsExecutor;
    private final String outputFilename = ".wav";
    private volatile boolean stopped = false;

    public static class AigcMetadata {
        public final String label;
        public final String contentProducer;
        public final String produceId;
        public final String contentPropagator;
        public final String propagateId;
        public final String reservedCode2;
        public String reservedCode1;

        public AigcMetadata(
                String label,
                String contentProducer,
                String produceId,
                String contentPropagator,
                String propagateId,
                String reservedCode2) {
            this.label = label == null ? "" : label;
            this.contentProducer = contentProducer == null ? "" : contentProducer;
            this.produceId = produceId == null ? "" : produceId;
            this.contentPropagator = contentPropagator == null ? "" : contentPropagator;
            this.propagateId = propagateId == null ? "" : propagateId;
            this.reservedCode2 = reservedCode2 == null ? "" : reservedCode2;
            this.reservedCode1 = "";
        }
    }

    private static class WavFormatInfo {
        final int audioFormat;
        final int channelCount;
        final int sampleRate;
        final int blockAlign;
        final int bitsPerSample;
        final int dataOffset;
        final int dataSize;

        WavFormatInfo(
                int audioFormat,
                int channelCount,
                int sampleRate,
                int blockAlign,
                int bitsPerSample,
                int dataOffset,
                int dataSize) {
            this.audioFormat = audioFormat;
            this.channelCount = channelCount;
            this.sampleRate = sampleRate;
            this.blockAlign = blockAlign;
            this.bitsPerSample = bitsPerSample;
            this.dataOffset = dataOffset;
            this.dataSize = dataSize;
        }
    }

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
        if (parent != null && !parent.exists())
            parent.mkdirs();

        try (OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0)
                os.write(buf, 0, n);
            os.flush();
        }
        Log.i(TAG, "Copied file: " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children)
                    deleteRecursively(c);
            }
        }
        if (!f.delete()) {
            Log.w(TAG, "deleteRecursively: failed to delete " + f.getAbsolutePath());
        }
    }

    private static String normDir(String s) {
        if (s == null)
            return "";
        // 去掉末尾 /
        while (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String join(String a, String b) {
        a = normDir(a);
        if (b == null)
            b = "";
        b = b.replaceAll("^[\\\\/]+", "");
        if (a.isEmpty())
            return b;
        return a + "/" + b;
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
            boolean isKitten) {
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
            String lexiconPath = lexicon.isEmpty() ? ""
                    : (lexicon.contains(",") ? lexicon : (modelDir + "/" + lexicon));
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
            // track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);

            // Send progress update
            // JSObject progress = new JSObject();
            // progress.put("samplesGenerated", samples.length);
            // notifyListeners("onGenerationProgress", progress);

            return 1;
        } else {
            // track.stop();
            return 0;
        }
    }

    public void initTTS(Integer itype, Context context, String ttsRootDir) {
        String vitsName = pickVitsName(itype);

        Log.i(TAG, "initTTS type:" + itype + " vitsName:" + vitsName);

        String modelDirName = "vits-piper-" + vitsName;
        String modelName = vitsName + ".onnx";

        String root = normDir(ttsRootDir);
        boolean useExternal = !root.isEmpty();

        String modelDir;
        String dataDir;

        if (useExternal) {
            modelDir = join(root, modelDirName);
            dataDir = modelDir + "/espeak-ng-data";
        } else {
            // 兼容旧模式：从 assets copy
            modelDir = copyDataDir(modelDirName, context);
            dataDir = copyDataDir(modelDirName + "/espeak-ng-data", context);
        }

        String ruleFsts = null;
        String ruleFars = null;
        String lexicon = "";
        String dictDir = "";
        String acousticModelName = "";
        String vocoder = "";
        String voices = "";
        boolean isKitten = false;

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
                isKitten);

        tts = new OfflineTts(config);
    }

    private String pickVitsName(Integer itype) {
        if (itype == null)
            itype = 0;
        switch (itype) {
            case 0:
                return "en_US-kristin-medium";
            case 1:
                return "en_US-bryce-medium";
            case 2:
                return "en_GB-alan-medium";
            case 3:
                return "en_GB-cori-medium";
            case 4:
                return "zh_CN-huayan-medium";
            case 5:
                return "fr_FR-siwis-medium";
            case 6:
                return "fr_FR-tom-medium";
            default:
                return "en_US-kristin-medium";
        }
    }

    private static void assertIsFile(String label, String path) {
        File f = new File(path);
        Log.i(TAG, label + " exists=" + f.exists() + " isFile=" + f.isFile() + " size=" + (f.exists() ? f.length() : -1)
                + " path=" + path);
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
        int bufLength = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT);

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

    public JSObject generateSpeech(
            String text,
            String wavName,
            int sid,
            float speed,
            Context context,
            AigcMetadata aigcMetadata,
            boolean addExplicitMarker) throws IOException, NoSuchAlgorithmException {
        // track.pause();
        // track.flush();
        // track.play();

        GeneratedAudio audio = tts.generateWithCallback(text, sid, speed, this::audioCallback);

        String filename = context.getFilesDir().getAbsolutePath() + "/" + wavName + outputFilename;
        boolean success = audio.getSamples().length > 0 && audio.save(filename);
        if (success) {
            File wavFile = new File(filename);
            if (addExplicitMarker) {
                prependExplicitMarker(wavFile);
            }
            String reservedCode1 = computeDataChunkSha256Hex(wavFile);
            aigcMetadata.reservedCode1 = reservedCode1;
            String aigcMetadataJson = appendAigcChunk(wavFile, aigcMetadata);

            JSObject aigcMetadataObject = new JSObject();
            aigcMetadataObject.put("Label", aigcMetadata.label);
            aigcMetadataObject.put("ContentProducer", aigcMetadata.contentProducer);
            aigcMetadataObject.put("ProduceID", aigcMetadata.produceId);
            aigcMetadataObject.put("ReservedCode1", aigcMetadata.reservedCode1);
            if (!aigcMetadata.contentPropagator.isEmpty()) {
                aigcMetadataObject.put("ContentPropagator", aigcMetadata.contentPropagator);
            }
            if (!aigcMetadata.propagateId.isEmpty()) {
                aigcMetadataObject.put("PropagateID", aigcMetadata.propagateId);
            }
            if (!aigcMetadata.reservedCode2.isEmpty()) {
                aigcMetadataObject.put("ReservedCode2", aigcMetadata.reservedCode2);
            }

            JSObject result = new JSObject();
            result.put("filePath", filename);
            result.put("sampleRate", tts.getSampleRate());
            result.put("numSamples", audio.getSamples().length);
            result.put("aigcMetadata", aigcMetadataObject);
            result.put("aigcMetadataJson", aigcMetadataJson);
            result.put("explicitMarkerAdded", addExplicitMarker);

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

    private String appendAigcChunk(File wavFile, AigcMetadata metadata) throws IOException {
        String aigcMetadataJson = buildAigcMetadataJson(metadata);
        byte[] chunkData = aigcMetadataJson.getBytes(StandardCharsets.UTF_8);
        int padding = chunkData.length % 2;

        try (RandomAccessFile raf = new RandomAccessFile(wavFile, "rw")) {
            if (raf.length() < 12) {
                throw new IOException("WAV file is too short: " + wavFile.getAbsolutePath());
            }

            byte[] riffHeader = new byte[4];
            raf.readFully(riffHeader);
            byte[] waveHeader = new byte[4];
            raf.seek(8);
            raf.readFully(waveHeader);

            if (!"RIFF".equals(new String(riffHeader, StandardCharsets.US_ASCII))
                    || !"WAVE".equals(new String(waveHeader, StandardCharsets.US_ASCII))) {
                throw new IOException("Target file is not a RIFF/WAVE file: " + wavFile.getAbsolutePath());
            }

            raf.seek(4);
            int currentRiffSize = readLittleEndianInt(raf);
            int nextRiffSize = currentRiffSize + 8 + chunkData.length + padding;

            raf.seek(raf.length());
            raf.write(AIGC_CHUNK_ID.getBytes(StandardCharsets.US_ASCII));
            writeLittleEndianInt(raf, chunkData.length);
            raf.write(chunkData);
            if (padding == 1) {
                raf.write(0);
            }

            raf.seek(4);
            writeLittleEndianInt(raf, nextRiffSize);
        }

        return aigcMetadataJson;
    }

    private void prependExplicitMarker(File wavFile) throws IOException {
        byte[] sourceBytes = readAllBytes(wavFile);
        validateRiffWave(sourceBytes, wavFile);
        WavFormatInfo formatInfo = parseWavFormatInfo(sourceBytes, wavFile);
        byte[] markerData = buildExplicitMarkerData(sourceBytes, formatInfo);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(sourceBytes.length + markerData.length + 64);
        outputStream.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(outputStream, 0);
        outputStream.write("WAVE".getBytes(StandardCharsets.US_ASCII));

        int offset = 12;
        while (offset + 8 <= sourceBytes.length) {
            String chunkId = new String(sourceBytes, offset, 4, StandardCharsets.US_ASCII);
            int chunkSize = readLittleEndianInt(sourceBytes, offset + 4);
            int chunkDataStart = offset + 8;
            int chunkDataEnd = chunkDataStart + chunkSize;

            if (chunkSize < 0 || chunkDataEnd > sourceBytes.length) {
                throw new IOException("Invalid WAV chunk layout in " + wavFile.getAbsolutePath());
            }

            outputStream.write(chunkId.getBytes(StandardCharsets.US_ASCII));
            if ("data".equals(chunkId)) {
                writeLittleEndianInt(outputStream, chunkSize + markerData.length);
                outputStream.write(markerData);
                outputStream.write(sourceBytes, chunkDataStart, chunkSize);
                if (((chunkSize + markerData.length) & 1) == 1) {
                    outputStream.write(0);
                }
            } else {
                writeLittleEndianInt(outputStream, chunkSize);
                outputStream.write(sourceBytes, chunkDataStart, chunkSize);
                if ((chunkSize & 1) == 1) {
                    outputStream.write(0);
                }
            }

            offset = chunkDataEnd + (chunkSize & 1);
        }

        byte[] rewrittenBytes = outputStream.toByteArray();
        writeLittleEndianInt(rewrittenBytes, 4, rewrittenBytes.length - 8);
        overwriteFile(wavFile, rewrittenBytes);
    }

    private String computeDataChunkSha256Hex(File wavFile) throws IOException, NoSuchAlgorithmException {
        byte[] fileBytes = readAllBytes(wavFile);
        validateRiffWave(fileBytes, wavFile);

        int offset = 12;
        while (offset + 8 <= fileBytes.length) {
            String chunkId = new String(fileBytes, offset, 4, StandardCharsets.US_ASCII);
            int chunkSize = readLittleEndianInt(fileBytes, offset + 4);
            int chunkDataStart = offset + 8;
            int chunkDataEnd = chunkDataStart + chunkSize;

            if (chunkSize < 0 || chunkDataEnd > fileBytes.length) {
                throw new IOException("Invalid WAV chunk layout in " + wavFile.getAbsolutePath());
            }

            if ("data".equals(chunkId)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(fileBytes, chunkDataStart, chunkSize);
                return bytesToHex(digest.digest());
            }

            offset = chunkDataEnd + (chunkSize % 2);
        }

        throw new IOException("WAV data chunk not found: " + wavFile.getAbsolutePath());
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    private void overwriteFile(File file, byte[] content) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(content);
            outputStream.flush();
        }
    }

    private String buildAigcMetadataJson(AigcMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        appendJsonField(builder, "Label", metadata.label, false);
        appendJsonField(builder, "ContentProducer", metadata.contentProducer, true);
        appendJsonField(builder, "ProduceID", metadata.produceId, true);
        appendJsonField(builder, "ReservedCode1", metadata.reservedCode1, true);
        if (!metadata.contentPropagator.isEmpty()) {
            appendJsonField(builder, "ContentPropagator", metadata.contentPropagator, true);
        }
        if (!metadata.propagateId.isEmpty()) {
            appendJsonField(builder, "PropagateID", metadata.propagateId, true);
        }
        if (!metadata.reservedCode2.isEmpty()) {
            appendJsonField(builder, "ReservedCode2", metadata.reservedCode2, true);
        }
        builder.append("}");
        return builder.toString();
    }

    private void appendJsonField(StringBuilder builder, String key, String value, boolean prependComma) {
        if (prependComma) {
            builder.append(",");
        }
        builder.append("\"")
                .append(escapeJson(key))
                .append("\":\"")
                .append(escapeJson(value))
                .append("\"");
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c <= 0x1F) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    private void validateRiffWave(byte[] fileBytes, File wavFile) throws IOException {
        if (fileBytes.length < 12) {
            throw new IOException("WAV file is too short: " + wavFile.getAbsolutePath());
        }

        String riff = new String(fileBytes, 0, 4, StandardCharsets.US_ASCII);
        String wave = new String(fileBytes, 8, 4, StandardCharsets.US_ASCII);
        if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) {
            throw new IOException("Target file is not a RIFF/WAVE file: " + wavFile.getAbsolutePath());
        }
    }

    private WavFormatInfo parseWavFormatInfo(byte[] fileBytes, File wavFile) throws IOException {
        Integer audioFormat = null;
        Integer channelCount = null;
        Integer sampleRate = null;
        Integer blockAlign = null;
        Integer bitsPerSample = null;
        Integer dataOffset = null;
        Integer dataSize = null;

        int offset = 12;
        while (offset + 8 <= fileBytes.length) {
            String chunkId = new String(fileBytes, offset, 4, StandardCharsets.US_ASCII);
            int chunkSize = readLittleEndianInt(fileBytes, offset + 4);
            int chunkDataStart = offset + 8;
            int chunkDataEnd = chunkDataStart + chunkSize;

            if (chunkSize < 0 || chunkDataEnd > fileBytes.length) {
                throw new IOException("Invalid WAV chunk layout in " + wavFile.getAbsolutePath());
            }

            if ("fmt ".equals(chunkId)) {
                if (chunkSize < 16) {
                    throw new IOException("WAV fmt chunk is invalid: " + wavFile.getAbsolutePath());
                }
                audioFormat = readLittleEndianUnsignedShort(fileBytes, chunkDataStart);
                channelCount = readLittleEndianUnsignedShort(fileBytes, chunkDataStart + 2);
                sampleRate = readLittleEndianInt(fileBytes, chunkDataStart + 4);
                blockAlign = readLittleEndianUnsignedShort(fileBytes, chunkDataStart + 12);
                bitsPerSample = readLittleEndianUnsignedShort(fileBytes, chunkDataStart + 14);
            } else if ("data".equals(chunkId)) {
                dataOffset = chunkDataStart;
                dataSize = chunkSize;
            }

            offset = chunkDataEnd + (chunkSize & 1);
        }

        if (audioFormat == null || channelCount == null || sampleRate == null || blockAlign == null
                || bitsPerSample == null || dataOffset == null || dataSize == null) {
            throw new IOException("WAV fmt/data chunks are incomplete: " + wavFile.getAbsolutePath());
        }

        return new WavFormatInfo(
                audioFormat,
                channelCount,
                sampleRate,
                blockAlign,
                bitsPerSample,
                dataOffset,
                dataSize);
    }

    private byte[] buildExplicitMarkerData(byte[] fileBytes, WavFormatInfo formatInfo) throws IOException {
        if (formatInfo.blockAlign <= 0) {
            throw new IOException("Unsupported WAV block align: " + formatInfo.blockAlign);
        }

        int unitFrames = Math.max(1, (int) Math.round(formatInfo.sampleRate * EXPLICIT_MARKER_UNIT_SECONDS));
        int shortToneFrames = unitFrames;
        int longToneFrames = unitFrames * 3;
        int symbolGapFrames = unitFrames;
        // Morse "AI" rendered as a strict explicit marker sequence: . - . .
        int totalFrames = shortToneFrames + symbolGapFrames + longToneFrames + symbolGapFrames
                + shortToneFrames + symbolGapFrames + shortToneFrames;

        float[] markerFrames = new float[totalFrames];
        float amplitude = computeMarkerAmplitude(fileBytes, formatInfo);
        int cursor = 0;

        cursor = writeTone(markerFrames, cursor, shortToneFrames, formatInfo.sampleRate, amplitude);
        cursor += symbolGapFrames;
        cursor = writeTone(markerFrames, cursor, longToneFrames, formatInfo.sampleRate, amplitude);
        cursor += symbolGapFrames;
        cursor = writeTone(markerFrames, cursor, shortToneFrames, formatInfo.sampleRate, amplitude);
        cursor += symbolGapFrames;
        writeTone(markerFrames, cursor, shortToneFrames, formatInfo.sampleRate, amplitude);

        return encodeFrames(markerFrames, formatInfo);
    }

    private float computeMarkerAmplitude(byte[] fileBytes, WavFormatInfo formatInfo) throws IOException {
        int frameCount = formatInfo.dataSize / formatInfo.blockAlign;
        int sampledFrames = Math.min(frameCount, Math.max(formatInfo.sampleRate * 3, 1));
        if (sampledFrames <= 0) {
            return 0.12f;
        }

        double energy = 0.0d;
        int samples = 0;
        for (int frameIndex = 0; frameIndex < sampledFrames; frameIndex++) {
            int frameOffset = formatInfo.dataOffset + frameIndex * formatInfo.blockAlign;
            for (int channel = 0; channel < formatInfo.channelCount; channel++) {
                int sampleOffset = frameOffset + channel * bytesPerSample(formatInfo);
                float sample = decodeSample(fileBytes, sampleOffset, formatInfo);
                energy += sample * sample;
                samples++;
            }
        }

        if (samples == 0) {
            return 0.18f;
        }

        double rms = Math.sqrt(energy / samples);
        double targetAmplitude = Math.max(0.12d, Math.min(0.35d, rms * Math.sqrt(2.0d)));
        return (float) targetAmplitude;
    }

    private int writeTone(float[] destination, int startFrame, int toneFrames, int sampleRate, float amplitude) {
        int fadeFrames = Math.max(1, (int) Math.round(sampleRate * EXPLICIT_MARKER_FADE_SECONDS));
        for (int i = 0; i < toneFrames && startFrame + i < destination.length; i++) {
            double envelope = 1.0d;
            if (i < fadeFrames) {
                envelope = Math.min(envelope, (double) i / fadeFrames);
            }
            int framesFromEnd = toneFrames - 1 - i;
            if (framesFromEnd < fadeFrames) {
                envelope = Math.min(envelope, (double) framesFromEnd / fadeFrames);
            }
            double phase = (2.0d * Math.PI * EXPLICIT_MARKER_TONE_FREQUENCY_HZ * i) / sampleRate;
            destination[startFrame + i] = (float) (Math.sin(phase) * amplitude * Math.max(envelope, 0.0d));
        }
        return startFrame + toneFrames;
    }

    private byte[] encodeFrames(float[] frames, WavFormatInfo formatInfo) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(frames.length * formatInfo.blockAlign);
        for (float frame : frames) {
            for (int channel = 0; channel < formatInfo.channelCount; channel++) {
                writeSample(outputStream, frame, formatInfo);
            }
        }
        return outputStream.toByteArray();
    }

    private void writeSample(ByteArrayOutputStream outputStream, float sample, WavFormatInfo formatInfo)
            throws IOException {
        float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
        if (formatInfo.audioFormat == 1 && formatInfo.bitsPerSample == 16) {
            short pcm = (short) Math.round(clamped * Short.MAX_VALUE);
            outputStream.write(pcm & 0xFF);
            outputStream.write((pcm >> 8) & 0xFF);
            return;
        }
        if (formatInfo.audioFormat == 1 && formatInfo.bitsPerSample == 32) {
            int pcm = (int) Math.round(clamped * Integer.MAX_VALUE);
            writeLittleEndianInt(outputStream, pcm);
            return;
        }
        if (formatInfo.audioFormat == 3 && formatInfo.bitsPerSample == 32) {
            writeLittleEndianInt(outputStream, Float.floatToIntBits(clamped));
            return;
        }
        throw new IOException("Explicit marker only supports PCM16, PCM32, or Float32 WAV data");
    }

    private float decodeSample(byte[] data, int offset, WavFormatInfo formatInfo) throws IOException {
        if (formatInfo.audioFormat == 1 && formatInfo.bitsPerSample == 16) {
            short pcm = (short) readLittleEndianUnsignedShort(data, offset);
            return pcm / 32768.0f;
        }
        if (formatInfo.audioFormat == 1 && formatInfo.bitsPerSample == 32) {
            int pcm = readLittleEndianInt(data, offset);
            return pcm / 2147483648.0f;
        }
        if (formatInfo.audioFormat == 3 && formatInfo.bitsPerSample == 32) {
            return Float.intBitsToFloat(readLittleEndianInt(data, offset));
        }
        throw new IOException("Explicit marker only supports PCM16, PCM32, or Float32 WAV data");
    }

    private int bytesPerSample(WavFormatInfo formatInfo) {
        return formatInfo.blockAlign / Math.max(formatInfo.channelCount, 1);
    }

    private int readLittleEndianInt(RandomAccessFile raf) throws IOException {
        int b0 = raf.read();
        int b1 = raf.read();
        int b2 = raf.read();
        int b3 = raf.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new IOException("Unexpected EOF while reading little-endian int");
        }
        return (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
    }

    private int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private int readLittleEndianUnsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private void writeLittleEndianInt(RandomAccessFile raf, int value) throws IOException {
        raf.write(value & 0xFF);
        raf.write((value >> 8) & 0xFF);
        raf.write((value >> 16) & 0xFF);
        raf.write((value >> 24) & 0xFF);
    }

    private void writeLittleEndianInt(ByteArrayOutputStream outputStream, int value) {
        outputStream.write(value & 0xFF);
        outputStream.write((value >> 8) & 0xFF);
        outputStream.write((value >> 16) & 0xFF);
        outputStream.write((value >> 24) & 0xFF);
    }

    private void writeLittleEndianInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
