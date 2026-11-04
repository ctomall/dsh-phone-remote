/**
 * In-memory one-time pairing codes. A code never touches disk: it expires
 * within minutes, is consumed by one successful pairing, and disappears when
 * the Host restarts — which is exactly the lifetime a short-lived handoff
 * secret should have.
 *
 * @module @local/phone-remote/pairing
 */
import { randomInt } from 'node:crypto'

/** Unambiguous alphabet: no 0/O or 1/I/L. */
const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
const CODE_LENGTH = 8
const MAX_TTL_SECONDS = 900
const MIN_TTL_SECONDS = 60

/**
 * Normalize a user-typed code: uppercase, letters and digits only.
 * @param {unknown} value - raw input.
 * @returns {string} normalized code.
 */
export function normalizeCode(value) {
  return String(value ?? '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/gu, '')
    .slice(0, CODE_LENGTH)
}

/**
 * Format a code as `XXXX-XXXX` for display.
 * @param {string} code - normalized code.
 * @returns {string} grouped code.
 */
export function formatCode(code) {
  const normalized = normalizeCode(code)
  if (normalized.length <= 4) return normalized
  return `${normalized.slice(0, 4)}-${normalized.slice(4)}`
}

/**
 * Draw one code with a cryptographic RNG.
 * @returns {string} eight-character code.
 */
function drawCode() {
  let text = ''
  for (let index = 0; index < CODE_LENGTH; index += 1) text += ALPHABET[randomInt(ALPHABET.length)]
  return text
}

/**
 * One process's live pairing-code table.
 */
export class PairingCodes {
  /**
   * @param {{ttlSeconds?: number}} [options] - default lifetime.
   */
  constructor(options = {}) {
    this.ttlSeconds = clampTtl(options.ttlSeconds)
    this.codes = new Map()
  }

  /**
   * Issue one single-use code.
   * @param {{note?: string}} [meta] - optional creator note.
   * @returns {{code: string, formatted: string, expiresAt: number, ttlSeconds: number}} issued code.
   */
  create(meta = {}) {
    this.cleanup()
    let code = drawCode()
    while (this.codes.has(code)) code = drawCode()
    const now = Date.now()
    const expiresAt = now + this.ttlSeconds * 1000
    this.codes.set(code, {
      createdAt: now,
      expiresAt,
      note: typeof meta.note === 'string' ? meta.note.slice(0, 80) : '',
    })
    return { code, formatted: formatCode(code), expiresAt, ttlSeconds: this.ttlSeconds }
  }

  /**
   * Consume one code. A wrong or expired code changes nothing.
   * @param {unknown} value - typed code.
   * @returns {object|undefined} the consumed record, or undefined.
   */
  redeem(value) {
    const code = normalizeCode(value)
    if (code.length !== CODE_LENGTH) return undefined
    const record = this.codes.get(code)
    if (record === undefined) return undefined
    this.codes.delete(code)
    if (record.expiresAt <= Date.now()) return undefined
    return record
  }

  /**
   * Drop expired codes.
   * @returns {number} number removed.
   */
  cleanup() {
    const now = Date.now()
    let removed = 0
    for (const [code, record] of this.codes) {
      if (record.expiresAt <= now) {
        this.codes.delete(code)
        removed += 1
      }
    }
    return removed
  }

  /**
   * Count live codes.
   * @returns {number} live code count.
   */
  count() {
    this.cleanup()
    return this.codes.size
  }

  /**
   * Remove every code, e.g. when pairing is disabled or the gateway restarts.
   */
  clear() {
    this.codes.clear()
  }
}

/**
 * Clamp a configured TTL into the accepted range.
 * @param {unknown} value - seconds.
 * @returns {number} bounded seconds.
 */
export function clampTtl(value) {
  const numeric = typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : 300
  return Math.min(MAX_TTL_SECONDS, Math.max(MIN_TTL_SECONDS, numeric))
}
