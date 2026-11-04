/**
 * TLS material for the public phone entry: either operator-supplied PEM files
 * or a self-signed certificate generated on first use for the addresses the
 * phone will dial. Generation goes through the system `openssl` because Node's
 * `crypto` cannot mint X.509 certificates.
 *
 * @module @local/phone-remote/tls
 */
import { execFile } from 'node:child_process'
import { chmod, mkdir, readFile, writeFile } from 'node:fs/promises'
import { isIP } from 'node:net'
import { dirname, join } from 'node:path'
import { promisify } from 'node:util'
import { X509Certificate } from 'node:crypto'
import { createSecureContext } from 'node:tls'

const execFileAsync = promisify(execFile)
const RENEW_BEFORE_MS = 7 * 24 * 60 * 60 * 1000

/**
 * Read and validate one certificate/key pair.
 * @param {string} certFile - PEM chain path.
 * @param {string} keyFile - unencrypted PEM key path.
 * @returns {Promise<{cert: string, key: string, fingerprint256: string, expiresAt: number}>} material.
 */
async function readPair(certFile, keyFile) {
  let cert
  let key
  try {
    ;[cert, key] = await Promise.all([readFile(certFile, 'utf8'), readFile(keyFile, 'utf8')])
  } catch (error) {
    throw new Error(`无法读取证书或私钥：${error instanceof Error ? error.message : String(error)}`)
  }
  if (!cert.includes('BEGIN CERTIFICATE') || !key.includes('PRIVATE KEY')) {
    throw new Error('证书或私钥不是 PEM 格式。')
  }
  // createSecureContext verifies the key belongs to the certificate.
  createSecureContext({ cert, key })
  const x509 = new X509Certificate(cert)
  return {
    cert,
    key,
    fingerprint256: x509.fingerprint256,
    expiresAt: Date.parse(x509.validTo),
  }
}

/**
 * Compose the SAN extension value for the addresses a phone will use.
 * @param {string[]} hosts - IP literals and DNS names.
 * @returns {string} OpenSSL `subjectAltName` text.
 */
function sanValue(hosts) {
  const entries = []
  const seen = new Set()
  for (const raw of hosts) {
    const host = String(raw ?? '').trim()
    if (host.length === 0 || host.length > 253) continue
    const kind = isIP(host)
    const entry = kind === 4 ? `IP:${host}` : kind === 6 ? `IP:${host}` : /^[A-Za-z0-9.*-]+$/u.test(host) ? `DNS:${host}` : ''
    if (entry.length === 0 || seen.has(entry)) continue
    seen.add(entry)
    entries.push(entry)
  }
  if (entries.length === 0) entries.push('DNS:localhost', 'IP:127.0.0.1')
  return entries.join(',')
}

/**
 * Whether an existing certificate still covers the requested addresses.
 * @param {string} cert - PEM text.
 * @param {string} expectedSans - SAN value we would generate now.
 * @returns {boolean} whether the certificate is still usable.
 */
function coversHosts(cert, expectedSans) {
  try {
    const x509 = new X509Certificate(cert)
    if (Date.parse(x509.validTo) - Date.now() < RENEW_BEFORE_MS) return false
    const actual = x509.subjectAltName ?? ''
    const wanted = expectedSans.split(',').map((entry) => entry.trim())
    return wanted.every((entry) => actual.includes(entry.replace(/^IP:/u, 'IP Address:')))
  } catch {
    return false
  }
}

/**
 * Generate a self-signed certificate with `openssl`.
 * @param {string} certFile - output certificate path.
 * @param {string} keyFile - output key path.
 * @param {string} sans - SAN extension text.
 * @returns {Promise<void>}
 */
async function generateSelfSigned(certFile, keyFile, sans) {
  await mkdir(dirname(certFile), { recursive: true, mode: 0o700 })
  const args = [
    'req',
    '-x509',
    '-newkey',
    'rsa:2048',
    '-sha256',
    '-days',
    '825',
    '-nodes',
    '-keyout',
    keyFile,
    '-out',
    certFile,
    '-subj',
    '/CN=DSH Phone Remote',
    '-addext',
    `subjectAltName=${sans}`,
  ]
  try {
    await execFileAsync('openssl', args, { timeout: 30_000 })
  } catch (error) {
    const detail = error?.stderr?.trim() || error?.message || String(error)
    throw new Error(`生成自签名证书失败（需要系统 openssl）：${detail}`)
  }
  await chmod(keyFile, 0o600)
  await chmod(certFile, 0o644)
}

/**
 * Resolve the TLS material for the gateway listener.
 * @param {object} options - certificate configuration.
 * @param {'self-signed'|'files'|'off'} options.mode - TLS mode.
 * @param {string} [options.certFile] - supplied chain path.
 * @param {string} [options.keyFile] - supplied key path.
 * @param {string[]} options.hosts - addresses the phone will dial.
 * @param {string} options.dataDir - plugin data directory.
 * @returns {Promise<null|{cert: string, key: string, fingerprint256: string, expiresAt: number, certFile: string, keyFile: string, mode: string, generated: boolean}>} material or null for HTTP.
 */
export async function ensureTls({ mode, certFile, keyFile, hosts, dataDir }) {
  if (mode === 'off') return null
  if (mode === 'files') {
    if (typeof certFile !== 'string' || certFile.trim().length === 0 || typeof keyFile !== 'string' || keyFile.trim().length === 0) {
      throw new Error('tlsMode 为 files 时必须同时配置 certFile 与 keyFile。')
    }
    const pair = await readPair(certFile.trim(), keyFile.trim())
    return { ...pair, certFile: certFile.trim(), keyFile: keyFile.trim(), mode: 'files', generated: false }
  }

  const directory = join(dataDir, 'tls')
  const certificate = join(directory, 'remote-cert.pem')
  const key = join(directory, 'remote-key.pem')
  const metaFile = join(directory, 'cert-meta.json')
  const sans = sanValue(hosts)
  let generated = false

  let existing = null
  try {
    existing = await readFile(certificate, 'utf8')
  } catch {
    existing = null
  }

  if (existing === null || !coversHosts(existing, sans)) {
    await generateSelfSigned(certificate, key, sans)
    await writeFile(metaFile, `${JSON.stringify({ sans, createdAt: Date.now() }, null, 2)}\n`, { mode: 0o600 })
    generated = true
  }

  const pair = await readPair(certificate, key)
  return { ...pair, certFile: certificate, keyFile: key, mode: 'self-signed', generated }
}

/**
 * Human-readable certificate expiry for the settings page.
 * @param {number} expiresAt - epoch milliseconds.
 * @returns {string} ISO date or empty string.
 */
export function formatExpiry(expiresAt) {
  if (!Number.isFinite(expiresAt) || expiresAt <= 0) return ''
  return new Date(expiresAt).toISOString().slice(0, 10)
}
