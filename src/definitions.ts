import type { PluginListenerHandle } from '@capacitor/core';

export interface AigcMetadata {
  Label: string;
  ContentProducer: string;
  ProduceID: string;
  ReservedCode1: string;
  ContentPropagator?: string;
  PropagateID?: string;
  ReservedCode2?: string;
}

export interface GenerateSpeechOptions {
  text: string;
  wavName: string;
  sid: number;
  speed: number;
  label?: string;
  contentProducer?: string;
  produceId?: string;
  contentPropagator?: string;
  propagateId?: string;
  reservedCode2?: string;
}

export interface GenerateSpeechResult {
  filePath: string;
  sampleRate: number;
  numSamples: number;
  aigcMetadata: AigcMetadata;
  aigcMetadataJson: string;
}

export interface SpeechAndTextPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  InitSTT(options: { itype: number; rootDir: string }): Promise<{ value: string }>;
  DestroySTT(): Promise<{ value: string }>;
  startRecording(): Promise<{ text: string; isEndpoint: boolean }>;
  stopRecording(): Promise<{ value: string }>;
  checkPermission(): Promise<{ hasPermission: boolean }>;

  InitTTS(options: { itype: number; rootDir: string }): Promise<{ value: string }>;
  generateSpeech(options: GenerateSpeechOptions): Promise<GenerateSpeechResult>;

  addListener(
    eventName: 'onRecognizerResult',
    listenerFunc: (data: { text: string; isEndpoint: boolean }) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'onGenerationComplete',
    listenerFunc: (data: GenerateSpeechResult) => void,
  ): Promise<PluginListenerHandle>;
}
