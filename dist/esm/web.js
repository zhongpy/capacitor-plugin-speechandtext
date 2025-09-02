import { WebPlugin } from '@capacitor/core';
export class SpeechAndTextWeb extends WebPlugin {
    async echo(options) {
        console.log('ECHO', options);
        return options;
    }
    async InitSTT() {
        var result = { value: "" };
        return result;
    }
    async startRecording() {
        var result = { text: "", isEndpoint: true };
        return result;
    }
    async stopRecording() {
        var result = { value: "" };
        return result;
    }
    async checkPermission() {
        var result = { hasPermission: false };
        return result;
    }
    async InitTTS() {
        var result = { value: "" };
        return result;
    }
    async generateSpeech(options) {
        var result = { value: "" };
        console.log('generateSpeech', options);
        return result;
    }
}
//# sourceMappingURL=web.js.map