import { WebPlugin } from '@capacitor/core';

import type { SpeechAndTextPlugin } from './definitions';

export class SpeechAndTextWeb extends WebPlugin implements SpeechAndTextPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
  async InitSTT():Promise<{ value: string }>{
    var result={value:""};
    return result;
  }

  async startRecording():Promise<{ text: string, isEndpoint:boolean }>{
    var result={text:"",isEndpoint:true};
    return result;
  }

  async stopRecording():Promise<{ value: string }>{
    var result={value:""};
    return result;
  }
  async checkPermission():Promise<{hasPermission:boolean}>{
    var result={hasPermission:false};
    return result;
  }
  async InitTTS():Promise<{ value: string }>{
    var result={value:""};
    return result;
  }
  async generateSpeech(options:{text:string,sid:number,speed:number}):Promise<{ value: string }>{
    var result={value:""};
    console.log('generateSpeech', options);
    return result;
  }
}
