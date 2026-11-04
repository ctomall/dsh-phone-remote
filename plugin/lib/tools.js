/**
 * Model-facing control for the phone entry: the agent can report the public
 * URL, mint a short-lived pairing code, list paired devices and revoke one.
 * Minting a code is equivalent to the settings-page button — the agent runs
 * with the owner's authority, and the code still expires in minutes.
 *
 * @module @local/phone-remote/tools
 */

const ACTIONS = ['status', 'pair', 'devices', 'revoke']

/**
 * Render the current gateway status as prompt-ready Chinese text.
 * @param {object} status - service status snapshot.
 * @returns {string} text.
 */
function renderStatus(status) {
  const lines = [
    `手机入口：${phaseLabel(status.phase)}${status.error ? `（${status.error}）` : ''}`,
    `已配对设备：${status.deviceCount} 台`,
  ]
  for (const entry of status.entries ?? []) {
    lines.push(`${entry.label}（${entry.id}）：${phaseLabel(entry.phase)} · ${entry.urls?.public || '未确定'}${entry.error ? `（${entry.error}）` : ''}`)
  }
  if (status.tls) lines.push(`公网证书：${status.tls.mode}，SHA-256 ${status.tls.fingerprint}`)
  for (const warning of status.warnings) lines.push(`提示：${warning.text}`)
  return lines.join('\n')
}

/**
 * Map a gateway phase to a Chinese label.
 * @param {string} phase - phase name.
 * @returns {string} label.
 */
function phaseLabel(phase) {
  if (phase === 'running') return '运行中'
  if (phase === 'starting') return '启动中'
  if (phase === 'error') return '启动失败'
  if (phase === 'disabled') return '已停用'
  return '已停止'
}

/**
 * Render the paired device list.
 * @param {object[]} devices - device views.
 * @returns {string} text.
 */
function renderDevices(devices) {
  if (devices.length === 0) return '尚未配对任何设备。使用 phone_remote 的 pair 操作生成配对码。'
  return devices
    .map((device) => {
      const state = device.revokedAt !== null ? '已撤销' : device.online ? '在线' : '离线'
      const last = device.lastSeenAt > 0 ? new Date(device.lastSeenAt).toISOString() : '从未'
      return `- ${device.name}［${device.id}］${device.platform} · ${state} · 最近 ${last}${device.lastIp ? ` · ${device.lastIp}` : ''}`
    })
    .join('\n')
}

/**
 * Build the `phone_remote` tool.
 * @param {import('./service.js').PhoneRemoteService} service - entry coordinator.
 * @returns {object} tool definition.
 */
export function phoneRemoteTool(service) {
  return {
    name: 'phone_remote',
    description:
      '管理 DSH 的手机远程入口。action：status 查看入口地址、HTTPS 与设备数量；pair 生成一次性配对码（默认 5 分钟有效）并返回手机可打开的链接；devices 列出已配对设备；revoke 按 id 撤销某台设备。配对码等同登录凭据，只在用户明确要求时生成，且不要发到不可信渠道。',
    parameters: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ACTIONS, description: '要执行的操作。' },
        id: { type: 'string', description: '设备 id，revoke 必填。' },
        note: { type: 'string', description: '配对码备注，例如「给用户的 iPhone」。' },
        entry: { type: 'string', enum: ['public', 'lan', 'tunnel', 'all'], description: '生成哪个入口的配对链接：public 公网、lan 内网、all 两个都生成（默认 all）。' },
      },
      required: ['action'],
      additionalProperties: false,
    },
    output: {
      schema: {
        type: 'object',
        properties: {
          summary: { type: 'string', description: '面向用户的中文结果。' },
          url: { type: 'string', description: 'pair 操作返回的手机配对链接。' },
          code: { type: 'string', description: 'pair 操作返回的配对码。' },
          expiresAt: { type: 'integer', description: '配对码过期时间（epoch 毫秒）。' },
        },
        required: ['summary'],
        additionalProperties: false,
      },
      render(_args, value) {
        return [{ type: 'text', text: value.summary }]
      },
    },
    async execute(args) {
      const action = typeof args?.action === 'string' ? args.action : ''
      if (!ACTIONS.includes(action)) throw new Error('action 必须是 status、pair、devices 或 revoke。')
      if (action === 'status') {
        return { summary: renderStatus(service.status()) }
      }
      if (action === 'pair') {
        if (!service.isRunning()) throw new Error('手机入口尚未运行，请先在设置页修复错误。')
        const entry = typeof args.entry === 'string' && args.entry.length > 0 ? args.entry : 'all'
        const pairings = service.createPairing({
          entry,
          note: typeof args.note === 'string' ? args.note : '',
        })
        const first = pairings[0]
        const lines = [`已生成一次性配对码：${first.formatted}（${Math.round(first.ttlSeconds / 60)} 分钟内有效）。`]
        for (const pairing of pairings) lines.push(`${pairing.label}：${pairing.url}`)
        lines.push('也可以让手机相机或鸿蒙 APP 扫描设置页中的二维码；同一条码只能使用一次。')
        return {
          summary: lines.join('\n'),
          url: first.url,
          code: first.code,
          expiresAt: first.expiresAt,
        }
      }
      if (action === 'devices') {
        return { summary: renderDevices(service.state.views()) }
      }
      if (typeof args.id !== 'string' || args.id.length === 0) throw new Error('revoke 需要设备 id。')
      const revoked = service.state.revoke(args.id)
      if (!revoked) throw new Error('设备不存在或已被撤销。')
      await service.state.flush()
      return { summary: `已撤销设备 ${args.id} 的访问权限，下一次请求会被要求重新配对。` }
    },
  }
}

/**
 * Register the tool.
 * @param {import('@deepseek-ai/cordis').Context} ctx - plugin context.
 * @param {import('./service.js').PhoneRemoteService} service - entry coordinator.
 * @returns {() => void} disposer.
 */
export function registerPhoneRemoteTools(ctx, service) {
  return ctx.tools.register(phoneRemoteTool(service))
}
