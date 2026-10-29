/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: '#FFC928', dark: '#E0A800', soft: '#FFF4CC' },
        ink: { DEFAULT: '#1F1A17', muted: '#5C534D' },
      },
    },
  },
  plugins: [],
};
