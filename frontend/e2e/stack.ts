import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { parseEnv } from 'node:util'

const repoRoot = fileURLToPath(new URL('../../', import.meta.url))

/** Docker Compose command for the isolated end-to-end stack (project "matriz-e2e"). */
export function compose(...args: string[]): void {
  execFileSync(
    'docker',
    [
      'compose',
      '--project-name',
      'matriz-e2e',
      '--file',
      `${repoRoot}compose.yaml`,
      '--file',
      fileURLToPath(new URL('./compose.e2e.yaml', import.meta.url)),
      ...args,
    ],
    { stdio: 'inherit', env: process.env },
  )
}

/**
 * Fills the database and demo-password variables that are not already set
 * from .env.example (fictitious values), so the stack and the tests share
 * them without reading the developer's .env.
 */
export function loadExampleEnv(): void {
  const example = parseEnv(readFileSync(`${repoRoot}.env.example`, 'utf8'))
  for (const key of ['MATRIZ_DB_NAME', 'MATRIZ_DB_USER', 'MATRIZ_DB_PASSWORD', 'MATRIZ_DEMO_PASSWORD']) {
    process.env[key] ??= example[key]
  }
}
