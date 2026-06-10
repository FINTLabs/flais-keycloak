import { defineConfig } from "oxlint";

export default defineConfig({
  ignorePatterns: [
    "dist/**",
    "build/**",
    "node_modules/**",
    "storybook-static/**",
    ".storybook-static/**",
  ],
  categories: {
    correctness: "error",
    suspicious: "warn",
    pedantic: "off",
  },
  plugins: ["typescript", "react", "oxc"],
  env: {
    browser: true,
    node: true,
    es2024: true,
  },
  rules: {
    "react/react-in-jsx-scope": "off",
    "react/rules-of-hooks": "error",
    "react/exhaustive-deps": "error",
    "typescript/no-explicit-any": "error",
    "no-console": "error",
  },
});
