/**
 * Browser half of the phone-remote plugin: one settings page that shows the
 * public entry, mints a one-time pairing code with its QR image, lists paired
 * devices, and runs reachability diagnostics. All data comes from the
 * authenticated `/api/phone-remote` routes, so the page never holds a pairing
 * secret longer than the code's lifetime.
 *
 * Plain-JS client module: React comes from the module table, styles are
 * rendered as elements so they disappear with the page.
 */
window.__ModuleLoader__.load({
  id: '@local/phone-remote',
  factory(require) {
    const React = require('react')
    const h = React.createElement
    // Relative to the document so the same page works at `/` and behind a
    // prefix such as `/phone/`.
    const API = './api/phone-remote'

    const TEXT = {
      title: '手机远程',
      subtitle: '给这台 DSH 开一个受设备授权保护的公网入口。手机扫码配对后即可远程使用，随时可以撤销。',
      refresh: '刷新',
      generating: '生成中…',
      generate: '生成配对码',
      regenerate: '重新生成',
      pairingTitle: '扫码配对',
      pairingHint: '用手机相机或鸿蒙 APP 扫描二维码；也可以手动打开链接并输入配对码。',
      lanHint: '内网入口只在手机与服务器处于同一局域网（或 VPN）时可用，使用 HTTP；仍然需要一次性配对码。',
      entryOffline: '未运行',
      pairingEmpty: '还没有生成配对码',
      pairingEmptyHint: '配对码 5 分钟内有效、只能使用一次。生成后再用手机扫码。',
      code: '配对码',
      expires: '剩余',
      expired: '配对码已过期，请重新生成。',
      link: '配对链接',
      copy: '复制',
      copied: '已复制',
      copyLink: '复制链接',
      open: '打开',
      accessTitle: '入口地址',
      accessPublic: '公网入口',
      accessLan: '局域网',
      accessLocal: '本机',
      devicesTitle: '已配对设备',
      devicesEmpty: '还没有设备配对。',
      devicesEmptyHint: '生成配对码并用手机扫码后，设备会出现在这里。',
      revoke: '撤销',
      revoking: '撤销中…',
      online: '在线',
      offline: '离线',
      revoked: '已撤销',
      never: '从未连接',
      lastSeen: '最近',
      diagTitle: '入口可达性',
      diagHint: '从服务器本机探测每个入口的上游与入口地址。云主机的 NAT 回环可能失败，最终以手机实际访问为准。',
      diagRun: '开始检测',
      diagRunning: '检测中…',
      diagUpstream: '本机 DSH 上游',
      diagPublic: '公网入口',
      diagOk: '可达',
      diagFail: '不可达',
      tlsTitle: '传输安全',
      tlsMode: '证书来源',
      tlsFingerprint: 'SHA-256 指纹',
      tlsExpires: '到期',
      tlsSelfSigned: '自签名',
      tlsFiles: '自有证书',
      tlsOff: '未启用 HTTPS',
      securityTitle: '安全提示',
      securityBody:
        '配对码等同于登录凭据，只发给你自己的设备。撤销设备后，它在下一次请求时就会被拦下并要求重新配对。公网入口建议在云防火墙中限制来源 IP；内网入口使用 HTTP，只应在可信局域网或 VPN 内使用，不要把 8080 暴露到公网。隧道入口的流量经 Cloudflare 转发，适合没有公网 IP 的网络。',
      loading: '正在读取手机入口状态…',
      loadFailed: '读取状态失败：{0}',
      pairFailed: '生成配对码失败：{0}',
      diagFailed: '检测失败：{0}',
      revokeFailed: '撤销失败：{0}',
      confirmRevoke: '撤销「{0}」？这台设备将立即失去访问权限，下次打开会要求重新配对。',
      phaseRunning: '运行中',
      phaseStarting: '启动中',
      phaseStopped: '已停止',
      phaseDisabled: '已停用',
      phaseError: '启动失败',
      port: '端口',
      upstream: '上游',
      devicePlatform: '平台',
      ip: '地址',
      created: '配对时间',
      noUrl: '尚未确定',
      copyFailed: '复制失败，请手动选择文本。',
    }

    const CSS = `
.pr-page { display: flex; flex-direction: column; gap: 18px; padding: 4px 0 56px; color: var(--dsw-alias-label-primary); font-size: 14px; line-height: 1.45; }
.pr-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.pr-title { margin: 0 0 5px; font-size: 18px; font-weight: 600; letter-spacing: -0.01em; }
.pr-sub { margin: 0; max-width: 62ch; font-size: 13px; color: var(--dsw-alias-label-tertiary); }
.pr-head-meta { display: flex; flex: none; align-items: center; gap: 8px; }
.pr-chip { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border: 1px solid var(--dsw-alias-border-l2); border-radius: 999px; color: var(--dsw-alias-label-secondary); font-size: 11.5px; white-space: nowrap; }
.pr-chip-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--dsw-alias-label-quaternary); }
.pr-chip.running .pr-chip-dot { background: var(--dsw-alias-state-success-primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--dsw-alias-state-success-primary) 18%, transparent); }
.pr-chip.starting .pr-chip-dot { background: var(--dsw-alias-brand-primary); animation: pr-pulse 1s ease-in-out infinite; }
.pr-chip.error .pr-chip-dot, .pr-chip.stopped .pr-chip-dot { background: var(--dsw-alias-state-error-primary); }
@keyframes pr-pulse { 50% { opacity: 0.3; } }
.pr-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; appearance: none; padding: 7px 12px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--dsw-alias-label-secondary); font: inherit; font-size: 13px; white-space: nowrap; cursor: pointer; transition: background 0.15s ease, color 0.15s ease; }
.pr-btn:hover { background: var(--dsw-alias-interactive-bg-hover); color: var(--dsw-alias-label-primary); }
.pr-btn:focus-visible { outline: 2px solid var(--dsw-alias-brand-primary); outline-offset: 2px; }
.pr-btn:disabled { opacity: 0.5; cursor: default; }
.pr-btn.primary { background: var(--dsw-alias-button-primary-fill); color: var(--dsw-alias-label-primary-inverted); font-weight: 550; }
.pr-btn.primary:hover { background: var(--dsw-alias-button-primary-hover); color: var(--dsw-alias-label-primary-inverted); }
.pr-btn.ghost { border-color: var(--dsw-alias-border-l2); }
.pr-btn.small { padding: 4px 8px; font-size: 12px; }
.pr-btn.danger { color: var(--dsw-alias-label-error); }
.pr-banner { padding: 10px 12px; border: 1px solid var(--dsw-alias-border-l2); border-radius: 10px; background: var(--dsw-alias-bg-l1); font-size: 12.5px; line-height: 1.55; }
.pr-banner.error { border-color: var(--dsw-alias-state-error-primary); background: color-mix(in srgb, var(--dsw-alias-state-error-primary) 8%, transparent); color: var(--dsw-alias-label-error); }
.pr-banner.warn { border-color: var(--dsw-alias-state-warn-primary, #d97706); background: color-mix(in srgb, var(--dsw-alias-state-warn-primary, #d97706) 8%, transparent); }
.pr-banner.info { color: var(--dsw-alias-label-secondary); }
.pr-banner b { font-weight: 600; }
.pr-grid { display: grid; grid-template-columns: minmax(0, 1.12fr) minmax(0, 0.88fr); gap: 16px; align-items: start; }
@media (max-width: 760px) { .pr-grid { grid-template-columns: minmax(0, 1fr); } }
.pr-tabs { display: flex; gap: 6px; padding: 4px; border: 1px solid var(--dsw-alias-border-l2); border-radius: 10px; background: var(--dsw-alias-bg-l2, rgba(127,127,127,0.06)); }
.pr-tab { flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 6px; appearance: none; padding: 6px 10px; border: 0; border-radius: 7px; background: transparent; color: var(--dsw-alias-label-tertiary); font: inherit; font-size: 12px; cursor: pointer; }
.pr-tab:hover { color: var(--dsw-alias-label-primary); }
.pr-tab.active { background: var(--dsw-alias-bg-l1); color: var(--dsw-alias-label-primary); box-shadow: 0 1px 2px rgba(0,0,0,0.08); }
.pr-tab:disabled { opacity: 0.45; cursor: default; }
.pr-tab-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--dsw-alias-state-success-primary); }
.pr-tab-dot.error { background: var(--dsw-alias-state-error-primary); }
.pr-tab-dot.disabled, .pr-tab-dot.stopped { background: var(--dsw-alias-label-quaternary); }
.pr-card { display: flex; flex-direction: column; gap: 14px; padding: 16px; border: 1px solid var(--dsw-alias-border-l2); border-radius: 14px; background: var(--dsw-alias-bg-l1); }
.pr-card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.pr-card-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; }
.pr-card-title svg { color: var(--dsw-alias-label-tertiary); }
.pr-card-sub { margin: 0; color: var(--dsw-alias-label-quaternary); font-size: 12px; line-height: 1.55; }
.pr-pair { display: flex; gap: 18px; align-items: center; }
.pr-qr { flex: none; display: grid; place-items: center; width: 188px; height: 188px; padding: 6px; border-radius: 14px; background: #fff; box-shadow: 0 0 0 1px rgba(0,0,0,0.06), 0 8px 24px rgba(0,0,0,0.08); }
.pr-qr img { width: 176px; height: 176px; display: block; }
.pr-qr-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; width: 100%; height: 100%; border: 1px dashed var(--dsw-alias-border-l2); border-radius: 10px; color: var(--dsw-alias-label-quaternary); font-size: 11.5px; text-align: center; background: repeating-linear-gradient(45deg, transparent, transparent 7px, rgba(127,127,127,0.05) 7px, rgba(127,127,127,0.05) 14px); }
.pr-pair-main { display: flex; flex: 1; flex-direction: column; gap: 10px; min-width: 0; }
.pr-code { font-family: ui-monospace, SFMono-Regular, "JetBrains Mono", Menlo, monospace; font-size: 28px; font-weight: 650; letter-spacing: 0.12em; line-height: 1.1; }
.pr-countdown { display: inline-flex; align-items: center; gap: 6px; color: var(--dsw-alias-label-tertiary); font-size: 12px; }
.pr-countdown b { color: var(--dsw-alias-label-primary); font-variant-numeric: tabular-nums; }
.pr-link { display: flex; align-items: center; gap: 6px; min-width: 0; }
.pr-link code { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 6px 8px; border: 1px solid var(--dsw-alias-border-l2); border-radius: 7px; color: var(--dsw-alias-label-secondary); font-size: 11.5px; }
.pr-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 0; }
.pr-row + .pr-row { border-top: 1px solid var(--dsw-alias-border-l1); }
.pr-row-label { flex: none; width: 78px; color: var(--dsw-alias-label-tertiary); font-size: 12px; }
.pr-row-value { flex: 1; min-width: 0; color: var(--dsw-alias-label-secondary); font-size: 12.5px; word-break: break-all; }
.pr-row-value.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11.5px; }
.pr-device { display: flex; align-items: flex-start; gap: 11px; padding: 12px 0; }
.pr-device + .pr-device { border-top: 1px solid var(--dsw-alias-border-l1); }
.pr-device-icon { flex: none; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: var(--dsw-alias-bg-l2, rgba(127,127,127,0.08)); color: var(--dsw-alias-label-tertiary); }
.pr-device-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.pr-device-name { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; font-weight: 600; font-size: 13px; }
.pr-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--dsw-alias-label-quaternary); }
.pr-dot.online { background: var(--dsw-alias-state-success-primary); }
.pr-device-sub { color: var(--dsw-alias-label-quaternary); font-size: 11.5px; word-break: break-all; }
.pr-tag { padding: 1px 6px; border: 1px solid var(--dsw-alias-border-l2); border-radius: 999px; color: var(--dsw-alias-label-quaternary); font-size: 10px; letter-spacing: 0.03em; }
.pr-empty { display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 22px 16px; border: 1px dashed var(--dsw-alias-border-l2); border-radius: 10px; color: var(--dsw-alias-label-quaternary); font-size: 12.5px; text-align: center; }
.pr-diag { display: flex; flex-direction: column; gap: 8px; }
.pr-diag-row { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--dsw-alias-label-secondary); }
.pr-diag-row .ok { color: var(--dsw-alias-state-success-primary); }
.pr-diag-row .bad { color: var(--dsw-alias-label-error); }
.pr-diag-ms { margin-left: auto; color: var(--dsw-alias-label-quaternary); font-variant-numeric: tabular-nums; }
.pr-note { margin: 0; color: var(--dsw-alias-label-quaternary); font-size: 11.5px; line-height: 1.6; }
.pr-security { border-left: 2px solid var(--dsw-alias-brand-primary); padding: 2px 0 2px 12px; }
.pr-security p { margin: 0; color: var(--dsw-alias-label-tertiary); font-size: 12px; line-height: 1.65; }
.pr-security b { color: var(--dsw-alias-label-secondary); }
.pr-skeleton { display: flex; flex-direction: column; gap: 12px; }
.pr-skeleton-row { height: 64px; border-radius: 12px; background: linear-gradient(90deg, var(--dsw-alias-bg-l1), var(--dsw-alias-bg-l2, rgba(127,127,127,0.08)), var(--dsw-alias-bg-l1)); background-size: 200% 100%; animation: pr-shimmer 1.4s ease-in-out infinite; }
@keyframes pr-shimmer { to { background-position: -200% 0; } }
`

    /**
     * Call one authenticated JSON route.
     * @param {string} path - route path under `/api/phone-remote`.
     * @param {object} [body] - JSON body.
     * @returns {Promise<any>} parsed response.
     */
    async function api(path, body) {
      const response = await fetch(`${API}${path}`, {
        method: body === undefined ? 'GET' : 'POST',
        credentials: 'same-origin',
        headers: body === undefined ? undefined : { 'content-type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      const text = await response.text()
      let data = null
      try {
        data = text.length > 0 ? JSON.parse(text) : null
      } catch {
        data = null
      }
      if (!response.ok) throw new Error(data?.error || `HTTP ${response.status}`)
      return data
    }

    /**
     * Format a remaining duration as `mm:ss`.
     * @param {number} milliseconds - remaining time.
     * @returns {string} clock text.
     */
    function clock(milliseconds) {
      const total = Math.max(0, Math.ceil(milliseconds / 1000))
      const minutes = Math.floor(total / 60)
      const seconds = total % 60
      return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    }

    /**
     * Relative Chinese time for a device's last activity.
     * @param {number} timestamp - epoch milliseconds.
     * @returns {string} label.
     */
    function relative(timestamp) {
      if (!Number.isFinite(timestamp) || timestamp <= 0) return TEXT.never
      const delta = Date.now() - timestamp
      if (delta < 60_000) return '刚刚'
      if (delta < 3_600_000) return `${Math.floor(delta / 60_000)} 分钟前`
      if (delta < 86_400_000) return `${Math.floor(delta / 3_600_000)} 小时前`
      if (delta < 7 * 86_400_000) return `${Math.floor(delta / 86_400_000)} 天前`
      return new Date(timestamp).toLocaleDateString('zh-CN')
    }

    /**
     * Put one string on the clipboard, with a legacy fallback.
     * @param {string} text - text to copy.
     * @returns {Promise<boolean>} whether it succeeded.
     */
    async function copyText(text) {
      try {
        await navigator.clipboard.writeText(text)
        return true
      } catch {
        try {
          const area = document.createElement('textarea')
          area.value = text
          area.style.position = 'fixed'
          area.style.opacity = '0'
          document.body.appendChild(area)
          area.select()
          const copied = document.execCommand('copy')
          area.remove()
          return copied
        } catch {
          return false
        }
      }
    }

    function IconPhone() {
      return h(
        'svg',
        { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true },
        h('rect', { x: 6.5, y: 2.5, width: 11, height: 19, rx: 3, stroke: 'currentColor', strokeWidth: 1.6 }),
        h('path', { d: 'M10 5.4h4', stroke: 'currentColor', strokeWidth: 1.6, strokeLinecap: 'round' }),
      )
    }

    function IconQr() {
      return h(
        'svg',
        { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true },
        h('rect', { x: 3.5, y: 3.5, width: 6, height: 6, rx: 1.2, stroke: 'currentColor', strokeWidth: 1.5 }),
        h('rect', { x: 14.5, y: 3.5, width: 6, height: 6, rx: 1.2, stroke: 'currentColor', strokeWidth: 1.5 }),
        h('rect', { x: 3.5, y: 14.5, width: 6, height: 6, rx: 1.2, stroke: 'currentColor', strokeWidth: 1.5 }),
        h('path', { d: 'M14.5 14.5h2.6v2.6h-2.6zM19 19h1.5v1.5H19z', fill: 'currentColor' }),
      )
    }

    function IconCopy() {
      return h(
        'svg',
        { width: 14, height: 14, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true },
        h('rect', { x: 8.5, y: 8.5, width: 11, height: 11, rx: 2.2, stroke: 'currentColor', strokeWidth: 1.6 }),
        h('path', { d: 'M15.5 8.5V6.8a2.3 2.3 0 0 0-2.3-2.3H6.8a2.3 2.3 0 0 0-2.3 2.3v6.4a2.3 2.3 0 0 0 2.3 2.3h1.7', stroke: 'currentColor', strokeWidth: 1.6 }),
      )
    }

    function IconShield() {
      return h(
        'svg',
        { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true },
        h('path', { d: 'M12 3.2 19 6v5.2c0 4.5-2.9 7.7-7 9.6-4.1-1.9-7-5.1-7-9.6V6l7-2.8Z', stroke: 'currentColor', strokeWidth: 1.6, strokeLinejoin: 'round' }),
        h('path', { d: 'm8.8 11.9 2.1 2.1 4.3-4.3', stroke: 'currentColor', strokeWidth: 1.6, strokeLinecap: 'round', strokeLinejoin: 'round' }),
      )
    }

    function IconRefresh() {
      return h(
        'svg',
        { width: 14, height: 14, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true },
        h('path', { d: 'M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.8v4.5h-4.5', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round' }),
      )
    }

    function IconWifi() {
      return h(
        'svg',
        { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true },
        h('path', { d: 'M3.8 9.2a12.4 12.4 0 0 1 16.4 0M6.5 12.4a8.3 8.3 0 0 1 11 0M9.3 15.6a4.2 4.2 0 0 1 5.4 0', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round' }),
        h('circle', { cx: 12, cy: 19, r: 1.3, fill: 'currentColor' }),
      )
    }

    /**
     * Small copy button with transient confirmation.
     * @param {{text: string, label?: string, small?: boolean}} props - button props.
     * @returns {object} element.
     */
    function CopyButton({ text, label, small }) {
      const [done, setDone] = React.useState(false)
      const run = async () => {
        const ok = await copyText(text)
        if (!ok) return
        setDone(true)
        setTimeout(() => setDone(false), 1600)
      }
      return h(
        'button',
        { type: 'button', className: `pr-btn ghost${small ? ' small' : ''}`, onClick: run, title: label || TEXT.copy },
        h(IconCopy),
        done ? TEXT.copied : label || TEXT.copy,
      )
    }

    /**
     * QR + one-time code card.
     * @param {{status: object, onError: Function}} props - page facts.
     * @returns {object} element.
     */
    function PairingCard({ status, onError }) {
      const [pairing, setPairing] = React.useState(null)
      const [busy, setBusy] = React.useState(false)
      const [remaining, setRemaining] = React.useState(0)
      const [entryId, setEntryId] = React.useState('public')

      const entries = Array.isArray(status.entries) && status.entries.length > 0
        ? status.entries
        : [{ id: 'public', label: TEXT.accessPublic, phase: status.phase, urls: status.urls }]
      const selected = entries.find((entry) => entry.id === entryId)
      const active = selected !== undefined && selected.phase === 'running'
        ? selected
        : entries.find((entry) => entry.phase === 'running') ?? selected ?? entries[0]
      const activeId = active === undefined ? '' : active.id
      const running = active !== undefined && active.phase === 'running'

      React.useEffect(() => {
        setPairing(null)
      }, [activeId])

      React.useEffect(() => {
        if (pairing === null) {
          setRemaining(0)
          return undefined
        }
        const tick = () => setRemaining(Math.max(0, pairing.expiresAt - Date.now()))
        tick()
        const timer = setInterval(tick, 1000)
        return () => clearInterval(timer)
      }, [pairing])

      React.useEffect(() => {
        if (pairing !== null && remaining === 0) {
          const timer = setTimeout(() => setPairing(null), 1200)
          return () => clearTimeout(timer)
        }
        return undefined
      }, [pairing, remaining])

      const generate = async () => {
        if (!running || busy) return
        setBusy(true)
        try {
          const data = await api('/pairing', { entry: activeId })
          setPairing(data.pairing)
        } catch (error) {
          onError(TEXT.pairFailed.replace('{0}', error.message))
        } finally {
          setBusy(false)
        }
      }

      return h(
        'section',
        { className: 'pr-card', 'aria-label': TEXT.pairingTitle },
        h(
          'div',
          { className: 'pr-card-head' },
          h('div', { className: 'pr-card-title' }, h(IconQr), TEXT.pairingTitle),
          pairing === null
            ? h('button', { type: 'button', className: 'pr-btn primary', onClick: generate, disabled: !running || busy }, busy ? TEXT.generating : TEXT.generate)
            : h('button', { type: 'button', className: 'pr-btn ghost small', onClick: generate, disabled: !running || busy }, busy ? TEXT.generating : TEXT.regenerate),
        ),
        entries.length > 1
          ? h(
              'div',
              { className: 'pr-tabs', role: 'tablist' },
              entries.map((entry) =>
                h(
                  'button',
                  {
                    key: entry.id,
                    type: 'button',
                    role: 'tab',
                    className: `pr-tab${activeId === entry.id ? ' active' : ''}`,
                    disabled: entry.phase !== 'running',
                    onClick: () => {
                      setEntryId(entry.id)
                      setPairing(null)
                    },
                  },
                  h('span', { className: `pr-tab-dot ${entry.phase === 'running' ? '' : entry.phase === 'error' ? 'error' : 'disabled'}` }),
                  entry.label,
                  entry.phase === 'running' ? null : h('span', { className: 'pr-tag' }, TEXT.entryOffline),
                ),
              ),
            )
          : null,
        h(
          'div',
          { className: 'pr-pair' },
          h(
            'div',
            { className: 'pr-qr' },
            pairing !== null && remaining > 0
              ? h('img', { src: pairing.qr, width: 176, height: 176, alt: TEXT.pairingTitle })
              : h(
                  'div',
                  { className: 'pr-qr-empty' },
                  h(IconPhone),
                  h('span', null, running ? TEXT.pairingEmpty : phaseText(active === undefined ? status.phase : active.phase)),
                ),
          ),
          h(
            'div',
            { className: 'pr-pair-main' },
            pairing !== null && remaining > 0
              ? [
                  h('div', { key: 'code', className: 'pr-code' }, pairing.formatted),
                  h(
                    'div',
                    { key: 'time', className: 'pr-countdown' },
                    `${TEXT.expires} `,
                    h('b', null, clock(remaining)),
                  ),
                  h(
                    'div',
                    { key: 'link', className: 'pr-link' },
                    h('code', { title: pairing.url }, pairing.url),
                    h(CopyButton, { text: pairing.url, label: TEXT.copyLink, small: true }),
                  ),
                  h('p', { key: 'hint', className: 'pr-card-sub' }, activeId === 'lan' ? TEXT.lanHint : TEXT.pairingHint),
                ]
              : [
                  h('div', { key: 'empty', style: { fontWeight: 600, fontSize: 13 } }, TEXT.pairingEmpty),
                  h('p', { key: 'empty-hint', className: 'pr-card-sub' }, TEXT.pairingEmptyHint),
                  h('p', { key: 'hint', className: 'pr-note' }, activeId === 'lan' ? TEXT.lanHint : TEXT.pairingHint),
                ],
          ),
        ),
      )
    }

    /**
     * Chinese label for a gateway phase.
     * @param {string} phase - phase.
     * @returns {string} label.
     */
    function phaseText(phase) {
      if (phase === 'running') return TEXT.phaseRunning
      if (phase === 'starting') return TEXT.phaseStarting
      if (phase === 'disabled') return TEXT.phaseDisabled
      if (phase === 'error') return TEXT.phaseError
      return TEXT.phaseStopped
    }

    /**
     * Entry-address card.
     * @param {{status: object}} props - page facts.
     * @returns {object} element.
     */
    function AccessCard({ status }) {
      const entries = Array.isArray(status.entries) && status.entries.length > 0
        ? status.entries
        : [{ id: 'public', label: TEXT.accessPublic, urls: status.urls, listener: status.listener }]
      const rows = [
        ...entries.map((entry) => ({ label: entry.label, value: entry.urls?.public || TEXT.noUrl })),
        { label: TEXT.accessLocal, value: status.urls.local || TEXT.noUrl },
      ]
      return h(
        'section',
        { className: 'pr-card', 'aria-label': TEXT.accessTitle },
        h('div', { className: 'pr-card-head' }, h('div', { className: 'pr-card-title' }, h(IconWifi), TEXT.accessTitle)),
        h(
          'div',
          null,
          rows.map((row, index) =>
            h(
              'div',
              { className: 'pr-row', key: `${row.label}-${index}` },
              h('div', { className: 'pr-row-label' }, row.label),
              h('div', { className: 'pr-row-value mono' }, row.value),
              row.value === TEXT.noUrl ? null : h(CopyButton, { text: row.value, small: true }),
            ),
          ),
        ),
        ...entries.map((entry) =>
          h(
            'div',
            { className: 'pr-row', key: `port-${entry.id}` },
            h('div', { className: 'pr-row-label' }, `${entry.id === 'public' ? '公网' : entry.id === 'lan' ? '内网' : '隧道'}端口`),
            h(
              'div',
              { className: 'pr-row-value' },
              entry.id === 'tunnel'
                ? 'Cloudflare 隧道 · HTTPS'
                : `${entry.listener?.host ?? '?'}:${entry.listener?.port ?? '?'} · ${entry.listener?.entrySecure ? 'HTTPS' : 'HTTP'}`,
            ),
          ),
        ),
        status.listener.entrySecure && status.tls === null
          ? h(
              'div',
              { className: 'pr-row' },
              h('div', { className: 'pr-row-label' }, 'TLS'),
              h('div', { className: 'pr-row-value' }, 'HTTPS（由前置反向代理终止）'),
            )
          : null,
        status.tls
          ? h(
              'div',
              { className: 'pr-row' },
              h('div', { className: 'pr-row-label' }, TEXT.tlsExpires),
              h('div', { className: 'pr-row-value' }, `${status.tls.expiresOn} · ${status.tls.mode === 'self-signed' ? TEXT.tlsSelfSigned : TEXT.tlsFiles}`),
            )
          : null,
      )
    }

    /**
     * Paired device list.
     * @param {{status: object, onChanged: Function, onError: Function}} props - page facts.
     * @returns {object} element.
     */
    function DeviceCard({ status, onChanged, onError }) {
      const [busyId, setBusyId] = React.useState('')
      const devices = status.devices ?? []
      const revoke = async (device) => {
        if (!window.confirm(TEXT.confirmRevoke.replace('{0}', device.name))) return
        setBusyId(device.id)
        try {
          await api('/devices/revoke', { id: device.id })
          onChanged()
        } catch (error) {
          onError(TEXT.revokeFailed.replace('{0}', error.message))
        } finally {
          setBusyId('')
        }
      }
      return h(
        'section',
        { className: 'pr-card', 'aria-label': TEXT.devicesTitle },
        h(
          'div',
          { className: 'pr-card-head' },
          h('div', { className: 'pr-card-title' }, h(IconPhone), TEXT.devicesTitle),
          h('span', { className: 'pr-tag' }, `${status.deviceCount} 台`),
        ),
        devices.length === 0
          ? h('div', { className: 'pr-empty' }, h(IconPhone), h('span', null, TEXT.devicesEmpty), h('span', null, TEXT.devicesEmptyHint))
          : devices.map((device) =>
              h(
                'div',
                { className: 'pr-device', key: device.id },
                h('div', { className: 'pr-device-icon' }, h(IconPhone)),
                h(
                  'div',
                  { className: 'pr-device-main' },
                  h(
                    'div',
                    { className: 'pr-device-name' },
                    h('span', { className: `pr-dot${device.online ? ' online' : ''}` }),
                    device.name,
                    h('span', { className: 'pr-tag' }, device.platform),
                    device.revokedAt !== null ? h('span', { className: 'pr-tag' }, TEXT.revoked) : null,
                  ),
                  h(
                    'div',
                    { className: 'pr-device-sub' },
                    `${TEXT.lastSeen} ${relative(device.lastSeenAt)}${device.lastIp ? ` · ${device.lastIp}` : ''} · ${device.id}`,
                  ),
                ),
                device.revokedAt === null
                  ? h(
                      'button',
                      { type: 'button', className: 'pr-btn danger small', onClick: () => revoke(device), disabled: busyId === device.id },
                      busyId === device.id ? TEXT.revoking : TEXT.revoke,
                    )
                  : null,
              ),
            ),
      )
    }


    /**
     * Plugin self-update card. Git installs fast-forward the plugin directory
     * from the tracked upstream branch; archive installs verify the signed
     * feed and swap the directory. Both need a DSH restart.
     * @param {{onError: Function}} props - error reporter.
     * @returns {object} element.
     */
    function UpdateCard({ onError }) {
      const [info, setInfo] = React.useState(null)
      const [busy, setBusy] = React.useState(false)
      const check = async () => {
        setBusy(true)
        try {
          setInfo(await api('/update'))
        } catch (error) {
          onError(`检查插件更新失败：${error.message}`)
        } finally {
          setBusy(false)
        }
      }
      const apply = async () => {
        const strategy = info?.mode === 'git'
          ? '从 Git 上游快进合并插件代码（失败自动回滚）'
          : '下载并校验更新包后替换插件目录'
        if (!window.confirm(`将${strategy}，完成后需要重启 DSH 生效。现在继续？`)) return
        setBusy(true)
        try {
          const result = await api('/update/apply', {})
          setInfo({ ...info, applied: result, available: false, dirty: false })
          const method = result.mode === 'git' ? 'Git 快进' : '更新包'
          window.alert(`已升级到 v${result.installedVersion}（${method}），请重启 DSH 生效。`)
        } catch (error) {
          onError(`升级失败：${error.message}`)
        } finally {
          setBusy(false)
        }
      }
      const modeLabel = info?.mode === 'git' ? 'Git 快进' : info?.mode === 'zip' ? '更新包' : '自动'
      const signedLabel = info?.signed ? '签名已校验' : null
      return h(
        'section',
        { className: 'pr-card', 'aria-label': '在线升级' },
        h(
          'div',
          { className: 'pr-card-head' },
          h('div', { className: 'pr-card-title' }, h(IconRefresh), '在线升级'),
          h('span', { className: 'pr-tag' }, modeLabel),
          signedLabel ? h('span', { className: 'pr-tag' }, signedLabel) : null,
          h('span', { className: 'pr-tag' }, info?.currentVersion ? `v${info.currentVersion}` : '未检查'),
        ),
        h('p', { className: 'pr-card-sub' },
          info?.mode === 'git'
            ? 'Git 安装：从上游分支快进合并插件目录，失败自动回滚；升级后重启 DSH 生效。'
            : '读取 HTTPS 更新清单，校验 ECDSA 签名与 SHA-256 后替换插件目录；升级后重启 DSH 生效。'),
        info?.error ? h('div', { className: 'pr-banner error' }, info.error) : null,
        info?.dirty ? h('div', { className: 'pr-banner error' }, '插件目录有未提交改动，升级会中止；请先提交或 stash。') : null,
        info?.available
          ? h('div', { className: 'pr-row' }, h('div', { className: 'pr-row-label' }, '可用版本'), h('div', { className: 'pr-row-value' }, `v${info.latestVersion}`))
          : null,
        info?.available && info?.mode === 'git'
          ? h('div', { className: 'pr-row' }, h('div', { className: 'pr-row-label' }, '落后提交'), h('div', { className: 'pr-row-value' }, `${info.commitsBehind} 个`))
          : null,
        info?.available && info?.mode === 'zip' && info?.signed
          ? h('div', { className: 'pr-row' }, h('div', { className: 'pr-row-label' }, '清单签名'), h('div', { className: 'pr-row-value' }, 'ECDSA P-256 通过'))
          : null,
        info?.notes ? h('p', { className: 'pr-card-sub' }, info.notes) : null,
        h(
          'div',
          { style: { display: 'flex', gap: 8, flexWrap: 'wrap' } },
          h('button', { type: 'button', className: 'pr-btn ghost', onClick: check, disabled: busy }, busy ? '检查中…' : '检查更新'),
          info?.available ? h('button', { type: 'button', className: 'pr-btn', onClick: apply, disabled: busy }, busy ? '升级中…' : '立即升级') : null,
        ),
      )
    }

    /**
     * Reachability diagnostics.
     * @param {{onError: Function}} props - page facts.
     * @returns {object} element.
     */
    function DiagnosticsCard({ onError }) {
      const [result, setResult] = React.useState(null)
      const [busy, setBusy] = React.useState(false)
      const run = async () => {
        setBusy(true)
        try {
          setResult(await api('/diagnose', {}))
        } catch (error) {
          onError(TEXT.diagFailed.replace('{0}', error.message))
        } finally {
          setBusy(false)
        }
      }
      const line = (label, probe) => {
        if (!probe) return null
        return h(
          'div',
          { className: 'pr-diag-row', key: `${label}-${probe.status}-${probe.ms}` },
          h('span', { className: probe.ok ? 'ok' : 'bad' }, probe.ok ? '✓' : '✕'),
          h('span', null, label),
          h('span', { className: 'pr-diag-ms' }, probe.ok ? `${probe.status} · ${probe.ms} ms` : probe.error || TEXT.diagFail),
        )
      }
      const probes = Array.isArray(result?.entries) && result.entries.length > 0
        ? result.entries.flatMap((entry) => [
            { key: `${entry.entry}-upstream`, label: `${entry.label} 上游`, probe: entry.upstream },
            { key: `${entry.entry}-public`, label: `${entry.label} 入口`, probe: entry.public },
          ])
        : [
            { key: 'upstream', label: TEXT.diagUpstream, probe: result?.upstream },
            { key: 'public', label: TEXT.diagPublic, probe: result?.public },
          ]
      return h(
        'section',
        { className: 'pr-card', 'aria-label': TEXT.diagTitle },
        h(
          'div',
          { className: 'pr-card-head' },
          h('div', { className: 'pr-card-title' }, h(IconShield), TEXT.diagTitle),
          h('button', { type: 'button', className: 'pr-btn ghost small', onClick: run, disabled: busy }, busy ? TEXT.diagRunning : TEXT.diagRun),
        ),
        h('p', { className: 'pr-note' }, TEXT.diagHint),
        result
          ? h(
              'div',
              { className: 'pr-diag' },
              ...probes.map((item) => line(item.label, item.probe)),
              h('p', { className: 'pr-note' }, result.note),
            )
          : null,
      )
    }

    /**
     * Settings page root.
     * @returns {object} element.
     */
    function PhoneRemotePage() {
      const [status, setStatus] = React.useState(null)
      const [error, setError] = React.useState('')
      const [loading, setLoading] = React.useState(true)

      const load = React.useCallback(async () => {
        try {
          const data = await api('/status')
          setStatus(data)
          setError('')
        } catch (cause) {
          setError(TEXT.loadFailed.replace('{0}', cause.message))
        } finally {
          setLoading(false)
        }
      }, [])

      React.useEffect(() => {
        load()
        const timer = setInterval(load, 15_000)
        return () => clearInterval(timer)
      }, [load])

      if (loading && status === null) {
        return h(
          React.Fragment,
          null,
          h('style', null, CSS),
          h(
            'div',
            { className: 'pr-page' },
            h('div', { className: 'pr-skeleton' }, h('div', { className: 'pr-skeleton-row' }), h('div', { className: 'pr-skeleton-row' })),
            h('p', { className: 'pr-note' }, TEXT.loading),
          ),
        )
      }

      if (status === null) {
        return h(
          React.Fragment,
          null,
          h('style', null, CSS),
          h(
            'div',
            { className: 'pr-page' },
            h('div', { className: 'pr-banner error', role: 'alert' }, error),
            h('button', { type: 'button', className: 'pr-btn ghost', onClick: load }, TEXT.refresh),
          ),
        )
      }

      const warnings = status.warnings ?? []
      const phaseClass = status.phase === 'error' ? 'error' : status.phase === 'running' ? 'running' : status.phase === 'starting' ? 'starting' : 'stopped'
      return h(
        React.Fragment,
        null,
        h('style', null, CSS),
        h(
          'div',
          { className: 'pr-page' },
          h(
            'div',
            { className: 'pr-head' },
            h('div', null, h('h2', { className: 'pr-title' }, TEXT.title), h('p', { className: 'pr-sub' }, TEXT.subtitle)),
            h(
              'div',
              { className: 'pr-head-meta' },
              h('span', { className: `pr-chip ${phaseClass}` }, h('span', { className: 'pr-chip-dot' }), phaseText(status.phase)),
              h('button', { type: 'button', className: 'pr-btn ghost', onClick: load }, h(IconRefresh), TEXT.refresh),
            ),
          ),
          error.length > 0 ? h('div', { className: 'pr-banner error', role: 'alert' }, error) : null,
          warnings.map((warning, index) => h('div', { className: `pr-banner ${warning.level}`, key: index }, warning.text)),
          h(
            'div',
            { className: 'pr-grid' },
            h(PairingCard, { status, onError: setError }),
            h(
              'div',
              { style: { display: 'flex', flexDirection: 'column', gap: 16 } },
              h(AccessCard, { status }),
              h(DiagnosticsCard, { onError: setError }),
            ),
          ),
          h(DeviceCard, { status, onChanged: load, onError: setError }),
          h(UpdateCard, { onError: setError }),
          h('div', { className: 'pr-security' }, h('p', null, h('b', null, `${TEXT.securityTitle}：`), TEXT.securityBody)),
        ),
      )
    }

    return {
      inject: ['slots'],
      apply(ctx) {
        ctx.effect(
          () =>
            ctx.slots.inject('settings.section', () =>
              ctx.slots.register(
                {
                  name: 'settings.section',
                  id: 'phone-remote',
                  order: 45,
                  label: '手机远程',
                },
                PhoneRemotePage,
              ),
            ),
          'phone-remote: settings page',
        )
      },
    }
  },
})
