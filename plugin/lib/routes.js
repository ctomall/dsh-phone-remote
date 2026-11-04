/**
 * Authenticated browser routes for the phone-remote settings page. They are
 * registered on the shared `/api` channel through Connection, so the Host's
 * browser-trust fence and login cookie already protect every one of them.
 * The public gateway never exposes these paths without a paired-device cookie
 * of its own.
 *
 * @module @local/phone-remote/routes
 */

const NO_STORE = { 'cache-control': 'no-store' }
const MAX_BODY_CHARS = 256 * 1024

/**
 * Build one JSON response.
 * @param {unknown} value - payload.
 * @param {number} [status] - HTTP status.
 * @returns {Response} response.
 */
function json(value, status = 200) {
  return Response.json(value, { status, headers: NO_STORE })
}

/**
 * Read one JSON object body.
 * @param {Request} request - inbound request.
 * @returns {Promise<Record<string, unknown>>} parsed body.
 */
async function readBody(request) {
  const text = await request.text()
  if (text.length > MAX_BODY_CHARS) throw new HttpError(413, '请求体过大。')
  if (text.trim().length === 0) return {}
  let value
  try {
    value = JSON.parse(text)
  } catch {
    throw new HttpError(400, '请求体必须是合法的 JSON。')
  }
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new HttpError(400, '请求体必须是 JSON 对象。')
  }
  return value
}

/** One route failure with an HTTP status. */
class HttpError extends Error {
  /**
   * @param {number} status - HTTP status.
   * @param {string} message - message.
   */
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

/**
 * Wrap a handler with uniform JSON failure mapping.
 * @param {(request: Request) => Promise<Response>} handler - route body.
 * @returns {(request: Request) => Promise<Response>} guarded handler.
 */
function guard(handler) {
  return async (request) => {
    try {
      return await handler(request)
    } catch (error) {
      const status = error instanceof HttpError ? error.status : 500
      return json({ error: error instanceof Error ? error.message : String(error) }, status)
    }
  }
}

/**
 * Build the exact Fetch routes the settings page consumes.
 * @param {import('./service.js').PhoneRemoteService} service - entry coordinator.
 * @returns {object[]} `ConnectionFetchRoute` records.
 */
export function createPhoneRemoteRoutes(service) {
  const status = guard(async () => json(service.status()))

  const pair = guard(async (request) => {
    const body = await readBody(request)
    if (!service.isRunning()) {
      throw new HttpError(409, '手机入口尚未运行，请先修复入口错误。')
    }
    const entry = typeof body.entry === 'string' && body.entry.length > 0 ? body.entry : 'public'
    let pairings
    try {
      pairings = service.createPairing({
        entry,
        note: typeof body.note === 'string' ? body.note : '',
      })
    } catch (error) {
      throw new HttpError(409, error instanceof Error ? error.message : String(error))
    }
    return json({ pairing: pairings[0], pairings })
  })

  const devices = guard(async () => json({ devices: service.state.views() }))

  const revoke = guard(async (request) => {
    const body = await readBody(request)
    if (typeof body.id !== 'string' || body.id.length === 0) throw new HttpError(400, '缺少设备 id。')
    const revoked = service.state.revoke(body.id)
    if (!revoked) throw new HttpError(404, '设备不存在或已被撤销。')
    await service.state.flush()
    return json({ revoked: true, devices: service.state.views() })
  })

  const diagnose = guard(async () => json(await service.diagnose()))

  const updateStatus = guard(async () =>
    json(service.updater ? await service.updater.check() : { currentVersion: '', available: false, error: '插件更新未启用' }),
  )

  const updateApply = guard(async () =>
    json(service.updater ? await service.updater.apply() : { error: '插件更新未启用' }),
  )

  return [
    { path: '/api/phone-remote/status', methods: ['GET'], requestBody: 'buffered', fetch: status },
    { path: '/api/phone-remote/pairing', methods: ['POST'], requestBody: 'buffered', fetch: pair },
    { path: '/api/phone-remote/devices', methods: ['GET'], requestBody: 'buffered', fetch: devices },
    { path: '/api/phone-remote/devices/revoke', methods: ['POST'], requestBody: 'buffered', fetch: revoke },
    { path: '/api/phone-remote/diagnose', methods: ['POST'], requestBody: 'buffered', fetch: diagnose },
    { path: '/api/phone-remote/update', methods: ['GET'], requestBody: 'buffered', fetch: updateStatus },
    { path: '/api/phone-remote/update/apply', methods: ['POST'], requestBody: 'buffered', fetch: updateApply },
  ]
}
