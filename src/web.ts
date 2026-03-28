import { WebPlugin } from '@capacitor/core';

import type { GenerateSpeechOptions, GenerateSpeechResult, SpeechAndTextPlugin } from './definitions';

export class SpeechAndTextWeb extends WebPlugin implements SpeechAndTextPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
  async InitSTT(options: { itype: number; rootDir: string }): Promise<{ value: string }> {
    const result = { value: '' };
    console.log('InitSTT', options);
    return result;
  }

  async DestroySTT(): Promise<{ value: string }> {
    const result = { value: '' };
    console.log('DestroySTT');
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
  async InitTTS(options: { itype: number; rootDir: string }): Promise<{ value: string }> {
    const result = { value: '' };
    console.log('InitTTS', options);
    return result;
  }
  async generateSpeech(options: GenerateSpeechOptions): Promise<GenerateSpeechResult> {
    const produceId = options.produceId ?? '';
    const contentProducer = options.contentProducer ?? '001191440115MA59C0UT8Y00000';
    const result = {
      filePath: '',
      sampleRate: 0,
      numSamples: 0,
      explicitMarkerAdded: options.addExplicitMarker ?? false,
      aigcMetadata: {
        Label: options.label ?? '1',
        ContentProducer: contentProducer,
        ProduceID: produceId,
        ReservedCode1: '',
        ContentPropagator: options.contentPropagator ?? contentProducer,
        PropagateID: options.propagateId ?? produceId,
        ReservedCode2: options.reservedCode2,
      },
      aigcMetadataJson: '',
    };
    console.log('generateSpeech', options);
    return result;
  }
}
