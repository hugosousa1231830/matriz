import { compose, loadExampleEnv } from './stack.ts'

/**
 * Starts the isolated stack from scratch: removes any leftover from an
 * aborted run, builds the images and waits until the backend is healthy
 * (migrations applied and demo data seeded) and the SPA is running.
 */
export default function globalSetup(): void {
  loadExampleEnv()
  compose('down', '--volumes', '--remove-orphans')
  compose('up', '--detach', '--build', '--wait')
}
