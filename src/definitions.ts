import type { PluginListenerHandle } from '@capacitor/core';

export interface SpeechAndTextPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  InitSTT(options: { type: number }): Promise<{ value: string }>;
  startRecording(): Promise<{ text: string; isEndpoint: boolean }>;
  stopRecording(): Promise<{ value: string }>;
  checkPermission(): Promise<{ hasPermission: boolean }>;

  InitTTS(options: { type: number }): Promise<{ value: string }>;
  generateSpeech(options: { text: string; sid: number; speed: number }): Promise<{ value: string }>;

  addListener(
    eventName: 'onRecognizerResult',
    listenerFunc: (data: { text: string; isEndpoint: boolean }) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'onGenerationComplete',
    listenerFunc: (data: { value: string }) => void,
  ): Promise<PluginListenerHandle>;
}
