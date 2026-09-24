/** @type {import('stylelint').Config} */
export default {
  plugins: ['stylelint-order'],
  extends: [
    'stylelint-config-recess-order',
    'stylelint-config-recommended-scss',
    'stylelint-config-recommended-vue/scss',
    'stylelint-prettier/recommended',
  ],
  rules: {
    'scss/at-rule-no-unknown': [
      true,
      {
        ignoreAtRules: [
          'theme',
          'source',
          'utility',
          'variant',
          'custom-variant',
          'plugin',
          'reference',
        ],
      },
    ],
    'number-max-precision': [2, { insideFunctions: { '/^(oklch|oklab|lch|lab)$/': 5 } }],
  },
  overrides: [
    {
      files: ['**/*.vue'],
      rules: {
        'no-empty-source': null,
      },
    },
  ],
}
