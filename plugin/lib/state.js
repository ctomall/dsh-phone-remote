/**
 * Durable phone-pairing state for one DSH installation: the device records a
 * paired phone is authenticated by, plus the small helpers both the gateway
 * and the settings page read. Device secrets are stored as SHA-256 digests,
 * exactly like a password file, so the state file alone never yields a live
 * cookie.
 *
 * @module @local/phone-remote/state
 */
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto'
import { chmod, mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'

const STATE_VERSION = 1
const DEVICE_ID_BYTES = 9
const DEVICE_SECRET_BYTES = 32

/**
 * Expand a leading `~` and resolve a configured data directory.
 * @param {unknown} configured - `dataDir` config value.
 * @returns {string} absolute directory.
 */
export function resolveDataDir(configured) {
  const chosen = typeof configured === 'string' ? configured.trim() : ''
  if (chosen.length > 0) return resolve(expandHome(chosen))
  const home = process.env.DSH_HOME?.trim() || join(homedir(), '.dsh')
  return join(home, 'phone-remote')
}

/**
 * Expand a leading `~` in one path.
 * @param {string} value - raw path.
 * @returns {string} expanded path.
 */
export function expandHome(value) {
  if (value === '~') return homedir()
  if (value.startsWith('~/')) return join(homedir(), value.slice(2))
  return value
}

/**
 * SHA-256 of one device secret as lowercase hex.
 * @param {string} secret - base64url secret.
 * @returns {string} digest.
 */
export function hashSecret(secret) {
  return createHash('sha256').update(secret, 'utf8').digest('hex')
}

/**
 * Constant-time comparison of one secret against a stored digest.
 * @param {string} secret - presented secret.
 * @param {string} stored - stored hex digest.
 * @returns {boolean} whether they match.
 */
export function secretMatches(secret, stored) {
  if (typeof secret !== 'string' || typeof stored !== 'string') return false
  const actual = Buffer.from(hashSecret(secret), 'hex')
  let expected
  try {
    expected = Buffer.from(stored, 'hex')
  } catch {
    return false
  }
  return actual.byteLength === expected.byteLength && timingSafeEqual(actual, expected)
}

/**
 * Atomically write a 0600 JSON document, creating its directory.
 * @param {string} file - destination path.
 * @param {unknown} value - JSON-serializable value.
 * @returns {Promise<void>}
 */
async function writePrivateJson(file, value) {
  await mkdir(dirname(file), { recursive: true, mode: 0o700 })
  const temporary = `${file}.${process.pid}.${Date.now()}.tmp`
  await writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 })
  await chmod(temporary, 0o600)
  await rename(temporary, file)
}

/**
 * Coerce persisted device records into the shape the rest of the plugin
 * trusts, dropping anything malformed instead of failing activation.
 * @param {unknown} raw - parsed state document.
 * @returns {object[]} sanitized devices.
 */
function sanitizeDevices(raw) {
  if (!Array.isArray(raw)) return []
  const devices = []
  for (const item of raw) {
    if (typeof item !== 'object' || item === null) continue
    const id = typeof item.id === 'string' ? item.id : ''
    const tokenHash = typeof item.tokenHash === 'string' ? item.tokenHash : ''
    if (!/^[a-f0-9]{8,32}$/u.test(id) || !/^[a-f0-9]{64}$/u.test(tokenHash)) continue
    devices.push({
      id,
      name: typeof item.name === 'string' && item.name.length > 0 ? item.name.slice(0, 64) : '未命名设备',
      platform: typeof item.platform === 'string' ? item.platform.slice(0, 40) : 'unknown',
      tokenHash,
      createdAt: Number.isFinite(item.createdAt) ? item.createdAt : Date.now(),
      lastSeenAt: Number.isFinite(item.lastSeenAt) ? item.lastSeenAt : 0,
      lastIp: typeof item.lastIp === 'string' ? item.lastIp.slice(0, 64) : '',
      userAgent: typeof item.userAgent === 'string' ? item.userAgent.slice(0, 240) : '',
      revokedAt: Number.isFinite(item.revokedAt) ? item.revokedAt : null,
    })
  }
  return devices
}

/**
 * One installation's paired-device catalog.
 */
export class PhoneRemoteState {
  /**
   * @param {string} dataDir - state directory.
   */
  constructor(dataDir) {
    this.dataDir = dataDir
    this.file = join(dataDir, 'state.json')
    this.devices = []
    this.loaded = false
    this.flushTimer = null
    this.lastFlush = 0
  }

  /**
   * Load the state document, creating an empty one on first use.
   * @returns {Promise<PhoneRemoteState>} this catalog.
   */
  static async open(dataDir) {
    const state = new PhoneRemoteState(dataDir)
    try {
      const text = await readFile(state.file, 'utf8')
      const parsed = JSON.parse(text)
      state.devices = sanitizeDevices(parsed?.devices)
    } catch (error) {
      if (error?.code !== 'ENOENT') {
        state.devices = []
      }
    }
    state.loaded = true
    if (!(await fileExists(state.file))) await state.flush()
    return state
  }

  /**
   * Persist the catalog immediately.
   * @returns {Promise<void>}
   */
  async flush() {
    this.lastFlush = Date.now()
    if (this.flushTimer !== null) {
      clearTimeout(this.flushTimer)
      this.flushTimer = null
    }
    await writePrivateJson(this.file, {
      version: STATE_VERSION,
      updatedAt: Date.now(),
      devices: this.devices,
    })
  }

  /**
   * Persist at most once per interval; a later call inside the window leaves
   * the existing timer in place.
   * @param {number} [delayMs] - debounce delay.
   */
  scheduleFlush(delayMs = 1500) {
    if (this.flushTimer !== null) return
    this.flushTimer = setTimeout(() => {
      this.flushTimer = null
      this.flush().catch(() => {})
    }, delayMs)
    this.flushTimer.unref?.()
  }

  /**
   * Register a fresh paired device.
   * @param {{name: string, platform: string, ip: string, userAgent: string}} meta - device details.
   * @returns {{device: object, token: string, cookieValue: string}} issued credential.
   */
  createDevice(meta) {
    const id = randomBytes(DEVICE_ID_BYTES).toString('hex')
    const token = randomBytes(DEVICE_SECRET_BYTES).toString('base64url')
    const device = {
      id,
      name: normalizeDeviceName(meta.name),
      platform: normalizePlatform(meta.platform),
      tokenHash: hashSecret(token),
      createdAt: Date.now(),
      lastSeenAt: Date.now(),
      lastIp: typeof meta.ip === 'string' ? meta.ip.slice(0, 64) : '',
      userAgent: typeof meta.userAgent === 'string' ? meta.userAgent.slice(0, 240) : '',
      revokedAt: null,
    }
    this.devices.push(device)
    this.scheduleFlush(0)
    return { device, token, cookieValue: `${id}.${token}` }
  }

  /**
   * Resolve a cookie value to its live device record.
   * @param {string} value - `<id>.<secret>`.
   * @returns {object|undefined} live device.
   */
  verify(value) {
    if (typeof value !== 'string') return undefined
    const at = value.indexOf('.')
    if (at <= 0) return undefined
    const id = value.slice(0, at)
    const secret = value.slice(at + 1)
    const device = this.devices.find((item) => item.id === id)
    if (device === undefined || device.revokedAt !== null) return undefined
    if (!secretMatches(secret, device.tokenHash)) return undefined
    return device
  }

  /**
   * Record one authenticated request without writing on every call.
   * @param {object} device - live device.
   * @param {{ip?: string, userAgent?: string}} meta - request facts.
   */
  touch(device, meta) {
    const now = Date.now()
    device.lastSeenAt = now
    if (typeof meta.ip === 'string' && meta.ip.length > 0) device.lastIp = meta.ip.slice(0, 64)
    if (typeof meta.userAgent === 'string' && meta.userAgent.length > 0) device.userAgent = meta.userAgent.slice(0, 240)
    this.scheduleFlush()
  }

  /**
   * Revoke one device. Its cookie stops validating on the next request.
   * @param {string} id - device id.
   * @returns {boolean} whether a live device was revoked.
   */
  revoke(id) {
    const device = this.devices.find((item) => item.id === id)
    if (device === undefined || device.revokedAt !== null) return false
    device.revokedAt = Date.now()
    this.scheduleFlush(0)
    return true
  }

  /**
   * Public, secret-free projection of every device.
   * @returns {object[]} device views, newest first.
   */
  views() {
    return [...this.devices]
      .sort((left, right) => right.createdAt - left.createdAt)
      .map((device) => ({
        id: device.id,
        name: device.name,
        platform: device.platform,
        createdAt: device.createdAt,
        lastSeenAt: device.lastSeenAt,
        lastIp: device.lastIp,
        revokedAt: device.revokedAt,
        online: device.revokedAt === null && Date.now() - device.lastSeenAt < 120_000,
      }))
  }

  /**
   * Count live devices.
   * @returns {number} live device count.
   */
  count() {
    return this.devices.filter((device) => device.revokedAt === null).length
  }
}

/**
 * Whether a path exists.
 * @param {string} file - path.
 * @returns {Promise<boolean>} existence.
 */
async function fileExists(file) {
  try {
    await readFile(file)
    return true
  } catch {
    return false
  }
}

/**
 * Trim and bound a user-supplied device name.
 * @param {unknown} value - raw name.
 * @returns {string} display name.
 */
export function normalizeDeviceName(value) {
  const text = typeof value === 'string' ? value.trim() : ''
  if (text.length === 0) return '未命名设备'
  return text.slice(0, 40)
}

/**
 * Classify a User-Agent into the platform labels the settings page shows.
 * @param {string} userAgent - request User-Agent.
 * @returns {string} platform label.
 */
export function normalizePlatform(userAgent) {
  const ua = typeof userAgent === 'string' ? userAgent : ''
  if (/HarmonyOS|OpenHarmony/iu.test(ua)) return 'HarmonyOS'
  if (/Android/iu.test(ua)) return 'Android'
  if (/iPhone|iPad|iPod/iu.test(ua)) return 'iOS'
  if (/Windows/iu.test(ua)) return 'Windows'
  if (/Macintosh|Mac OS X/iu.test(ua)) return 'macOS'
  if (/Linux/iu.test(ua)) return 'Linux'
  return 'unknown'
}
