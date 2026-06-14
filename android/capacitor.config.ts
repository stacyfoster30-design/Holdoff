import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'live.shouldiholdoff.app',
  appName: 'HoldOff',
  webDir: '../public',
  server: {
    androidScheme: 'https',
  },
  android: {
    backgroundColor: '#FAF6F0',
    allowMixedContent: true,
    captureInput: true,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 0,
      backgroundColor: '#FAF6F0',
    },
  },
};

export default config;