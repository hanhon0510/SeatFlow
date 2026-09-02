import { purgeSeedData } from './helpers/cleanup'

export default function globalTeardown() {
  purgeSeedData('post-run seed cleanup')
}
