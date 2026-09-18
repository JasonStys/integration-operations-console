/**
 * File: eslint.config.mjs
 * Purpose: TypeScript/React static-analysis policy with generated files excluded.
 * Symbols/variables: flat ESLint configuration array; exact lines are in docs/code-index.md.
 */
import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/**', 'coverage/**', 'src/api/schema.d.ts'] },
  eslint.configs.recommended,
  ...tseslint.configs.strict,
  {
    files: ['src/**/*.{ts,tsx}', '*.ts'],
    languageOptions: {
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
    rules: {
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
    },
  },
);
