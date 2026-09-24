#!/usr/bin/env node
import { execSync } from 'node:child_process'
/**
 * Scans the project for hardcoded Chinese text to catch missing i18n keys.
 * Detects 4 patterns: quoted strings, bare template text, template literals,
 * and Chinese text next to interpolation.
 */
import { readFileSync } from 'node:fs'
import process from 'node:process'

const outputJson = process.argv.includes('--json')

const files = execSync(
  `find src -type f \\( -name "*.vue" -o -name "*.ts" \\) ` +
    `-not -path "*/demo/*" -not -path "*/node_modules/*" -not -path "*/types/generated/*"`,
)
  .toString()
  .trim()
  .split('\n')
  .filter(Boolean)

const results = []

for (const file of files) {
  const content = readFileSync(file, 'utf-8')
  const lines = content.split('\n')
  const isVue = file.endsWith('.vue')

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const t = line.trim()

    if (t.startsWith('//') || t.startsWith('/*') || t.startsWith('*')) continue
    if (t.startsWith('.') && (t.includes('{') || t.includes('}'))) continue
    if (t.includes(':deep(') || t.includes('@import') || t.includes('font-family')) continue

    // --- 1. Chinese inside quotes ---
    for (const m of line.matchAll(/['"][^\n"'\u4E00-\u9FFF]*[\u4E00-\u9FFF][^\n"']*['"]/g)) {
      const s = m[0]
      if (
        s.startsWith('"./') ||
        s.startsWith("'./") ||
        s.startsWith('"../') ||
        s.startsWith("'../")
      ) {
        continue
      }
      if (s.includes('.svg') || s.includes('.css') || s.includes('.docx')) continue
      if (s.length < 3) continue
      const before = line.substring(0, m.index)
      if (before.includes('t(')) continue
      add(file, i + 1, 'quoted', s.substring(0, 60))
    }

    // --- 2. Bare Chinese between tags in a Vue template ---
    if (isVue) {
      for (const m of line.matchAll(/>([^<]{1,100}[\u4E00-\u9FFF][^<]{0,100})</g)) {
        const raw = m[1].trim()
        if (
          raw &&
          !raw.includes('{{') &&
          !line.trim().startsWith(':') &&
          !line.trim().startsWith('v-')
        ) {
          add(file, i + 1, 'bare-text', raw)
        }
      }
      // Multi-line bare text (opening tag on the previous line, pure Chinese line here)
      if (!t.includes('{') && !t.startsWith(':') && !t.startsWith('v-') && !t.startsWith('&')) {
        const pc = t.match(/^[\u4E00-\u9FFF\uFF00-\uFFEF\s]{2,30}$/)
        if (pc) add(file, i + 1, 'bare-text', pc[0].trim())
      }
    }

    // --- 3. Chinese inside backtick template literals ---
    for (const m of line.matchAll(/`[^`\u4E00-\u9FFF]*[\u4E00-\u9FFF][^`]*`/g)) {
      if (t.includes('t(')) continue
      add(file, i + 1, 'template-literal', m[0].substring(0, 60))
    }

    // --- 4. Chinese next to interpolation: `{{ var }}Chinese` or `Chinese{{ var }}` ---
    if (isVue) {
      for (const m of line.matchAll(/\}\}\s*[\u4E00-\u9FFF]+/g)) {
        const text = m[0].replace('}}', '').trim()
        if (text && !t.includes('t(')) add(file, i + 1, 'next-to-interpolation', text)
      }
      for (const m of line.matchAll(/[\u4E00-\u9FFF]+\s*\{\{/g)) {
        const text = m[0].replace('{{', '').trim()
        if (text && !t.includes('t(')) add(file, i + 1, 'next-to-interpolation', text)
      }
    }
  }
}

function add(file, line, type, text) {
  results.push({ file, line, type, text })
}

// De-duplicate
const seen = new Set()
const unique = results.filter((r) => {
  const k = `${r.file}:${r.type}:${r.text}`
  if (seen.has(k)) return false
  seen.add(k)
  return true
})

if (outputJson) {
  console.log(JSON.stringify(unique, null, 2))
} else {
  console.log(`\nFound ${unique.length} occurrence(s):\n`)
  let last = ''
  for (const r of unique) {
    if (r.file !== last) {
      console.log(`\n━━━ ${r.file} ━━━`)
      last = r.file
    }
    console.log(`  L${r.line} [${r.type}] ${r.text}`)
  }
  console.log(`\nTotal: ${unique.length} occurrence(s)`)
}
