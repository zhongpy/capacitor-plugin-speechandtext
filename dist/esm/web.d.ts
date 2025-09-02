import { WebPlugin } from '@capacitor/core';
import type { SpeechAndTextPlugin } from './definitions';
export declare class SpeechAndTextWeb extends WebPlugin implements SpeechAndTextPlugin {
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
    InitSTT(): Promise<{
        value: string;
    }>;
    startRecording(): Promise<{
        text: string;
        isEndpoint: boolean;
    }>;
    stopRecording(): Promise<{
        value: string;
    }>;
    checkPermission(): Promise<{
        hasPermission: boolean;
    }>;
    InitTTS(): Promise<{
        value: string;
    }>;
    generateSpeech(options: {
        text: string;
        sid: number;
        speed: number;
    }): Promise<{
        value: string;
    }>;
}
