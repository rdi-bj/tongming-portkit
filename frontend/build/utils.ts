import path from 'node:path'

export function resolvePath(...paths: string[]) {
  return path.resolve(__dirname, '..', ...paths)
}
