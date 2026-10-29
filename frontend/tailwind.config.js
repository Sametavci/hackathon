/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: '#157565', dark: '#163B36', soft: '#EDF4F0' },
        accent: '#F7D86B',
        line: '#CFDDD4',
        ink: { DEFAULT: '#163B36', muted: '#45625E' },
      },
    },
  },
  plugins: [],
};
