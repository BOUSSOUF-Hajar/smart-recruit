import { defineConfig, devices } from '@playwright/test';

/**
 * Captures d'écran du README (US-58) : « npm run screenshots », contre l'application lancée avec les
 * données de démonstration. Séparé des tests de bout en bout : rien n'est vérifié, tout est photographié.
 */
export default defineConfig({
  testDir: '.',
  testMatch: '*.screens.ts',
  timeout: 120_000,
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:8080',
    locale: 'fr-FR',
    timezoneId: 'Africa/Casablanca',
    colorScheme: 'light',
  },
  projects: [
    {
      name: 'captures',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        deviceScaleFactor: 1,
        launchOptions: process.env['PW_CHROMIUM_PATH']
          ? { executablePath: process.env['PW_CHROMIUM_PATH'] }
          : {},
      },
    },
  ],
});
