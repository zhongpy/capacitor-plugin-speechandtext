import { WebPlugin } from '@capacitor/core';
export class SpeechAndTextWeb extends WebPlugin {
    async echo(options) {
        console.log('ECHO', options);
        return options;
    }
    async InitSTT() {
        const result = { value: '' };
        return result;
    }
    async startRecording() {
        const result = { text: '', isEndpoint: true };
        return result;
    }
    async stopRecording() {
        const result = { value: '' };
        return result;
    }
    async checkPermission() {
        const result = { hasPermission: false };
        return result;
    }
    async InitTTS() {
        const result = { value: '' };
        return result;
    }
    async generateSpeech(options) {
        const result = { value: '' };
        console.log('generateSpeech', options);
        return result;
    }
}
//# sourceMappingURL=web.js.map