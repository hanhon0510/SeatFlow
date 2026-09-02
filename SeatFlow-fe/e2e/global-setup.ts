import { purgeSeedData } from './helpers/cleanup'

/** Clears anything an interrupted earlier run left behind, so every run starts from a clean slate. */
export default function globalSetup() {
  purgeSeedData('pre-run seed cleanup')
}
