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
            var _a, _b, _c, _d, _e, _f;
            const produceId = (_a = options.produceId) !== null && _a !== void 0 ? _a : '';
            const contentProducer = (_b = options.contentProducer) !== null && _b !== void 0 ? _b : '001191440115MA59C0UT8Y00000';
            const result = {
                filePath: '',
                sampleRate: 0,
                numSamples: 0,
                explicitMarkerAdded: (_c = options.addExplicitMarker) !== null && _c !== void 0 ? _c : false,
                aigcMetadata: {
                    Label: (_d = options.label) !== null && _d !== void 0 ? _d : '1',
                    ContentProducer: contentProducer,
                    ProduceID: produceId,
                    ReservedCode1: '',
                    ContentPropagator: (_e = options.contentPropagator) !== null && _e !== void 0 ? _e : contentProducer,
                    PropagateID: (_f = options.propagateId) !== null && _f !== void 0 ? _f : produceId,
                    ReservedCode2: options.reservedCode2,
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
