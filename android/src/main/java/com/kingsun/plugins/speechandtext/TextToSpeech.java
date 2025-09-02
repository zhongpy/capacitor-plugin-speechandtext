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
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
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
    private String copyDataDir(String dataDir) {
        Log.i(TAG, "data dir is " + dataDir);
        copyAssets(dataDir);

        Context context = Plugin.getContext();
        if (context == null) {
            return "";
        }

        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            externalDir = context.getFilesDir();
        }

        String newDataDir = externalDir.getAbsolutePath();
        Log.i(TAG, "newDataDir: " + newDataDir);
        return newDataDir;
    }

    private void copyAssets(String path) {
        Context context = Plugin.getContext();
        if (context == null) return;

        try {
            String[] assets = context.getAssets().list(path);
            if (assets == null || assets.length == 0) {
                copyFile(path);
            } else {
                String fullPath = context.getExternalFilesDir(null) + "/" + path;
                File dir = new File(fullPath);
                dir.mkdirs();
                for (String asset : assets) {
                    String p = path.isEmpty() ? "" : path + "/";
                    copyAssets(p + asset);
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "Failed to copy " + path + ". " + ex);
        }
    }

    private void copyFile(String filename) {
        Context context = Plugin.getContext();
        if (context == null) return;

        InputStream istream = null;
        OutputStream ostream = null;
        try {
            istream = context.getAssets().open(filename);
            String newFilename = context.getExternalFilesDir(null) + "/" + filename;

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
        } catch (Exception ex) {
            Log.e(TAG, "Failed to copy " + filename + ", " + ex);
        } finally {
            try {
                if (istream != null) istream.close();
                if (ostream != null) ostream.close();
            } catch (IOException ex) {
                Log.e(TAG, "Error closing streams", ex);
            }
        }
    }

    private OfflineTtsConfig getOfflineTtsConfig(
            String modelDir,
            String modelName, // for VITS
            String acousticModelName, // for Matcha
            String vocoder, // for Matcha
            String voices, // for Kokoro or kitten
            String lexicon,
            String dataDir,
            String dictDir,
            String ruleFsts,
            String ruleFars,
            Integer numThreads,
            boolean isKitten
    ) {
        int numberOfThreads;
        if (numThreads != null) {
            numberOfThreads = numThreads;
        } else if (voices != null && !voices.isEmpty()) {
            // for Kokoro and Kitten TTS models, we use more threads
            numberOfThreads = 4;
        } else {
            numberOfThreads = 2;
        }

        if ((modelName == null || modelName.isEmpty()) &&
                (acousticModelName == null || acousticModelName.isEmpty())) {
            throw new IllegalArgumentException("Please specify a TTS model");
        }

        if (modelName != null && !modelName.isEmpty() &&
                acousticModelName != null && !acousticModelName.isEmpty()) {
            throw new IllegalArgumentException("Please specify either a VITS or a Matcha model, but not both");
        }

        if (acousticModelName != null && !acousticModelName.isEmpty() &&
                (vocoder == null || vocoder.isEmpty())) {
            throw new IllegalArgumentException("Please provide vocoder for Matcha TTS");
        }

        OfflineTtsVitsModelConfig vits;
        if (modelName != null && !modelName.isEmpty() &&
                (voices == null || voices.isEmpty())) {
            vits = OfflineTtsVitsModelConfig.builder()
                    .setModel(modelDir + "/" + modelName)
                    .setLexicon(modelDir + "/" + lexicon)
                    .setTokens(modelDir + "/tokens.txt")
                    .setDataDir(dataDir)
                    .setDictDir(dictDir)
                    .build();
        } else {
            vits = OfflineTtsVitsModelConfig.builder().build();
        }

        OfflineTtsMatchaModelConfig matcha;
        if (acousticModelName != null && !acousticModelName.isEmpty()) {
            matcha = OfflineTtsMatchaModelConfig.builder()
                    .setAcousticModel(modelDir + "/" + acousticModelName)
                    .setVocoder(vocoder)
                    .setLexicon(modelDir + "/" + lexicon)
                    .setTokens(modelDir + "/tokens.txt")
                    .setDictDir(dictDir)
                    .setDataDir(dataDir)
                    .build();
        } else {
            matcha = OfflineTtsMatchaModelConfig.builder().build();
        }

        OfflineTtsKokoroModelConfig kokoro;
        if (voices != null && !voices.isEmpty() && !isKitten) {
            String lexiconPath;
            if (lexicon == null || lexicon.isEmpty()) {
                lexiconPath = "";
            } else if (lexicon.contains(",")) {
                lexiconPath = lexicon;
            } else {
                lexiconPath = modelDir + "/" + lexicon;
            }

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
                .setRuleFsts(ruleFsts)
                .setRuleFars(ruleFars)
                .build();
    }

    private int audioCallback(float[] samples) {
        if (!stopped) {
            track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);

            // Send progress update
            //JSObject progress = new JSObject();
            //progress.put("samplesGenerated", samples.length);
            //notifyListeners("onGenerationProgress", progress);

            return 1;
        } else {
            track.stop();
            return 0;
        }
    }
    public void initTTS(){
        String modelDir="vits-piper-en_US-miro-high";
        String modelName="en_US-miro-high.onnx";
        String dataDir="vits-piper-en_US-miro-high/espeak-ng-data";
        String ruleFsts=null;
        String ruleFars=null;
        String lexicon=null;
        String dictDir=null;
        String acousticModelName=null;
        String vocoder=null;
        String voices=null;

        boolean isKitten=false;
        Context context = Plugin.getContext();
        if (context == null) {
            throw new IllegalStateException("Context is null");
        }

        // Copy data directories if needed
        if (dataDir != null && !dataDir.isEmpty()) {
            String newDir = copyDataDir(dataDir);
            dataDir = newDir + "/" + dataDir;
        }

        if (dictDir != null && !dictDir.isEmpty()) {
            String newDir = copyDataDir(dictDir);
            dictDir = newDir + "/" + dictDir;
            if (ruleFsts == null || ruleFsts.isEmpty()) {
                ruleFsts = modelDir + "/phone.fst," + modelDir + "/date.fst," + modelDir + "/number.fst";
            }
        }

        OfflineTtsConfig config = getOfflineTtsConfig(
                modelDir, modelName, acousticModelName, vocoder, voices,
                lexicon, dataDir, dictDir, ruleFsts, ruleFars,null, isKitten
        );

        tts = new OfflineTts(config);
    }

    public void initAudioTrack() {
        if (tts == null) {
            throw new IllegalStateException("TTS not initialized");
        }

        int sampleRate = tts.getSampleRate();
        int bufLength = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
        );

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

        track = new AudioTrack(
                attr, format, bufLength, AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );
        track.play();
    }

    public JSObject generateSpeech(String text, int sid, float speed){
        track.pause();
        track.flush();
        track.play();

        GeneratedAudio audio = tts.generateWithCallback(
                text, sid, speed, this::audioCallback
        );

        String filename = Plugin.getContext().getFilesDir().getAbsolutePath() + "/" + outputFilename;
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
        if(tts!=null){
            tts.release();
            tts=null;
        }
    }

}
