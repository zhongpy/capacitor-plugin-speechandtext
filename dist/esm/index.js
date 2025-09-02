import { registerPlugin } from '@capacitor/core';
const SpeechAndText = registerPlugin('SpeechAndText', {
  web: () => import('./web').then((m) => new m.SpeechAndTextWeb()),
});
export * from './definitions';
export { SpeechAndText };
//# sourceMappingURL=index.js.map
