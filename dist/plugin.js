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
        async DestroySTT() {
            const result = { value: '' };
            console.log('DestroySTT');
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
            var _a, _b, _c;
            const result = {
                filePath: '',
                sampleRate: 0,
                numSamples: 0,
                aigcMetadata: {
                    Label: (_a = options.label) !== null && _a !== void 0 ? _a : '1',
                    ContentProducer: (_b = options.contentProducer) !== null && _b !== void 0 ? _b : '001191440115MA59C0UT8Y00000',
                    ProduceID: (_c = options.produceId) !== null && _c !== void 0 ? _c : '',
                    ReservedCode1: '',
                },
                aigcMetadataJson: '',
            };
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
