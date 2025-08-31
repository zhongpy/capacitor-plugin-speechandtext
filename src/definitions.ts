export interface SpeechAndTextPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
