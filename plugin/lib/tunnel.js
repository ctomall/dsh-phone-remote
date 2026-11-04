/**
 * Outbound tunnel for deployments without a public IP.
 *
 * The gateway already listens on a local port; this module runs Cloudflare's
 * `cloudflared` client, which dials out to Cloudflare and publishes that local
 * port at an HTTPS URL. No inbound port, port-forward or public address is
 * required. The tunnel carries the gateway (not DSH directly), so the existing
 * pairing and device-cookie checks still guard every request.
 *
 * @module @local/phone-remote/tunnel
 */
import { spawn } from 'node:child_process'
import https from 'node:https'
import { qrDataUrl } from './qr.js'

const QUICK_URL_PATTERN = /https:\/\/[a-z0-9-]+\.trycloudflare\.com/iu
const LOG_LIMIT = 64 * 1024

/**
 * Normalize tunnel configuration from the plugin row.
 * @param {object} raw - plugin row config.
 * @param {{port: number, host: string, tlsMode: string, basePath: string}} gateway - public gateway facts.
 * @returns {object} normalized tunnel config.
 */
export function normalizeTunnelConfig(raw, gateway) {
  const input = typeof raw === 'object' && raw !== null ? raw : {}
  const targetHost = gateway.host === '0.0.0.0' || gateway.host === '::' ? '127.0.0.1' : gateway.host
  const scheme = gateway.tlsMode === 'off' ? 'http' : 'https'
  const target = typeof input.tunnelTarget === 'string' && input.tunnelTarget.trim().length > 0
    ? input.tunnelTarget.trim()
    : `${scheme}://${targetHost}:${gateway.port}`
  const timeout = Number.isFinite(input.tunnelStartTimeoutSec) ? Math.round(input.tunnelStartTimeoutSec) : 45
  return {
    enabled: input.tunnelEnabled === true,
    label: typeof input.tunnelLabel === 'string' && input.tunnelLabel.trim().length > 0 ? input.tunnelLabel.trim() : '隧道入口',
    binary: typeof input.cloudflaredPath === 'string' && input.cloudflaredPath.trim().length > 0 ? input.cloudflaredPath.trim() : 'cloudflared',
    mode: input.tunnelMode === 'named' ? 'named' : 'quick',
    name: typeof input.tunnelName === 'string' ? input.tunnelName.trim() : '',
    url: typeof input.tunnelUrl === 'string' ? input.tunnelUrl.trim().replace(/\/+$/u, '') : '',
    target,
    protocol: input.tunnelProtocol === 'quic' ? 'quic' : 'http2',
    autoRestart: input.tunnelAutoRestart !== false,
    startTimeoutSec: Math.min(180, Math.max(15, timeout)),
    args: Array.isArray(input.tunnelArgs) ? input.tunnelArgs.filter((item) => typeof item === 'string') : [],
  }
}

/**
 * Probe one HTTPS health URL.
 * @param {string} target - absolute URL.
 * @returns {Promise<{ok: boolean, status: number, ms: number, error?: string}>} probe result.
 */
function probeHttps(target) {
  return new Promise((resolve) => {
    const started = Date.now()
    let settled = false
    const finish = (value) => {
      if (settled) return
      settled = true
      resolve(value)
    }
    const request = https.get(target, { timeout: 7000, headers: { accept: 'application/json' } }, (response) => {
      response.resume()
      response.on('end', () => finish({ ok: true, status: response.statusCode ?? 0, ms: Date.now() - started }))
    })
    request.on('timeout', () => request.destroy(new Error('超时')))
    request.on('error', (error) => finish({ ok: false, status: 0, ms: Date.now() - started, error: error.message }))
  })
}

/**
 * One `cloudflared` child process plus the URL it publishes.
 */
export class CloudflareTunnel {
  /**
   * @param {{config: object, logger?: object}} options - normalized config.
   */
  constructor({ config, logger }) {
    this.config = config
    this.logger = logger
    this.phase = config.enabled ? 'stopped' : 'disabled'
    this.error = null
    this.url = ''
    this.child = null
    this.stopping = false
    this.startedAt = 0
    this.buffer = ''
    this.restartTimer = null
    this.startTimer = null
  }

  /**
   * Start the tunnel and wait for its public URL (bounded by the configured
   * timeout). A missing binary or a failed start is recorded, never thrown.
   * @returns {Promise<void>}
   */
  async start() {
    if (!this.config.enabled) {
      this.phase = 'disabled'
      return
    }
    if (this.phase === 'running' || this.phase === 'starting') return
    this.stopping = false
    this.error = null
    this.phase = 'starting'
    this.startedAt = Date.now()
    this.url = this.config.mode === 'named' ? this.config.url : ''

    const args = ['tunnel', '--no-autoupdate', '--protocol', this.config.protocol]
    if (this.config.mode === 'named') {
      if (this.config.name.length === 0) {
        this.phase = 'error'
        this.error = 'tunnelMode 为 named 时必须配置 tunnelName。'
        return
      }
      if (this.config.url.length === 0) {
        this.phase = 'error'
        this.error = 'named 模式需要配置 tunnelUrl（例如 https://dsh.example.com）。'
        return
      }
      args.push('run', this.config.name)
    } else {
      args.push('--url', this.config.target)
    }
    if (this.config.target.startsWith('https:')) args.push('--no-tls-verify')
    args.push(...this.config.args)

    let child
    try {
      child = spawn(this.config.binary, args, { stdio: ['ignore', 'pipe', 'pipe'], env: process.env })
    } catch (error) {
      this.fail(`无法启动 cloudflared：${error instanceof Error ? error.message : String(error)}`)
      return
    }
    this.child = child
    this.buffer = ''
    const onData = (chunk) => this.onOutput(chunk)
    child.stdout?.on('data', onData)
    child.stderr?.on('data', onData)
    child.once('error', (error) => {
      const hint = error?.code === 'ENOENT'
        ? '未找到 cloudflared，请先安装：https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/'
        : `cloudflared 启动失败：${error.message}`
      this.fail(hint)
    })
    child.once('exit', (code, signal) => {
      this.child = null
      if (this.stopping) {
        if (this.phase !== 'error') this.phase = 'stopped'
        return
      }
      this.phase = 'error'
      this.error = `cloudflared 已退出（code=${String(code)}${signal ? `, signal=${signal}` : ''}）`
      this.logger?.warn?.(`phone-remote：${this.error}`)
      this.scheduleRestart()
    })

    this.startTimer = setTimeout(() => {
      if (this.phase === 'starting' && this.url.length === 0) {
        this.fail(`cloudflared 在 ${this.config.startTimeoutSec} 秒内没有返回公网地址。请检查出站 HTTPS/HTTP2 是否可用，或查看 cloudflared 日志。`)
        this.kill()
      }
    }, this.config.startTimeoutSec * 1000)
    this.startTimer.unref?.()

    // A named tunnel with a configured URL is usable immediately; health is
    // still reported by the diagnostics probe.
    if (this.url.length > 0) this.markRunning()
  }

  /**
   * Stop the process and any pending restart.
   * @returns {Promise<void>}
   */
  async stop() {
    this.stopping = true
    if (this.restartTimer !== null) {
      clearTimeout(this.restartTimer)
      this.restartTimer = null
    }
    if (this.startTimer !== null) {
      clearTimeout(this.startTimer)
      this.startTimer = null
    }
    const child = this.child
    this.child = null
    if (child === null) {
      this.phase = this.config.enabled ? 'stopped' : 'disabled'
      return
    }
    await new Promise((resolve) => {
      const timer = setTimeout(() => {
        child.kill('SIGKILL')
        resolve()
      }, 3000)
      timer.unref?.()
      child.once('exit', () => {
        clearTimeout(timer)
        resolve()
      })
      child.kill('SIGTERM')
    })
    this.phase = this.config.enabled ? 'stopped' : 'disabled'
    this.url = ''
  }

  /**
   * Consume one chunk of cloudflared output; extract the public URL.
   * @param {Buffer|string} chunk - output chunk.
   */
  onOutput(chunk) {
    this.buffer += String(chunk)
    if (this.buffer.length > LOG_LIMIT) this.buffer = this.buffer.slice(-LOG_LIMIT)
    if (this.url.length > 0) return
    const match = QUICK_URL_PATTERN.exec(this.buffer)
    if (match !== null) {
      this.url = match[0].replace(/\/+$/u, '')
      this.markRunning()
      return
    }

  }

  /**
   * Mark the tunnel usable and clear the start timeout.
   */
  markRunning() {
    if (this.startTimer !== null) {
      clearTimeout(this.startTimer)
      this.startTimer = null
    }
    this.phase = 'running'
    this.error = null
    this.startedAt = Date.now()
    this.logger?.info?.(`phone-remote：隧道已建立 ${this.url}`)
  }

  /**
   * Record a terminal failure and stop the child.
   * @param {string} message - failure reason.
   */
  fail(message) {
    this.phase = 'error'
    this.error = message
    this.logger?.warn?.(`phone-remote：${message}`)
    this.kill()
  }

  /**
   * Kill the current child without marking the tunnel stopped by the caller.
   */
  kill() {
    if (this.child !== null) {
      this.stopping = true
      this.child.kill('SIGTERM')
      this.child = null
    }
  }

  /**
   * Retry after an unexpected exit.
   */
  scheduleRestart() {
    if (!this.config.autoRestart || this.restartTimer !== null) return
    this.restartTimer = setTimeout(() => {
      this.restartTimer = null
      void this.start()
    }, 5000)
    this.restartTimer.unref?.()
  }

  /**
   * Public base URL including the gateway mount prefix.
   * @param {string} basePath - gateway mount prefix, e.g. `/phone`.
   * @returns {string} base URL ending in `/`, or empty.
   */
  baseUrl(basePath) {
    if (this.url.length === 0) return ''
    return `${this.url}${basePath.length > 0 ? `${basePath}/` : '/'}`
  }

  /**
   * Tunnel status for the settings page.
   * @returns {object} status.
   */
  status() {
    return {
      provider: 'cloudflare',
      mode: this.config.mode,
      phase: this.phase,
      error: this.error,
      url: this.url,
      startedAt: this.startedAt,
    }
  }
}

/**
 * A gateway-like facade for the tunnel entry. The tunnel does not open another
 * socket; it reuses the public gateway listener and only changes the advertised
 * base URL used by pairings, status and diagnostics.
 */
export class TunnelEntry {
  /**
   * @param {{manager: CloudflareTunnel, gateway: import('./gateway.js').PhoneGateway, basePath: string, label?: string}} options - entry facts.
   */
  constructor({ manager, gateway, basePath, label }) {
    this.id = 'tunnel'
    this.label = label ?? '隧道入口'
    this.manager = manager
    this.gateway = gateway
    this.basePath = basePath
  }

  /**
   * Effective phase: a tunnel is only usable once it has a URL.
   * @returns {string} phase.
   */
  get phase() {
    return this.manager.phase === 'running' && this.manager.url.length === 0 ? 'starting' : this.manager.phase
  }

  /**
   * Start the tunnel.
   * @returns {Promise<void>}
   */
  async start() {
    await this.manager.start()
  }

  /**
   * Stop the tunnel.
   * @returns {Promise<void>}
   */
  async stop() {
    await this.manager.stop()
  }

  /**
   * Entry status. While the tunnel is starting/error it reports a skeleton; once
   * running it mirrors the public gateway with the tunnel URL substituted.
   * @returns {object} status.
   */
  status() {
    const base = this.gateway.status()
    const tunnel = this.manager.status()
    const url = this.manager.baseUrl(this.basePath)
    const warnings = []
    if (tunnel.phase === 'running') {
      warnings.push({ level: 'info', text: '流量经 Cloudflare 隧道转发；隧道 URL 变化后，旧设备需要重新配对。' })
    } else if (tunnel.error) {
      warnings.push({ level: 'warn', text: tunnel.error })
    }
    if (tunnel.phase !== 'running' || url.length === 0) {
      return {
        ...base,
        phase: tunnel.phase,
        error: tunnel.error,
        urls: { public: '', lan: [], local: '' },
        warnings,
        tunnel,
      }
    }
    return {
      ...base,
      phase: 'running',
      error: null,
      urls: { public: url, lan: [], local: url },
      warnings,
      tunnel,
    }
  }

  /**
   * Build a pairing URL/QR for the tunnel host.
   * @param {object} issued - issued pairing code.
   * @returns {object} pairing payload.
   */
  pairingPayload(issued) {
    const url = `${this.manager.baseUrl(this.basePath)}pair?code=${issued.code}`
    return { ...issued, url, qr: qrDataUrl(url, { cellSize: 7, margin: 3 }) }
  }

  /**
   * Probe the tunnel's public health endpoint plus the local upstream.
   * @returns {Promise<object>} diagnostic result.
   */
  async diagnose() {
    const base = await this.gateway.diagnose()
    if (this.phase !== 'running' || this.manager.url.length === 0) {
      return {
        ...base,
        public: { ok: false, status: 0, ms: 0, error: this.manager.error ?? '隧道未运行' },
        note: '隧道入口未运行，诊断仅覆盖本机上游。',
      }
    }
    const publicProbe = await probeHttps(`${this.manager.baseUrl(this.basePath)}healthz`)
    return {
      ...base,
      public: publicProbe,
      note: '隧道入口从公网回环探测可能受 Cloudflare 可达性影响；最终以手机实际访问为准。',
    }
  }
}
