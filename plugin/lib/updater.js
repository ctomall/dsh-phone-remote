/**
 * Online update support for the phone-remote plugin itself.
 *
 * Two strategies, picked from where the plugin is installed:
 *
 * - `git`: the plugin directory lives inside a git work tree — the usual
 *   `link:` install. Upgrading is a fast-forward merge from the tracked
 *   upstream branch, followed by the same install validation the archive
 *   strategy uses; any failure resets the work tree to the pre-pull commit.
 * - `zip`: anything else. A signed HTTPS feed is fetched, verified (ECDSA
 *   P-256 plus SHA-256), extracted, validated, and swapped into place while
 *   the previous directory is kept as a backup.
 *
 * Both strategies record what they replaced in `.phone-remote-update.json`
 * next to the plugin directory, so {@link UpdateManager#rollback} can undo the
 * last successful upgrade (a git reset for git installs, a directory swap for
 * archives). The running Host keeps the old module in memory, so every result
 * carries `restartRequired: true`.
 *
 * @module @local/phone-remote/updater
 */
import { createHash, createPublicKey, verify as verifySignature } from 'node:crypto'
import { execFile } from 'node:child_process'
import { mkdtemp, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const MAX_ARCHIVE_BYTES = 40 * 1024 * 1024
const GIT_READ_TIMEOUT_MS = 30_000
const GIT_APPLY_TIMEOUT_MS = 60_000

/** The installed plugin directory (`…/plugin`). */
export const INSTALL_DIR = dirname(dirname(fileURLToPath(import.meta.url)))

/**
 * ECDSA P-256 public key (PEM) that signs released update feeds. It is the
 * same key the Android client pins for its own manifest.
 */
export const UPDATE_PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzcsZnRGxY3+4KdnLgCH9cHu/joD7
VKxpaZfI45fDh4YSY3IBepXf4QHKKXZRP+UtI3YX5Mvs/sLjpDLKiVY3Cw==
-----END PUBLIC KEY-----`

/**
 * Accept either a PEM key or its bare base64 SPKI body.
 * @param {string} [key] - configured key.
 * @returns {string} PEM public key.
 */
function toPem(key) {
  const value = typeof key === 'string' ? key.trim() : ''
  if (value.length === 0) return UPDATE_PUBLIC_KEY
  if (value.includes('BEGIN PUBLIC KEY')) return value
  const body = value.replace(/\s+/gu, '').match(/.{1,64}/gu)?.join('\n') ?? ''
  return `-----BEGIN PUBLIC KEY-----\n${body}\n-----END PUBLIC KEY-----`
}

/**
 * The exact UTF-8 bytes a feed signature covers.
 * @param {object} feed - manifest fields.
 * @returns {string} canonical payload.
 */
export function canonicalFeedPayload(feed) {
  const sha256 = typeof feed?.sha256 === 'string' ? feed.sha256.trim().toLowerCase() : ''
  return [feed?.version ?? '', feed?.url ?? '', sha256, feed?.notes ?? ''].join('\n')
}

/**
 * Verify an ECDSA P-256 / SHA-256 / DER manifest signature.
 * @param {object} feed - manifest fields.
 * @param {string} signature - base64 signature.
 * @param {string} [publicKey] - PEM or bare base64 public key.
 * @returns {boolean} whether the canonical fields verify.
 */
export function verifyFeed(feed, signature, publicKey = UPDATE_PUBLIC_KEY) {
  try {
    return verifySignature(
      'sha256',
      Buffer.from(canonicalFeedPayload(feed), 'utf8'),
      createPublicKey(toPem(publicKey)),
      Buffer.from(signature, 'base64'),
    )
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
 * Run git in one directory and return trimmed stdout.
 * @param {string} cwd - repository directory.
 * @param {string[]} args - git arguments.
 * @param {number} [timeout] - timeout in milliseconds.
 * @returns {Promise<string>} stdout.
 */
async function git(cwd, args, timeout = GIT_READ_TIMEOUT_MS) {
  const { stdout } = await execFileAsync('git', ['-C', cwd, ...args], { timeout, maxBuffer: 8 * 1024 * 1024 })
  return stdout.trim()
}

function errorMessage(error) {
  return error instanceof Error ? error.message : String(error)
}

/**
 * Check and apply one plugin update, over git or a signed archive.
 */
export class UpdateManager {
  /**
   * @param {object} options - manager options.
   * @param {string} options.currentVersion - installed plugin version.
   * @param {string} [options.feedUrl] - HTTPS feed URL; empty disables archive updates.
   * @param {string} [options.installDir] - plugin directory to upgrade.
   * @param {string} [options.publicKey] - feed signing key override (PEM or base64).
   * @param {boolean} [options.requireSignature] - reject unsigned feeds (default true).
   * @param {'auto'|'git'|'zip'} [options.mode] - force one strategy.
   * @param {string} [options.remote] - git remote name (default `origin`).
   * @param {object} [options.logger] - host logger.
   * @param {typeof fetch} [options.fetchImpl] - fetch override for tests.
   */
  constructor({
    currentVersion,
    feedUrl = '',
    installDir = INSTALL_DIR,
    publicKey = UPDATE_PUBLIC_KEY,
    requireSignature = true,
    mode = 'auto',
    remote = 'origin',
    logger,
    fetchImpl = globalThis.fetch,
  }) {
    this.currentVersion = currentVersion
    this.feedUrl = feedUrl
    this.installDir = resolve(installDir)
    this.publicKey = publicKey
    this.requireSignature = requireSignature !== false
    this.mode = mode === 'git' || mode === 'zip' ? mode : 'auto'
    this.remote = remote
    this.logger = logger
    this.fetchImpl = fetchImpl
    this.gitRootPromise = null
  }

  /**
   * Which strategy this installation upgrades with.
   * @returns {Promise<'git'|'zip'>} strategy.
   */
  async resolveMode() {
    if (this.mode !== 'auto') return this.mode
    return (await this.findGitRoot()) ? 'git' : 'zip'
  }

  /**
   * Find the enclosing git work tree, if any.
   * @returns {Promise<string|null>} repository root.
   */
  async findGitRoot() {
    this.gitRootPromise ??= (async () => {
      let dir = this.installDir
      for (;;) {
        try {
          await stat(join(dir, '.git'))
          return dir
        } catch {
          // keep walking up
        }
        const parent = dirname(dir)
        if (parent === dir) return null
        dir = parent
      }
    })()
    return this.gitRootPromise
  }

  /**
   * Repository-relative manifest path, in git's POSIX form.
   * @param {string} repo - repository root.
   * @returns {string} manifest path.
   */
  manifestPath(repo) {
    const rel = relative(repo, this.installDir)
    if (rel.length === 0) return 'package.json'
    return `${rel.split(sep).join('/')}/package.json`
  }

  /**
   * Check whether a newer version is available.
   * @returns {Promise<object>} status.
   */
  async check() {
    const mode = await this.resolveMode()
    return mode === 'git' ? this.checkGit(mode) : this.checkFeed(mode)
  }

  /**
   * Git strategy status: is the tracked upstream branch ahead?
   * @param {'git'} mode - resolved strategy.
   * @returns {Promise<object>} status.
   */
  async checkGit(mode) {
    const base = { currentVersion: this.currentVersion, mode, available: false, signed: false, feedUrl: '' }
    const repo = await this.findGitRoot()
    if (!repo) return { ...base, error: '未找到 git 工作区' }
    try {
      const remoteUrl = await git(repo, ['remote', 'get-url', this.remote])
      const branch = await git(repo, ['rev-parse', '--abbrev-ref', 'HEAD'])
      if (branch === 'HEAD') return { ...base, remoteUrl, error: 'git 处于 detached HEAD，无法自动升级' }
      const upstream = await git(repo, ['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{u}'])
      await git(repo, ['fetch', '--prune', this.remote])
      const commitsBehind = Number(await git(repo, ['rev-list', '--count', `HEAD..${upstream}`]))
      const dirty = (await git(repo, ['status', '--porcelain', '--untracked-files=no'])).length > 0
      const localPackage = JSON.parse(await readFile(join(this.installDir, 'package.json'), 'utf8'))
      const upstreamPackage = JSON.parse(await git(repo, ['show', `${upstream}:${this.manifestPath(repo)}`]))
      if (upstreamPackage.name !== localPackage.name) {
        throw new Error(`上游包名 ${String(upstreamPackage.name)} 与已安装的 ${String(localPackage.name)} 不一致`)
      }
      const notes = await git(repo, ['log', '-1', '--format=%s', upstream]).catch(() => '')
      return {
        ...base,
        available: commitsBehind > 0,
        latestVersion: typeof upstreamPackage.version === 'string' ? upstreamPackage.version : this.currentVersion,
        commitsBehind,
        branch,
        upstream,
        remoteUrl,
        dirty,
        notes,
        install: 'git',
      }
    } catch (error) {
      return { ...base, error: errorMessage(error) }
    }
  }

  /**
   * Archive strategy status: signed feed version compared to installed.
   * @param {'zip'} mode - resolved strategy.
   * @returns {Promise<object>} status.
   */
  async checkFeed(mode) {
    const base = { currentVersion: this.currentVersion, mode, available: false, signed: false }
    if (this.feedUrl.length === 0) return { ...base, error: '未配置更新源', feedUrl: '' }
    if (!/^https:\/\//u.test(this.feedUrl)) return { ...base, error: '更新源必须是 HTTPS', feedUrl: this.feedUrl }
    try {
      const response = await this.fetchImpl(this.feedUrl, {
        headers: { 'cache-control': 'no-cache' },
        signal: AbortSignal.timeout(15_000),
      })
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const feed = await response.json()
      const version = typeof feed?.version === 'string' ? feed.version.trim() : ''
      const url = typeof feed?.url === 'string' ? feed.url.trim() : ''
      if (version.length === 0 || !/^https:\/\//u.test(url)) throw new Error('更新源格式无效')
      const signed = typeof feed.signature === 'string' && feed.signature.trim().length > 0
      if (!signed && this.requireSignature) throw new Error('更新清单缺少签名')
      if (signed && !verifyFeed(feed, feed.signature, this.publicKey)) throw new Error('更新清单签名无效')
      return {
        ...base,
        signed,
        latestVersion: version,
        available: compareVersions(version, this.currentVersion) > 0,
        notes: typeof feed.notes === 'string' ? feed.notes : '',
        url,
        sha256: typeof feed.sha256 === 'string' ? feed.sha256.trim().toLowerCase() : '',
        feedUrl: this.feedUrl,
        install: 'zip',
      }
    } catch (error) {
      return { ...base, error: errorMessage(error), feedUrl: this.feedUrl }
    }
  }

  /**
   * Apply the available update.
   * @returns {Promise<object>} result; `restartRequired` is always true.
   */
  async apply() {
    const status = await this.check()
    if (status.error) throw new Error(status.error)
    if (!status.available) throw new Error(`当前已是最新版本 ${this.currentVersion}`)
    return status.mode === 'git' ? this.applyGit(status) : this.applyArchive(status)
  }

  /**
   * Fast-forward the git work tree, then validate the checked-out plugin.
   * @param {object} status - result of `checkGit`.
   * @returns {Promise<object>} result.
   */
  async applyGit(status) {
    const repo = await this.findGitRoot()
    if (!repo) throw new Error('未找到 git 工作区')
    if (status.dirty) throw new Error('插件目录有未提交改动，已中止升级；请先提交或 stash。')
    const head = await git(repo, ['rev-parse', 'HEAD'])
    try {
      await git(repo, ['merge', '--ff-only', status.upstream], GIT_APPLY_TIMEOUT_MS)
      await validateInstall(this.installDir)
      const installed = JSON.parse(await readFile(join(this.installDir, 'package.json'), 'utf8'))
      if (typeof installed.version !== 'string' || installed.version.length === 0) {
        throw new Error('升级后缺少 package.json 版本')
      }
      if (compareVersions(installed.version, this.currentVersion) < 0) {
        throw new Error(`升级后版本 ${installed.version} 低于当前 ${this.currentVersion}`)
      }
      const commit = await git(repo, ['rev-parse', 'HEAD'])
      await this.saveBackup({ mode: 'git', repo, previousCommit: head, previousVersion: this.currentVersion })
      this.logger?.info?.(`phone-remote：已从 ${status.upstream} 快进到 ${installed.version}，重启 DSH 后生效。`)
      return {
        mode: 'git',
        installedVersion: installed.version,
        previousVersion: this.currentVersion,
        previousCommit: head,
        commit,
        upstream: status.upstream,
        restartRequired: true,
        rollback: `git -C ${repo} reset --hard ${head}`,
      }
    } catch (error) {
      await git(repo, ['reset', '--hard', head]).catch(() => {})
      throw new Error(`Git 升级失败，已回滚到 ${head}：${errorMessage(error)}`)
    }
  }

  /**
   * Download, verify, extract, validate, and swap the plugin directory.
   * @param {object} status - result of `checkFeed`.
   * @returns {Promise<object>} result.
   */
  async applyArchive(status) {
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
      await this.saveBackup({ mode: 'zip', backup, previousVersion: this.currentVersion })
      this.logger?.info?.(`phone-remote：已安装 ${status.latestVersion}，等待重启 DSH 生效。`)
      return {
        mode: 'zip',
        installedVersion: status.latestVersion,
        previousVersion: this.currentVersion,
        restartRequired: true,
        backup,
        rollback: `rm -rf "${this.installDir}" && mv "${backup}" "${this.installDir}"`,
      }
    } finally {
      await rm(work, { recursive: true, force: true }).catch(() => {})
    }
  }

  /**
   * Undo the last successful upgrade (git reset or directory swap).
   * @returns {Promise<object>} rollback result.
   */
  async rollback() {
    const state = await this.readBackup()
    if (state === null) throw new Error('没有可回滚的更新')
    if (state.mode === 'git') {
      const repo = await this.findGitRoot()
      if (!repo) throw new Error('未找到 git 工作区')
      try { await git(repo, ['cat-file', '-e', `${state.previousCommit}^{commit}`]) } catch { throw new Error('回滚目标提交不存在') }
      await git(repo, ['reset', '--hard', state.previousCommit])
      await validateInstall(this.installDir)
      await rm(this.backupStatePath(), { force: true }).catch(() => {})
      this.logger?.info?.(`phone-remote：已回滚到 ${state.previousCommit}，重启 DSH 后生效。`)
      return { mode: 'git', rolledBackTo: state.previousVersion, commit: state.previousCommit, restartRequired: true }
    }
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
    return { mode: 'zip', rolledBackTo: state.previousVersion, restartRequired: true }
  }

  /** @returns {string} sidecar path recording the last backup. */
  backupStatePath() {
    return join(dirname(this.installDir), '.phone-remote-update.json')
  }

  /**
   * @param {object} state - rollback record.
   * @returns {Promise<void>}
   */
  async saveBackup(state) {
    await writeFile(this.backupStatePath(), JSON.stringify(state), 'utf8')
  }

  /**
   * @returns {Promise<object|null>} last rollback record.
   */
  async readBackup() {
    try {
      const value = JSON.parse(await readFile(this.backupStatePath(), 'utf8'))
      if (typeof value.previousVersion !== 'string') return null
      if (value.mode === 'git' && typeof value.previousCommit === 'string') return value
      if (typeof value.backup === 'string') return { ...value, mode: value.mode ?? 'zip' }
      return null
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
