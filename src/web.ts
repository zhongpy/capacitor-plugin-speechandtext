import { WebPlugin } from '@capacitor/core';

import type { SpeechAndTextPlugin } from './definitions';

export class SpeechAndTextWeb extends WebPlugin implements SpeechAndTextPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
