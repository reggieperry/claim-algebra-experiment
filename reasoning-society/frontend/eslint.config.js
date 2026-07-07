import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import react from 'eslint-plugin-react';
import vitest from '@vitest/eslint-plugin';
import testingLibrary from 'eslint-plugin-testing-library';
import prettier from 'eslint-config-prettier';

export default tseslint.config(
  // Never lint build output or coverage reports.
  { ignores: ['dist/**', 'coverage/**'] },

  // Baseline JS correctness for every lintable file.
  js.configs.recommended,

  // Type-aware TypeScript (ts-types / ts-errors / ts-concurrency / ts-modules).
  // strictTypeChecked pulls in no-explicit-any, no-non-null-assertion,
  // no-floating-promises, no-misused-promises, await-thenable, only-throw-error,
  // no-implied-eval; stylisticTypeChecked adds the idiom rules.
  {
    name: 'app/typescript-type-aware',
    files: ['**/*.{ts,tsx}'],
    extends: [
      tseslint.configs.strictTypeChecked,
      tseslint.configs.stylisticTypeChecked,
    ],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
      globals: { ...globals.browser },
    },
    rules: {
      // ts-modules: mark every type-only import, the companion to verbatimModuleSyntax.
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports', fixStyle: 'separate-type-imports' },
      ],
      // ts-types / ts-style: a new union member must break an exhaustive switch.
      '@typescript-eslint/switch-exhaustiveness-check': 'error',
      // ts-security: no dynamic code execution from a string.
      'no-eval': 'error',
      'no-script-url': 'error',
    },
  },

  // React: the Rules of Hooks + exhaustive-deps (ts-react), the fast-refresh
  // boundary, jsx-a11y accessibility, and the injection rules from
  // eslint-plugin-react (ts-security).
  {
    name: 'app/react',
    files: ['**/*.{ts,tsx}'],
    extends: [jsxA11y.flatConfigs.recommended],
    plugins: {
      // react-hooks v7's `recommended-latest` preset still ships an eslintrc-style
      // string-array `plugins`, which flat config rejects — so register the plugin
      // here and reuse only its rule set below.
      'react-hooks': reactHooks,
      react,
      'react-refresh': reactRefresh,
    },
    settings: {
      react: { version: 'detect' },
    },
    rules: {
      ...reactHooks.configs['recommended-latest'].rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      'react/no-danger': 'error',
      'react/jsx-no-target-blank': 'error',
      'react/jsx-no-script-url': 'error',
    },
  },

  // Test files only: Vitest + Testing Library discipline (ts-testing).
  {
    name: 'app/tests',
    files: ['src/**/*.test.{ts,tsx}', 'src/test/**/*.{ts,tsx}'],
    extends: [vitest.configs.recommended, testingLibrary.configs['flat/react']],
    rules: {
      'vitest/no-focused-tests': 'error',
      'vitest/no-disabled-tests': 'error',
    },
  },

  // The Vite/Vitest config runs in Node — give it Node globals.
  {
    name: 'app/node-config',
    files: ['vite.config.ts'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },

  // Plain-JS config files (this file) are not part of a TS program.
  {
    name: 'app/js-config',
    files: ['**/*.js'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },

  // Turn off every rule Prettier already owns. Must stay last.
  prettier,
);
