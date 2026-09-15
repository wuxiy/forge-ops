import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import type { PersistedRun } from './types.js'

/** Atomic local state: identifiers only; prompts, Context and provider output are never written here. */
export class RunStore {
  private readonly records = new Map<string, PersistedRun>()

  constructor(private readonly file: string) {}

  async load(): Promise<void> {
    try {
      const text = await readFile(this.file, 'utf8')
      const parsed = JSON.parse(text) as PersistedRun[]
      for (const record of parsed) this.records.set(record.idempotencyKey, record)
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
    }
  }

  get(idempotencyKey: string): PersistedRun | undefined {
    return this.records.get(idempotencyKey)
  }

  all(): readonly PersistedRun[] {
    return [...this.records.values()]
  }

  async save(record: PersistedRun): Promise<void> {
    this.records.set(record.idempotencyKey, record)
    await mkdir(dirname(this.file), { recursive: true })
    const temporary = `${this.file}.tmp`
    await writeFile(temporary, JSON.stringify([...this.records.values()]), { mode: 0o600 })
    await rename(temporary, this.file)
  }
}
