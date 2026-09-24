import antfu from '@antfu/eslint-config'
import pluginPrettier from 'eslint-plugin-prettier/recommended'

export default antfu(
  {
    // .vscode/settings.json is JSONC (it carries comments), so it cannot be linted as JSON
    ignores: ['**/.vscode/**'],
    javascript: true,
    typescript: {
      tsconfigPath: 'tsconfig.app.json',
    },
    type: true,
    vue: true,
    unocss: true,
    rules: {
      'e18e/prefer-static-regex': 'off',
      'antfu/consistent-list-newline': 'off',
      'antfu/if-newline': 'off',
      'unocss/order': 'error',
      'unocss/order-attributify': 'error',
      'unocss/enforce-class-compile': 'off',
      'ts/strict-boolean-expressions': 'off',
      'ts/no-unsafe-assignment': 'off',
      'ts/no-unsafe-member-access': 'off',
      'ts/no-unsafe-call': 'off',
      'ts/no-unsafe-argument': 'off',
      'ts/no-unsafe-return': 'off',
    },
  },
  {
    files: ['**/*.vue'],
    rules: {
      'vue/block-order': [
        'error',
        {
          order: ['script', 'template', 'style'],
        },
      ],
      'vue/component-name-in-template-casing': [
        'error',
        'PascalCase',
        {
          registeredComponentsOnly: false,
        },
      ],
      'vue/prop-name-casing': ['error', 'camelCase'],
      'vue/slot-name-casing': ['error', 'camelCase'],
      'vue/component-options-name-casing': ['error', 'PascalCase'],
    },
  },
  pluginPrettier,
)
