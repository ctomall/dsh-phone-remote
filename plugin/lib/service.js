/**
 * Coordinates the phone entries of one installation. The public entry (usually
 * behind nginx) and the optional LAN entry share one device catalog and one
 * pairing-code table, but listen independently and advertise their own URLs.
 * The settings page and the agent tool consume this coordinator rather than
 * one gateway, so a code can be rendered for either network.
 *
 * @module @local/phone-remote/service
 */

/**
 * One installation's set of phone entries.
 */
export class PhoneRemoteService {
  /**
   * @param {object} options - dependencies.
   * @param {Array<{id: string, label: string, gateway: import('./gateway.js').PhoneGateway}>} options.entries - running entries.
   * @param {import('./state.js').PhoneRemoteState} options.state - shared device catalog.
   * @param {import('./pairing.js').PairingCodes} options.pairing - shared code table.
   * @param {object} [options.updater] - optional self-update manager.
   * @param {object} [options.logger] - host logger.
   */
  constructor({ entries, state, pairing, updater, logger }) {
    this.entries = entries
    this.state = state
    this.pairing = pairing
    this.updater = updater
    this.logger = logger
  }

  /**
   * The public entry, used for the top-level compatibility fields.
   * @returns {{id: string, label: string, gateway: import('./gateway.js').PhoneGateway}|undefined} public entry.
   */
  get publicEntry() {
    return this.entries.find((entry) => entry.id === 'public')
  }

  /**
   * Start every entry. Individual gateways record their own failure instead of
   * rejecting, so one busy port never prevents the other entry from running.
   * @returns {Promise<void>}
   */
  async start() {
    const regular = this.entries.filter((entry) => entry.id !== 'tunnel')
    const tunnels = this.entries.filter((entry) => entry.id === 'tunnel')
    await Promise.all(regular.map((entry) => entry.gateway.start()))
    // Start the tunnel after the public listener is up so cloudflared has an
    // origin to reach on its first connection attempt.
    await Promise.all(tunnels.map((entry) => entry.gateway.start()))
  }

  /**
   * Stop every entry.
   * @returns {Promise<void>}
   */
  async stop() {
    await Promise.all(this.entries.map((entry) => entry.gateway.stop()))
  }

  /**
   * Resolve one entry by id.
   * @param {string} id - entry id.
   * @returns {{id: string, label: string, gateway: import('./gateway.js').PhoneGateway}|undefined} entry.
   */
  entry(id) {
    return this.entries.find((item) => item.id === id)
  }

  /**
   * Whether at least one entry is listening.
   * @returns {boolean} running.
   */
  isRunning() {
    return this.entries.some((entry) => entry.gateway.phase === 'running')
  }

  /**
   * Aggregated status for the settings page.
   * @returns {object} status.
   */
  status() {
    const publicStatus = this.publicEntry?.gateway.status() ?? {
      version: '1.0.0',
      phase: 'disabled',
      error: null,
      enabled: false,
      startedAt: 0,
      upstream: '',
      listener: { host: '', port: 0, scheme: 'http', secure: false, entrySecure: false, basePath: '' },
      urls: { public: '', lan: [], local: '' },
      addresses: { publicIp: '', source: 'unknown', lan: [] },
      tls: null,
      devices: this.state.views(),
      deviceCount: this.state.count(),
      pairingActive: 0,
      warnings: [],
    }
    const entries = this.entries.map((entry) => ({ id: entry.id, label: entry.label, ...entry.gateway.status() }))
    const warnings = []
    const seen = new Set()
    for (const entry of entries) {
      for (const warning of entry.warnings ?? []) {
        const key = `${warning.level}:${warning.text}`
        if (seen.has(key)) continue
        seen.add(key)
        warnings.push(warning)
      }
    }
    const running = entries.some((entry) => entry.phase === 'running')
    return {
      ...publicStatus,
      phase: running ? 'running' : publicStatus.phase,
      entries,
      warnings,
      deviceCount: this.state.count(),
      devices: this.state.views(),
      pairingActive: this.pairing.count(),
    }
  }

  /**
   * Mint one code and render it for the requested entries. `all` renders every
   * running entry from the same code, so either QR/URL can be used once.
   * @param {{entry?: string, note?: string}} [request] - target entry and note.
   * @returns {object[]} pairing payloads.
   */
  createPairing(request = {}) {
    const targetId = typeof request.entry === 'string' && request.entry.length > 0 ? request.entry : 'public'
    const candidates = targetId === 'all' ? this.entries : this.entries.filter((entry) => entry.id === targetId)
    if (candidates.length === 0) throw new Error(`未知入口：${targetId}`)
    const running = candidates.filter((entry) => entry.gateway.phase === 'running')
    if (running.length === 0) throw new Error('所选入口尚未运行，请先修复入口错误。')
    const issued = this.pairing.create({ note: typeof request.note === 'string' ? request.note : '' })
    return running.map((entry) => ({
      entry: entry.id,
      label: entry.label,
      ...entry.gateway.pairingPayload(issued),
    }))
  }

  /**
   * Probe every entry; the public result stays at the top level for older
   * clients, and `entries` carries each network's own probe.
   * @returns {Promise<object>} diagnostic result.
   */
  async diagnose() {
    const entries = await Promise.all(
      this.entries.map(async (entry) => ({
        entry: entry.id,
        label: entry.label,
        ...(await entry.gateway.diagnose()),
      })),
    )
    const publicResult = entries.find((entry) => entry.entry === 'public') ?? entries[0] ?? {
      upstream: { ok: false, status: 0, ms: 0, error: '入口未配置' },
      public: { ok: false, status: 0, ms: 0, error: '入口未配置' },
      note: '',
    }
    return {
      ...publicResult,
      entries,
      note:
        '入口探测在服务器本机发起；云主机的 NAT 回环可能失败，内网入口也可能因路由策略不可回环。最终以手机实际访问为准。',
    }
  }
}
