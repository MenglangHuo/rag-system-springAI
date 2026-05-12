/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#eef4ff',
          100: '#dae6ff',
          200: '#bcd2ff',
          300: '#8db3ff',
          400: '#5a8cff',
          500: '#3366ff',
          600: '#1f47e6',
          700: '#1a38b8',
          800: '#172f8f',
          900: '#0f1f5c',
        },
        gold: { 500: '#c9a227', 600: '#a7861f' },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        display: ['"Plus Jakarta Sans"', 'Inter', 'sans-serif'],
      },
      boxShadow: {
        soft: '0 1px 2px rgba(16,24,40,.04), 0 1px 3px rgba(16,24,40,.06)',
        glow: '0 10px 30px -10px rgba(31,71,230,.35)',
      },
      keyframes: {
        'fade-in': { '0%': { opacity: 0, transform: 'translateY(6px)' }, '100%': { opacity: 1, transform: 'translateY(0)' } },
        'pulse-dot': { '0%,80%,100%': { transform: 'scale(0.6)', opacity: .4 }, '40%': { transform: 'scale(1)', opacity: 1 } },
      },
      animation: {
        'fade-in': 'fade-in .35s ease-out both',
        'pulse-dot': 'pulse-dot 1.2s infinite ease-in-out',
      },
    },
  },
  plugins: [],
}
