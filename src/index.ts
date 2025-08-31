import { registerPlugin } from '@capacitor/core';

import type { SpeechAndTextPlugin } from './definitions';

const SpeechAndText = registerPlugin<SpeechAndTextPlugin>('SpeechAndText', {
  web: () => import('./web').then((m) => new m.SpeechAndTextWeb()),
});

export * from './definitions';
export { SpeechAndText };
