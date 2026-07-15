import { configPath, deleteStoredConfig } from '../config'

/** `linkweave logout` (BR-025): removes the stored configuration. */
export function runLogout(): void {
  if (deleteStoredConfig()) {
    console.log("✓ Configuration removed. Run 'linkweave login' to authenticate again.")
  } else {
    console.log(`No configuration found at ${configPath()}.`)
  }
}
