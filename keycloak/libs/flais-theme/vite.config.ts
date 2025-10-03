import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { keycloakify } from 'keycloakify/vite-plugin'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    tailwindcss(),
    react(),
    keycloakify({
      accountThemeImplementation: 'none',
      themeName: 'flais-theme',
      themeVersion: '0.0.1',
      keycloakVersionTargets: {
        '22-to-25': false,
        'all-other-versions': 'flais-theme.jar',
      },
    }),
  ],
})
