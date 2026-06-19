import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'live.shouldiholdoff.app',
  appName: 'HoldOff',
  webDir: '../public',
  server: {
    androidScheme: 'https',
  },
  android: {
    backgroundColor: '#0d0a1a',
    allowMixedContent: true,
    captureInput: true,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 0,
      backgroundColor: '#0d0a1a',
    },
  },
};

export default config;
