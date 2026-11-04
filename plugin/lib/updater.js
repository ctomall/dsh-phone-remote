/**
 * Online update support for the phone-remote plugin itself.
 *
 * A feed is a small JSON document:
 *   { version, url, sha256, notes }
 * `apply()` downloads the zip, verifies its SHA-256, extracts it, and swaps the
 * plugin directory. The running Host keeps the old module in memory, so the
 * caller must restart DSH for the new code to run.
 *
 * @module @local/phone-remote/updater
 */
import { createHash, createPublicKey, verify as verifySignature } from 'node:crypto'
import { execFile } from 'node:child_process'
import { mkdtemp, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const MAX_ARCHIVE_BYTES = 40 * 1024 * 1024
const INSTALL_DIR = dirname(dirname(fileURLToPath(import.meta.url)))
const UPDATE_PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzcsZnRGxY3+4KdnLgCH9cHu/joD7
VKxpaZfI45fDh4YSY3IBepXf4QHKKXZRP+UtI3YX5Mvs/sLjpDLKiVY3Cw==
-----END PUBLIC KEY-----`

/**
 * Verify an ECDSA P-256 manifest signature.
 * @param {object} feed - manifest fields.
 * @param {string} signature - base64 signature.
 * @param {string} publicKeyPem - PEM public key.
 * @returns {boolean} whether canonical fields verify.
 */
export function verifyFeed(feed, signature, publicKeyPem = UPDATE_PUBLIC_KEY) {
  const canonical = [feed.version, feed.url, String(feed.sha256 ?? '').toLowerCase(), feed.notes ?? ''].join('\n')
  try {
    return verifySignature('sha256', Buffer.from(canonical, 'utf8'), createPublicKey(publicKeyPem), Buffer.from(signature, 'base64'))
  } catch {
    return false
  }
}

/**
 * Compare dotted numeric versions; a prerelease suffix is ignored.
 * @param {string} left - version.
 * @param {string} right - version.
 * @returns {number} -1, 0, or 1.
 */
export function compareVersions(left, right) {
  const parse = (value) => String(value).split('-')[0].split('.').map((part) => Number.parseInt(part, 10) || 0)
  const a = parse(left)
  const b = parse(right)
  for (let i = 0; i < Math.max(a.length, b.length); i += 1) {
    const diff = (a[i] ?? 0) - (b[i] ?? 0)
    if (diff !== 0) return diff > 0 ? 1 : -1
  }
  return 0
}

/**
 * Check and apply one plugin update from an HTTPS feed.
 */
export class UpdateManager {
  /**
   * @param {object} options - manager options.
   * @param {string} options.currentVersion - installed plugin version.
   * @param {string} [options.feedUrl] - HTTPS feed URL; empty disables checks.
   * @param {string} [options.installDir] - plugin directory to replace.
   * @param {object} [options.logger] - host logger.
   * @param {typeof fetch} [options.fetchImpl] - fetch override for tests.
   */
  constructor({ currentVersion, feedUrl = '', installDir = INSTALL_DIR, publicKey = UPDATE_PUBLIC_KEY, logger, fetchImpl = globalThis.fetch }) {
    this.currentVersion = currentVersion
    this.feedUrl = feedUrl
    this.installDir = installDir
    this.publicKey = publicKey
    this.logger = logger
    this.fetchImpl = fetchImpl
  }

  /**
   * Fetch the feed and report whether a newer version exists.
   * @returns {Promise<object>} status.
   */
  async check() {
    if (!this.feedUrl) {
      return { currentVersion: this.currentVersion, available: false, error: '未配置更新源', feedUrl: '' }
    }
    if (!/^https:\/\//u.test(this.feedUrl)) {
      return { currentVersion: this.currentVersion, available: false, error: '更新源必须是 HTTPS', feedUrl: this.feedUrl }
    }
    try {
      const response = await this.fetchImpl(this.feedUrl, {
        headers: { 'cache-control': 'no-cache' },
        signal: AbortSignal.timeout(15_000),
      })
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const feed = await response.json()
      const version = typeof feed.version === 'string' ? feed.version.trim() : ''
      const url = typeof feed.url === 'string' ? feed.url.trim() : ''
      if (version.length === 0 || !/^https:\/\//u.test(url)) throw new Error('更新源格式无效')
      if (typeof feed.signature !== 'string' || !verifyFeed(feed, feed.signature, this.publicKey)) {
        throw new Error('更新清单签名无效')
      }
      return {
        currentVersion: this.currentVersion,
        latestVersion: version,
        available: compareVersions(version, this.currentVersion) > 0,
        notes: typeof feed.notes === 'string' ? feed.notes : '',
        url,
        sha256: typeof feed.sha256 === 'string' ? feed.sha256.toLowerCase() : '',
        feedUrl: this.feedUrl,
      }
    } catch (error) {
      return {
        currentVersion: this.currentVersion,
        available: false,
        error: error instanceof Error ? error.message : String(error),
        feedUrl: this.feedUrl,
      }
    }
  }

  /**
   * Download, verify, extract, and swap the plugin directory.
   * @returns {Promise<object>} apply result; `restartRequired` is always true.
   */
  async apply() {
    const status = await this.check()
    if (status.error) throw new Error(status.error)
    if (!status.available) throw new Error(`当前已是最新版本 ${this.currentVersion}`)
    const response = await this.fetchImpl(status.url, { signal: AbortSignal.timeout(60_000) })
    if (!response.ok) throw new Error(`下载更新失败：HTTP ${response.status}`)
    const bytes = new Uint8Array(await response.arrayBuffer())
    if (bytes.byteLength === 0 || bytes.byteLength > MAX_ARCHIVE_BYTES) throw new Error('更新包大小无效')
    if (status.sha256.length > 0) {
      const digest = createHash('sha256').update(bytes).digest('hex')
      if (digest !== status.sha256) throw new Error('更新包 SHA-256 校验失败')
    }

    const work = await mkdtemp(join(tmpdir(), 'phone-remote-update-'))
    const archive = join(work, 'update.zip')
    const extracted = join(work, 'extracted')
    try {
      await writeFile(archive, bytes)
      await execFileAsync('unzip', ['-q', archive, '-d', extracted], { timeout: 60_000 })
      const root = await findPackageRoot(extracted)
      const manifest = JSON.parse(await readFile(join(root, 'package.json'), 'utf8'))
      if (typeof manifest.version !== 'string' || compareVersions(manifest.version, status.latestVersion) !== 0) {
        throw new Error(`更新包版本不匹配：${String(manifest.version)}`)
      }
      await validateInstall(root)
      const backup = `${this.installDir}.bak-${Date.now()}`
      await rename(this.installDir, backup)
      try {
        await rename(root, this.installDir)
        await validateInstall(this.installDir)
      } catch (error) {
        await rm(this.installDir, { recursive: true, force: true }).catch(() => {})
        await rename(backup, this.installDir).catch(() => {})
        throw error
      }
      await this.saveBackup(backup, this.currentVersion)
      this.logger?.info?.(`phone-remote：已安装 ${status.latestVersion}，等待重启 DSH 生效。`)
      return {
        installedVersion: status.latestVersion,
        previousVersion: this.currentVersion,
        restartRequired: true,
        backup,
      }
    } finally {
      await rm(work, { recursive: true, force: true }).catch(() => {})
    }
  }

  /**
   * Restore the previous plugin directory written by the last successful apply.
   * @returns {Promise<object>} rollback result.
   */
  async rollback() {
    const state = await this.readBackup()
    if (state === null) throw new Error('没有可回滚的更新')
    try { await stat(state.backup) } catch { throw new Error('备份目录不存在') }
    const failed = `${this.installDir}.failed-${Date.now()}`
    await rename(this.installDir, failed)
    try {
      await rename(state.backup, this.installDir)
      await validateInstall(this.installDir)
    } catch (error) {
      await rm(this.installDir, { recursive: true, force: true }).catch(() => {})
      await rename(failed, this.installDir).catch(() => {})
      throw error
    }
    await rm(failed, { recursive: true, force: true }).catch(() => {})
    await rm(this.backupStatePath(), { force: true }).catch(() => {})
    return { rolledBackTo: state.previousVersion, restartRequired: true }
  }

  /** @returns {string} sidecar path recording the last backup. */
  backupStatePath() {
    return join(dirname(this.installDir), '.phone-remote-update.json')
  }

  async saveBackup(backup, previousVersion) {
    await writeFile(this.backupStatePath(), JSON.stringify({ backup, previousVersion }), 'utf8')
  }

  async readBackup() {
    try {
      const value = JSON.parse(await readFile(this.backupStatePath(), 'utf8'))
      if (typeof value.backup !== 'string' || typeof value.previousVersion !== 'string') return null
      return value
    } catch {
      return null
    }
  }
}

/**
 * Validate a plugin tree by checking required files and JavaScript syntax.
 * @param {string} root - candidate plugin directory.
 * @returns {Promise<void>}
 */
async function validateInstall(root) {
  const required = ['package.json', 'index.js', 'client.js', 'cordis.patch.yml', 'lib/routes.js', 'lib/updater.js']
  for (const rel of required) await stat(join(root, rel))
  for (const rel of ['index.js', 'client.js', 'lib/routes.js', 'lib/updater.js']) {
    await execFileAsync(process.execPath, ['--check', join(root, rel)], { timeout: 20_000 })
  }
}

/**
 * Find the directory containing package.json in an extracted archive.
 * @param {string} extracted - extraction root.
 * @returns {Promise<string>} package root.
 */
async function findPackageRoot(extracted) {
  try {
    await stat(join(extracted, 'package.json'))
    return extracted
  } catch {
    const { readdir } = await import('node:fs/promises')
    const children = await readdir(extracted, { withFileTypes: true })
    for (const child of children) {
      if (!child.isDirectory()) continue
      const candidate = join(extracted, child.name)
      try {
        await stat(join(candidate, 'package.json'))
        return candidate
      } catch {
        // continue
      }
    }
    throw new Error('更新包缺少 package.json')
  }
}
