import { WebPlugin } from '@capacitor/core';
import type { GenerateSpeechOptions, GenerateSpeechResult, SpeechAndTextPlugin } from './definitions';
export declare class SpeechAndTextWeb extends WebPlugin implements SpeechAndTextPlugin {
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
    InitSTT(options: {
        itype: number;
        rootDir: string;
    }): Promise<{
        value: string;
    }>;
    DestroySTT(): Promise<{
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
    InitTTS(options: {
        itype: number;
        rootDir: string;
    }): Promise<{
        value: string;
    }>;
    generateSpeech(options: GenerateSpeechOptions): Promise<GenerateSpeechResult>;
}
