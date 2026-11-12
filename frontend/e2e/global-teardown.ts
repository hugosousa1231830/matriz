import { compose, loadExampleEnv } from './stack.ts'

/** Stops the stack and discards its database (set E2E_KEEP_STACK=1 to inspect it after a run). */
export default function globalTeardown(): void {
  if (process.env.E2E_KEEP_STACK) {
    return
  }
  loadExampleEnv()
  compose('down', '--volumes', '--remove-orphans')
}
