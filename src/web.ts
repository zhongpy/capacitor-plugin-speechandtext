import { WebPlugin } from '@capacitor/core';

import type { SpeechAndTextPlugin } from './definitions';

export class SpeechAndTextWeb extends WebPlugin implements SpeechAndTextPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
  async InitSTT(options: { itype: number }): Promise<{ value: string }> {
    const result = { value: '' };
    console.log('InitSTT', options);
    return result;
  }

  async startRecording(): Promise<{ text: string; isEndpoint: boolean }> {
    const result = { text: '', isEndpoint: true };
    return result;
  }

  async stopRecording(): Promise<{ value: string }> {
    const result = { value: '' };
    return result;
  }
  async checkPermission(): Promise<{ hasPermission: boolean }> {
    const result = { hasPermission: false };
    return result;
  }
  async InitTTS(options: { itype: number }): Promise<{ value: string }> {
    const result = { value: '' };
    console.log('InitTTS', options);
    return result;
  }
  async generateSpeech(options: { text: string; sid: number; speed: number }): Promise<{ value: string }> {
    const result = { value: '' };
    console.log('generateSpeech', options);
    return result;
  }
}
