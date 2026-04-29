import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.flowna.musicplayer',
  appName: 'Flowna Music Player',
  webDir: 'www',
  bundledWebRuntime: false,
  android: {
    backgroundColor: '#F7F4F0',
    allowMixedContent: false,
    captureInput: true
  },
  server: {
    androidScheme: 'https'
  }
};

export default config;
