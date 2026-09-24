import type { App } from 'vue'
import { createI18n } from 'vue-i18n'

const messageModules = import.meta.glob<{ default: Record<string, string> }>('/locales/**/*.json', {
  eager: true,
})

const messages: Record<string, Record<string, Record<string, string>>> = {}

for (const path in messageModules) {
  const [_, __, lang, fileName] = path.split('/')
  const moduleName = fileName.replace('.json', '')
  if (messages[lang]) {
    messages[lang][moduleName] = messageModules[path].default
  } else {
    messages[lang] = {
      [moduleName]: messageModules[path].default,
    }
  }
}

export const i18n = createI18n({
  // legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'en-US',
  globalInjection: true,
  availableLocales: Object.keys(messages),
  messages,
})

export function setupI18n(app: App) {
  app.use(i18n)
}
