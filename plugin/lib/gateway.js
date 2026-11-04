/**
 * The public phone gateway: a small HTTPS listener in front of the local DSH
 * Web server. Every path except the pairing page and its health probe requires
 * a paired-device cookie, and all traffic is reverse-proxied to DSH over the
 * loopback authority rewrite. Pairing codes are minted by the authenticated
 * settings page, so opening the public port alone grants nothing.
 *
 * @module @local/phone-remote/gateway
 */
import http from 'node:http'
import https from 'node:https'
import net from 'node:net'
import os from 'node:os'
import { URL } from 'node:url'
import { ensureTls, formatExpiry } from './tls.js'
import { forwardRequest, forwardUpgrade } from './proxy.js'
import { PAGE_HEADERS, renderPairPage } from './pages.js'
import { normalizeCode } from './pairing.js'
import { normalizeDeviceName, normalizePlatform } from './state.js'
import { qrDataUrl } from './qr.js'

const VERSION = '1.2.0'
const MAX_BODY_BYTES = 64 * 1024
const RATE_WINDOW_MS = 10 * 60 * 1000
const RATE_MAX_ATTEMPTS = 15
const PUBLIC_IP_TTL_MS = 10 * 60 * 1000

/**
 * Normalize raw row config into the gateway's accepted options.
 * @param {unknown} raw - config object from the Loader row.
 * @returns {object} normalized config.
 */
export function normalizeConfig(raw) {
  const input = typeof raw === 'object' && raw !== null ? raw : {}
  const tlsMode = input.tlsMode === 'files' || input.tlsMode === 'off' ? input.tlsMode : 'self-signed'
  const port = Number.isInteger(input.port) ? input.port : Number(input.port)
  const publicUrl = typeof input.publicUrl === 'string' ? input.publicUrl.trim() : ''
  let basePath = normalizeBasePath(input.basePath)
  if (basePath === '' && publicUrl.length > 0) {
    try {
      basePath = normalizeBasePath(new URL(publicUrl).pathname)
    } catch {
      basePath = ''
    }
  }
  return {
    enabled: input.enabled !== false,
    host: typeof input.host === 'string' && input.host.trim().length > 0 ? input.host.trim() : '0.0.0.0',
    port: Number.isInteger(port) && port >= 0 && port <= 65_535 ? port : 8443,
    tlsMode,
    certFile: typeof input.certFile === 'string' ? input.certFile.trim() : '',
    keyFile: typeof input.keyFile === 'string' ? input.keyFile.trim() : '',
    tlsHosts: Array.isArray(input.tlsHosts) ? input.tlsHosts.filter((item) => typeof item === 'string') : [],
    publicUrl,
    publicHost: typeof input.publicHost === 'string' ? input.publicHost.trim() : '',
    basePath,
    trustProxy: input.trustProxy === true,
    pairingTtlSec: input.pairingTtlSec,
    deviceMaxAgeDays: Number.isFinite(input.deviceMaxAgeDays) ? Math.min(365, Math.max(1, Math.round(input.deviceMaxAgeDays))) : 180,
    kind: input.kind === 'lan' ? 'lan' : 'public',
  }
}

/**
 * Normalize a mount prefix to `''` or `/segment[/segment]` with no trailing slash.
 * @param {unknown} value - configured base path.
 * @returns {string} normalized prefix.
 */
export function normalizeBasePath(value) {
  const raw = typeof value === 'string' ? value.trim() : ''
  if (raw.length === 0 || raw === '/') return ''
  const withSlash = raw.startsWith('/') ? raw : `/${raw}`
  const cleaned = withSlash.replace(/\/+$/u, '')
  if (cleaned === '' || cleaned.includes('//') || cleaned.includes('?') || cleaned.includes('#')) return ''
  return cleaned
}

/**
 * Format one address for a URL.
 * @param {string} host - IP or hostname.
 * @returns {string} URL host part.
 */
function urlHost(host) {
  return net.isIP(host) === 6 ? `[${host}]` : host
}

/**
 * Whether an address is a loopback literal or name.
 * @param {string} host - bind host.
 * @returns {boolean} loopback.
 */
function isLoopback(host) {
  const value = host.replace(/^\[|\]$/gu, '').split('%')[0]
  return value === 'localhost' || value === '::1' || /^127\./u.test(value)
}

/**
 * Probe one URL and return a compact result.
 * @param {string} target - absolute URL.
 * @param {{timeoutMs?: number, rejectUnauthorized?: boolean}} [options] - probe options.
 * @returns {Promise<{ok: boolean, status: number, ms: number, error?: string}>} probe result.
 */
function probe(target, options = {}) {
  return new Promise((resolve) => {
    const started = Date.now()
    let settled = false
    const finish = (value) => {
      if (settled) return
      settled = true
      resolve(value)
    }
    let parsed
    try {
      parsed = new URL(target)
    } catch {
      finish({ ok: false, status: 0, ms: 0, error: 'URL 无效' })
      return
    }
    const transport = parsed.protocol === 'https:' ? https : http
    const request = transport.request(
      {
        protocol: parsed.protocol,
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
        path: `${parsed.pathname}${parsed.search}`,
        method: 'GET',
        headers: { host: parsed.host, accept: 'text/plain' },
        rejectUnauthorized: options.rejectUnauthorized ?? true,
        timeout: options.timeoutMs ?? 6000,
      },
      (response) => {
        response.resume()
        response.on('end', () => finish({ ok: true, status: response.statusCode ?? 0, ms: Date.now() - started }))
      },
    )
    request.on('timeout', () => {
      request.destroy(new Error('超时'))
    })
    request.on('error', (error) => finish({ ok: false, status: 0, ms: Date.now() - started, error: error.message }))
    request.end()
  })
}

/**
 * One installation's public phone entry.
 */
export class PhoneGateway {
  /**
   * @param {object} options - dependencies.
   * @param {import('@deepseek-ai/cordis').Context} options.ctx - host context.
   * @param {object} options.config - normalized config.
   * @param {import('./state.js').PhoneRemoteState} options.state - device catalog.
   * @param {import('./pairing.js').PairingCodes} options.pairing - code table.
   * @param {object} [options.logger] - host logger.
   */
  constructor({ ctx, config, state, pairing, logger }) {
    this.ctx = ctx
    this.config = config
    this.state = state
    this.pairing = pairing
    this.logger = logger
    this.phase = 'stopped'
    this.error = null
    this.server = null
    this.tls = null
    this.upstream = null
    this.publicBase = ''
    this.addresses = { publicIp: '', source: 'unknown', lan: [], hosts: [] }
    this.startedAt = 0
    this.rate = new Map()
    this.ipCache = null
    this.launchTokenCache = undefined
    this.publicIpPending = null
    this.disposed = false
  }

  /**
   * Start listening. Failures are recorded in `status()` instead of escaping,
   * so a busy port never takes the whole Host down.
   * @returns {Promise<void>}
   */
  async start() {
    if (this.disposed) return
    if (!this.config.enabled) {
      this.phase = 'disabled'
      return
    }
    if (this.phase === 'running' || this.phase === 'starting') return
    this.phase = 'starting'
    this.error = null
    try {
      this.upstream = new URL(this.ctx.webServer.getOrigin())
      const addresses = await this.detectAddresses()
      this.addresses = addresses
      this.tls = await ensureTls({
        mode: this.config.tlsMode,
        certFile: this.config.certFile,
        keyFile: this.config.keyFile,
        hosts: addresses.hosts,
        dataDir: this.state.dataDir,
      })
      const handler = (request, response) => {
        this.handleRequest(request, response).catch((error) => {
          this.logger?.warn?.(`phone-remote：代理请求失败：${error.message}`)
          if (!response.headersSent) {
            response.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' })
          }
          response.end('网关内部错误')
        })
      }
      this.server = this.tls
        ? https.createServer({ key: this.tls.key, cert: this.tls.cert }, handler)
        : http.createServer(handler)
      this.server.on('upgrade', (request, socket, head) => {
        this.handleUpgrade(request, socket, head)
      })
      this.server.on('clientError', (_error, socket) => {
        if (!socket.destroyed) socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n')
      })
      await new Promise((resolve, reject) => {
        const onError = (error) => {
          this.server?.off('listening', onListening)
          reject(error)
        }
        const onListening = () => {
          this.server?.off('error', onError)
          resolve()
        }
        this.server.once('error', onError)
        this.server.once('listening', onListening)
        this.server.listen(this.config.port, this.config.host)
      })
      if (this.disposed) {
        await this.stop()
        return
      }
      this.startedAt = Date.now()
      this.publicBase = this.resolvePublicBase(addresses)
      this.phase = 'running'
      const scheme = this.tls ? 'https' : 'http'
      const localHost = isLoopback(this.config.host) ? this.config.host : '127.0.0.1'
      this.logger?.info?.(`phone-remote：手机入口已启动 ${scheme}://${urlHost(localHost)}:${this.actualPort()}（对外 ${this.publicBase}）`)
    } catch (error) {
      this.phase = 'error'
      this.error = error instanceof Error ? error.message : String(error)
      try {
        this.server?.close()
      } catch {
        // Already closed.
      }
      this.server = null
      this.logger?.warn?.(`phone-remote：手机入口启动失败：${this.error}`)
    }
  }

  /**
   * Stop listening and release every connection.
   * @returns {Promise<void>}
   */
  async stop() {
    this.disposed = true
    const server = this.server
    this.server = null
    this.phase = 'stopped'
    if (server === null) return
    await new Promise((resolve) => {
      const timer = setTimeout(resolve, 2000)
      timer.unref?.()
      server.close(() => {
        clearTimeout(timer)
        resolve()
      })
      server.closeAllConnections?.()
    })
  }

  /**
   * The port the listener actually bound.
   * @returns {number} port.
   */
  actualPort() {
    const address = this.server?.address()
    return typeof address === 'object' && address !== null ? address.port : this.config.port
  }

  /**
   * Locate the machine's public and local addresses, caching the public lookup.
   * @returns {Promise<{publicIp: string, source: string, lan: string[], hosts: string[]}>} addresses.
   */
  async detectAddresses() {
    const lan = []
    for (const entries of Object.values(os.networkInterfaces())) {
      for (const entry of entries ?? []) {
        if (entry.internal) continue
        const address = entry.address.split('%')[0]
        if (net.isIP(address) !== 4) continue
        if (!lan.includes(address)) lan.push(address)
      }
    }
    lan.sort((left, right) => {
      const privateLeft = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/u.test(left) ? 1 : 0
      const privateRight = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/u.test(right) ? 1 : 0
      return privateLeft - privateRight
    })

    let publicIp = ''
    let source = 'unknown'
    if (this.config.publicHost.length > 0) {
      publicIp = this.config.publicHost
      source = 'config'
    } else if (this.config.publicUrl.length > 0) {
      try {
        publicIp = new URL(this.config.publicUrl).hostname.replace(/^\[|\]$/gu, '')
        source = 'publicUrl'
      } catch {
        publicIp = ''
      }
    }
    if (publicIp.length === 0) {
      const detected = await this.detectPublicIp()
      if (detected !== '') {
        publicIp = detected
        source = 'public-ip'
      } else if (lan.length > 0) {
        publicIp = lan[0]
        source = 'lan'
      }
    }

    const hosts = []
    const add = (value) => {
      const host = String(value ?? '').trim()
      if (host.length === 0 || hosts.includes(host)) return
      hosts.push(host)
    }
    add(publicIp)
    for (const address of lan) add(address)
    for (const extra of this.config.tlsHosts) add(extra)
    add('127.0.0.1')
    add('localhost')

    return { publicIp, source, lan, hosts }
  }

  /**
   * Ask public IP echo services, falling back to the first LAN address.
   * A failed lookup is cached for a shorter time than a success.
   * @param {boolean} [refresh] - ignore the cache.
   * @returns {Promise<string>} address or empty string.
   */
  async detectPublicIp(refresh = false) {
    if (!refresh && this.ipCache !== null && this.ipCache.expiresAt > Date.now()) return this.ipCache.address
    if (this.publicIpPending !== null) return this.publicIpPending
    this.publicIpPending = (async () => {
      for (const endpoint of ['https://api.ipify.org', 'https://ifconfig.me/ip', 'https://api64.ipify.org']) {
        try {
          const response = await fetch(endpoint, { signal: AbortSignal.timeout(3500), headers: { accept: 'text/plain' } })
          if (!response.ok) continue
          const text = (await response.text()).trim()
          if (net.isIP(text) !== 0) {
            this.ipCache = { address: text, expiresAt: Date.now() + PUBLIC_IP_TTL_MS }
            return text
          }
        } catch {
          // Try the next provider.
        }
      }
      this.ipCache = { address: '', expiresAt: Date.now() + 60_000 }
      return ''
    })().finally(() => {
      this.publicIpPending = null
    })
    return this.publicIpPending
  }

  /**
   * Resolve the advertised root the phone should open.
   * @param {{publicIp: string}} addresses - detected addresses.
   * @returns {string} root URL ending in `/`.
   */
  resolvePublicBase(addresses) {
    const port = this.actualPort()
    const scheme = this.tls ? 'https' : 'http'
    if (this.config.publicUrl.length > 0) {
      try {
        const url = new URL(this.config.publicUrl)
        url.pathname = `${url.pathname.replace(/\/+$/u, '')}/`
        url.search = ''
        url.hash = ''
        return url.href
      } catch {
        // Fall through to a derived URL.
      }
    }
    const host = addresses.publicIp.length > 0 ? addresses.publicIp : '127.0.0.1'
    const defaultPort = (scheme === 'https' && port === 443) || (scheme === 'http' && port === 80)
    return `${scheme}://${urlHost(host)}${defaultPort ? '' : `:${port}`}${this.config.basePath}/`
  }

  /**
   * The advertised root when the listener is up.
   * @returns {string} root URL.
   */
  baseUrl() {
    if (this.publicBase.length > 0) return this.publicBase
    const address = this.addresses.publicIp.length > 0 ? this.addresses.publicIp : '127.0.0.1'
    const scheme = this.tls ? 'https' : 'http'
    return `${scheme}://${urlHost(address)}:${this.config.port}${this.config.basePath}/`
  }

  /**
   * The process launch token, read from Connection once.
   * @returns {string} token, or empty string when unavailable.
   */
  launchToken() {
    if (typeof this.launchTokenCache === 'string' && this.launchTokenCache.length > 0) return this.launchTokenCache
    try {
      const connection = typeof this.ctx.get === 'function' ? this.ctx.get('connection') : this.ctx.connection
      if (connection === undefined) return ''
      const url = new URL(connection.authenticatedUrl('http://127.0.0.1:1/'))
      const token = url.searchParams.get('token') ?? ''
      if (token.length > 0) this.launchTokenCache = token
      return token
    } catch {
      return ''
    }
  }

  /**
   * Read and validate the device cookie on one request.
   * @param {http.IncomingMessage} request - inbound request.
   * @returns {object|undefined} live device.
   */
  authenticate(request) {
    const header = request.headers.cookie
    if (typeof header !== 'string' || header.length === 0) return undefined
    for (const segment of header.split(';')) {
      const at = segment.indexOf('=')
      if (at === -1) continue
      if (segment.slice(0, at).trim() !== 'dsh_phone') continue
      return this.state.verify(segment.slice(at + 1).trim())
    }
    return undefined
  }

  /**
   * Client address for audit and rate limiting.
   * @param {http.IncomingMessage} request - inbound request.
   * @returns {string} address.
   */
  clientIp(request) {
    if (this.config.trustProxy) {
      const forwarded = request.headers['x-forwarded-for']
      const value = Array.isArray(forwarded) ? forwarded[0] : forwarded
      if (typeof value === 'string' && value.length > 0) return value.split(',')[0].trim()
    }
    return request.socket.remoteAddress?.replace(/^::ffff:/u, '') ?? ''
  }

  /**
   * Dispatch one HTTP request.
   * @param {http.IncomingMessage} request - inbound request.
   * @param {http.ServerResponse} response - downstream response.
   * @returns {Promise<void>}
   */
  async handleRequest(request, response) {
    const url = new URL(request.url ?? '/', 'http://dsh.invalid')
    const ip = this.clientIp(request)
    const localPath = this.localPath(url.pathname)
    if (localPath === null) {
      response.writeHead(404, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' })
      response.end('not found')
      return
    }
    if (localPath === '') {
      response.writeHead(302, { location: `${this.config.basePath || ''}/`, 'cache-control': 'no-store' })
      response.end()
      return
    }
    if (localPath === '/healthz') {
      response.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
      response.end(JSON.stringify({ ok: true, service: 'dsh-phone-remote', version: VERSION }))
      return
    }
    if (localPath === '/pair' || localPath === '/pair/') {
      await this.handlePair(request, response, url, ip)
      return
    }
    const device = this.authenticate(request)
    if (device === undefined) {
      this.rejectUnpaired(request, response, url, localPath)
      return
    }
    this.state.touch(device, { ip, userAgent: request.headers['user-agent'] ?? '' })
    request.url = `${localPath}${url.search}`
    forwardRequest(request, response, {
      upstream: this.upstream,
      secure: this.isHttps(),
      publicBase: this.publicBase,
      ip,
    })
  }

  /**
   * Strip the configured mount prefix from one request path.
   * @param {string} pathname - raw request pathname.
   * @returns {string|null} local path, `''` for the bare mount point, or null.
   */
  localPath(pathname) {
    const base = this.config.basePath
    if (base === '') return pathname
    if (pathname === base) return ''
    if (pathname.startsWith(`${base}/`)) return pathname.slice(base.length) || '/'
    return null
  }

  /**
   * Whether the phone actually reaches the entry over HTTPS, either at this
   * listener or through a TLS-terminating reverse proxy.
   * @returns {boolean} HTTPS entry.
   */
  isHttps() {
    return this.tls !== null || (this.config.trustProxy && this.publicBase.startsWith('https://'))
  }

  /**
   * Answer an unpaired request: browsers go to the pairing page, API calls get JSON.
   * @param {http.IncomingMessage} request - inbound request.
   * @param {http.ServerResponse} response - downstream response.
   * @param {URL} url - request URL.
   */
  rejectUnpaired(request, response, url, localPath) {
    const acceptsHtml = String(request.headers.accept ?? '').includes('text/html')
    const isDocument = (request.method === 'GET' || request.method === 'HEAD') && acceptsHtml && !localPath.startsWith('/api/')
    if (isDocument) {
      const next = `${localPath}${url.search}`
      const location = `${this.config.basePath}/pair${next === '/' ? '' : `?next=${encodeURIComponent(next)}`}`
      response.writeHead(302, { location, 'cache-control': 'no-store', 'referrer-policy': 'no-referrer' })
      response.end()
      return
    }
    response.writeHead(401, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
    response.end(JSON.stringify({ error: '此设备尚未配对，或配对已被撤销。', code: 'PHONE_UNAUTHORIZED' }))
  }

  /**
   * Serve or process the pairing form.
   * @param {http.IncomingMessage} request - inbound request.
   * @param {http.ServerResponse} response - downstream response.
   * @param {URL} url - request URL.
   * @param {string} ip - client address.
   * @returns {Promise<void>}
   */
  async handlePair(request, response, url, ip) {
    const next = sanitizeNext(url.searchParams.get('next'))
    if (request.method === 'GET' || request.method === 'HEAD') {
      const page = renderPairPage({
        code: normalizeCode(url.searchParams.get('code')),
        name: url.searchParams.get('name') ?? '',
        next,
        action: `${this.config.basePath}/pair`,
        auto: url.searchParams.get('auto') === '1',
        ttlMinutes: Math.round(this.pairing.ttlSeconds / 60),
        baseUrl: this.baseUrl(),
        fingerprint: this.tls?.fingerprint256 ?? '',
        secure: this.isHttps(),
      })
      this.sendHtml(response, 200, page)
      return
    }
    if (request.method !== 'POST') {
      response.writeHead(405, { allow: 'GET, POST' })
      response.end()
      return
    }
    if (!sameOriginPost(request)) {
      this.sendHtml(response, 403, renderPairPage({
        next,
        action: `${this.config.basePath}/pair`,
        error: '请求来源无效，请从本入口重新打开配对页。',
        ttlMinutes: Math.round(this.pairing.ttlSeconds / 60),
        baseUrl: this.baseUrl(),
        fingerprint: this.tls?.fingerprint256 ?? '',
        secure: this.isHttps(),
      }))
      return
    }
    if (!this.allowAttempt(ip)) {
      const page = renderPairPage({
        next,
        action: `${this.config.basePath}/pair`,
        error: '尝试次数过多，请 10 分钟后再试。',
        ttlMinutes: Math.round(this.pairing.ttlSeconds / 60),
        baseUrl: this.baseUrl(),
        fingerprint: this.tls?.fingerprint256 ?? '',
        secure: this.isHttps(),
      })
      this.sendHtml(response, 429, page)
      return
    }
    const body = await readBody(request)
    const contentType = String(request.headers['content-type'] ?? '')
    const fields = contentType.includes('application/json') ? parseJsonBody(body) : parseFormBody(body)
    const typedCode = normalizeCode(fields.code)
    const record = this.pairing.redeem(fields.code)
    if (record === undefined) {
      const page = renderPairPage({
        code: typedCode,
        name: typeof fields.name === 'string' ? fields.name : '',
        next,
        action: `${this.config.basePath}/pair`,
        error: '配对码无效或已过期，请在电脑端重新生成。',
        ttlMinutes: Math.round(this.pairing.ttlSeconds / 60),
        baseUrl: this.baseUrl(),
        fingerprint: this.tls?.fingerprint256 ?? '',
        secure: this.isHttps(),
      })
      this.sendHtml(response, 401, page)
      return
    }
    this.resetAttempts(ip)
    const userAgent = String(request.headers['user-agent'] ?? '')
    const { cookieValue } = this.state.createDevice({
      name: normalizeDeviceName(fields.name),
      platform: normalizePlatform(userAgent),
      ip,
      userAgent,
    })
    await this.state.flush()
    const maxAge = this.config.deviceMaxAgeDays * 24 * 60 * 60
    const attributes = [
      `dsh_phone=${cookieValue}`,
      'Path=/',
      `Max-Age=${maxAge}`,
      'HttpOnly',
      'SameSite=Lax',
    ]
    if (this.isHttps()) attributes.push('Secure')
    const token = this.launchToken()
    const root = `${this.config.basePath}/`
    const location = token.length > 0 ? `${root}?token=${encodeURIComponent(token)}` : root
    response.writeHead(303, {
      location,
      'set-cookie': attributes.join('; '),
      'cache-control': 'no-store',
      'referrer-policy': 'no-referrer',
    })
    response.end()
  }

  /**
   * Send one HTML page with the pre-authentication security headers.
   * @param {http.ServerResponse} response - downstream response.
   * @param {number} status - HTTP status.
   * @param {string} html - document body.
   */
  sendHtml(response, status, html) {
    response.writeHead(status, PAGE_HEADERS)
    response.end(html)
  }

  /**
   * Consume one pairing attempt from an address.
   * @param {string} ip - client address.
   * @returns {boolean} whether the attempt may proceed.
   */
  allowAttempt(ip) {
    const now = Date.now()
    const record = this.rate.get(ip)
    if (record === undefined || record.resetAt <= now) {
      this.rate.set(ip, { count: 1, resetAt: now + RATE_WINDOW_MS })
      return true
    }
    record.count += 1
    return record.count <= RATE_MAX_ATTEMPTS
  }

  /**
   * Clear failed attempts after a successful pairing.
   * @param {string} ip - client address.
   */
  resetAttempts(ip) {
    this.rate.delete(ip)
  }

  /**
   * Authenticate and proxy one upgrade request.
   * @param {http.IncomingMessage} request - upgrade request.
   * @param {import('node:stream').Duplex} socket - client socket.
   * @param {Buffer} head - bytes already read.
   */
  handleUpgrade(request, socket, head) {
    const url = new URL(request.url ?? '/', 'http://dsh.invalid')
    const localPath = this.localPath(url.pathname)
    if (localPath === null || localPath === '') {
      socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n')
      return
    }
    const device = this.authenticate(request)
    if (device === undefined) {
      socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\nContent-Length: 0\r\n\r\n')
      return
    }
    request.url = `${localPath}${url.search}`
    const ip = this.clientIp(request)
    this.state.touch(device, { ip, userAgent: request.headers['user-agent'] ?? '' })
    try {
      forwardUpgrade(request, socket, head, {
        upstream: this.upstream,
        secure: this.isHttps(),
        publicBase: this.publicBase,
        ip,
      })
    } catch {
      socket.destroy()
    }
  }

  /**
   * Mint one pairing code and its QR image.
   * @param {{note?: string}} [meta] - optional note.
   * @returns {{code: string, formatted: string, url: string, qr: string, expiresAt: number, ttlSeconds: number}} pairing payload.
   */
  createPairing(meta = {}) {
    return this.pairingPayload(this.pairing.create(meta))
  }

  /**
   * Build one entry's URL and QR for an already-issued code. A coordinator
   * can mint one code and render it for both the public and LAN entries.
   * @param {{code: string, formatted: string, expiresAt: number, ttlSeconds: number}} issued - issued code.
   * @returns {{code: string, formatted: string, url: string, qr: string, expiresAt: number, ttlSeconds: number}} pairing payload.
   */
  pairingPayload(issued) {
    const url = `${this.baseUrl()}pair?code=${issued.code}`
    return { ...issued, url, qr: qrDataUrl(url, { cellSize: 7, margin: 3 }) }
  }

  /**
   * Run reachability probes for the diagnostics card.
   * @returns {Promise<object>} diagnostic result.
   */
  async diagnose() {
    const upstreamHost = `127.0.0.1:${this.upstream?.port || '80'}`
    const upstream = await new Promise((resolve) => {
      if (this.upstream === null) {
        resolve({ ok: false, status: 0, ms: 0, error: '上游未初始化' })
        return
      }
      const started = Date.now()
      const request = http.request(
        {
          hostname: this.upstream.hostname,
          port: this.upstream.port || 80,
          path: '/',
          method: 'GET',
          headers: { host: upstreamHost, accept: 'text/html' },
          timeout: 5000,
        },
        (response) => {
          response.resume()
          response.on('end', () => resolve({ ok: true, status: response.statusCode ?? 0, ms: Date.now() - started }))
        },
      )
      request.on('timeout', () => request.destroy(new Error('超时')))
      request.on('error', (error) => resolve({ ok: false, status: 0, ms: Date.now() - started, error: error.message }))
      request.end()
    })

    const publicProbe = this.phase === 'running' && this.publicBase.length > 0
      ? await probe(`${this.publicBase}healthz`, { rejectUnauthorized: false, timeoutMs: 7000 })
      : { ok: false, status: 0, ms: 0, error: '入口未运行' }

    return {
      upstream,
      public: publicProbe,
      listening: this.phase === 'running',
      secure: this.isHttps(),
      note:
        publicProbe.ok && publicProbe.status === 200
          ? '公网入口可达。若手机仍打不开，请检查手机网络、云防火墙端口和证书信任。'
          : '服务端回环探测失败不一定代表公网不可达：部分云主机不支持 NAT 回环。请直接用手机访问入口地址验证。',
    }
  }

  /**
   * Settings-page status snapshot.
   * @returns {object} status.
   */
  status() {
    const port = this.phase === 'running' ? this.actualPort() : this.config.port
    const scheme = this.tls ? 'https' : 'http'
    const listenLoopback = isLoopback(this.config.host)
    const lanUrls = listenLoopback
      ? []
      : this.addresses.lan.map((address) => `${scheme}://${urlHost(address)}${port === 443 || port === 80 ? '' : `:${port}`}${this.config.basePath}/`)
    const warnings = []
    if (this.phase === 'error') warnings.push({ level: 'error', text: this.error ?? '入口启动失败' })
    if (this.phase === 'running' && !this.isHttps()) {
      warnings.push({
        level: 'warn',
        text: this.config.kind === 'lan'
          ? '内网入口使用 HTTP，仅应在可信局域网或 VPN 内使用；不要把该端口直接暴露到公网。'
          : '入口未启用 HTTPS，配对码与对话内容会以明文传输。',
      })
    }
    if (this.phase === 'running' && this.tls === null && this.isHttps()) {
      warnings.push({ level: 'info', text: 'HTTPS 由前置反向代理终止，请确保代理到该监听的链路只走本机或内网。' })
    }
    if (this.phase === 'running' && this.tls?.mode === 'self-signed') {
      warnings.push({ level: 'info', text: '使用自签名证书：手机首次访问需手动信任，鸿蒙 APP 会弹出证书确认。' })
    }
    if (this.phase === 'running' && this.addresses.source === 'lan') {
      warnings.push({ level: 'warn', text: '未检测到公网 IP，二维码可能只能在同一局域网使用；请配置 publicHost 或 publicUrl。' })
    }
    if (listenLoopback && !this.config.trustProxy) {
      warnings.push({ level: 'warn', text: `入口监听在 ${this.config.host}，手机无法直接访问；公网使用请设为 0.0.0.0，或配置 trustProxy 由反向代理转发。` })
    }
    if (this.phase === 'running' && this.state.count() === 0) {
      warnings.push({ level: 'info', text: '尚未配对任何设备。生成配对码后，用手机扫码即可接入。' })
    }
    return {
      version: VERSION,
      phase: this.phase,
      error: this.error,
      enabled: this.config.enabled,
      startedAt: this.startedAt,
      upstream: this.upstream === null ? '' : this.upstream.origin,
      listener: {
        host: this.config.host,
        port,
        scheme,
        secure: this.isHttps(),
        entrySecure: this.isHttps(),
        basePath: this.config.basePath,
      },
      urls: {
        public: this.publicBase,
        lan: lanUrls,
        local: `${scheme}://127.0.0.1:${port}/`,
      },
      addresses: {
        publicIp: this.addresses.publicIp,
        source: this.addresses.source,
        lan: this.addresses.lan,
      },
      tls: this.tls === null
        ? null
        : {
            mode: this.tls.mode,
            fingerprint: this.tls.fingerprint256,
            expiresAt: this.tls.expiresAt,
            expiresOn: formatExpiry(this.tls.expiresAt),
            generated: this.tls.generated,
            certFile: this.tls.certFile,
          },
      devices: this.state.views(),
      deviceCount: this.state.count(),
      pairingActive: this.pairing.count(),
      warnings,
    }
  }
}

/**
 * Reject a cross-site form post: pairing is a state change, and an attacker
 * page must not be able to submit a guessed code from the victim's browser.
 * @param {http.IncomingMessage} request - inbound request.
 * @returns {boolean} whether the post looks same-origin.
 */
function sameOriginPost(request) {
  const origin = request.headers.origin
  // Chromium sends `Origin: null` for a same-origin form post when the page
  // sets `Referrer-Policy: no-referrer` (Android WebView included). The pairing
  // code is the credential, so an absent/opaque origin is accepted; a present
  // foreign origin is still refused, and the code table enforces single use
  // plus per-IP rate limiting.
  if (typeof origin !== 'string' || origin.length === 0 || origin === 'null') return true
  const host = request.headers.host
  if (typeof host !== 'string' || host.length === 0) return false
  try {
    return new URL(origin).host === host
  } catch {
    return false
  }
}

/**
 * Keep one post-pairing path local and safe.
 * @param {unknown} value - raw `next` query value.
 * @returns {string} path or empty string.
 */
function sanitizeNext(value) {
  if (typeof value !== 'string' || value.length === 0 || value.length > 512) return ''
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return ''
  if (value.startsWith('/pair')) return ''
  return value
}

/**
 * Read a request body up to the gateway limit.
 * @param {http.IncomingMessage} request - inbound request.
 * @returns {Promise<string>} UTF-8 body.
 */
function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let size = 0
    request.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        reject(new Error('请求体过大'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    request.on('error', reject)
  })
}

/**
 * Parse an `application/x-www-form-urlencoded` body.
 * @param {string} body - raw body.
 * @returns {Record<string, string>} fields.
 */
function parseFormBody(body) {
  const fields = {}
  for (const [key, value] of new URLSearchParams(body)) fields[key] = value
  return fields
}

/**
 * Parse a JSON body, tolerating malformed input as an empty object.
 * @param {string} body - raw body.
 * @returns {Record<string, unknown>} fields.
 */
function parseJsonBody(body) {
  try {
    const value = JSON.parse(body)
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? value : {}
  } catch {
    return {}
  }
}
