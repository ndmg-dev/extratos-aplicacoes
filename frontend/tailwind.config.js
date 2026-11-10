/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
        mono: ["ui-monospace", "SFMono-Regular", "Menlo", "Consolas", "monospace"],
      },
      colors: {
        // superfícies do tema escuro
        bg: "#0b0e12",
        panel: "#161a20",
        panel2: "#1d232b",
        edge: "#272e38",
        edge2: "#353f4b",
        // textos
        ink: "#e7eaef",
        ink2: "#9aa4b0",
        ink3: "#69727e",
        // acento (âmbar/dourado)
        accent: "#e3b23c",
        accent2: "#f1c457",
        // status funcionais (§10) — tons calibrados para fundo escuro
        ok: "#35c07a",
        revisar: "#e3b24a",
        semdetalhe: "#8b95a1",
        erro: "#ef6b61",
        info: "#5b9bf0",
      },
      fontSize: {
        "2xs": ["0.6875rem", { lineHeight: "1rem" }],
      },
    },
  },
  plugins: [],
};
