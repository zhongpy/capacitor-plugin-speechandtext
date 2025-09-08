var capacitorSpeechAndText = (function (exports, core) {
    'use strict';

    const SpeechAndText = core.registerPlugin('SpeechAndText', {
        web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.SpeechAndTextWeb()),
    });

    class SpeechAndTextWeb extends core.WebPlugin {
        async echo(options) {
            console.log('ECHO', options);
            return options;
        }
        async InitSTT(options) {
            const result = { value: '' };
            console.log('InitSTT', options);
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
        async InitTTS(options) {
            const result = { value: '' };
            console.log('InitTTS', options);
            return result;
        }
        async generateSpeech(options) {
            const result = { value: '' };
            console.log('generateSpeech', options);
            return result;
        }
    }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        SpeechAndTextWeb: SpeechAndTextWeb
    });

    exports.SpeechAndText = SpeechAndText;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
