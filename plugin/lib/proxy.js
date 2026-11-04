/**
 * Reverse proxy from the public phone entry to the local DSH Web listener.
 *
 * DSH's browser-trust fence accepts loopback authorities, so the upstream
 * `Host` (and same-origin `Origin`) are rewritten to `127.0.0.1:<port>` no
 * matter which address the phone dialed. That keeps the deployment free of
 * extra `--trusted-host` flags while the browser-facing cookie audience stays
 * the authority DSH itself computes. WebSocket upgrades use the same rewrite
 * and are authenticated by the gateway before they are piped.
 *
 * @module @local/phone-remote/proxy
 */
import http from 'node:http'
import https from 'node:https'

/** Headers that must not cross one hop. */
const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
])

/**
 * Copy end-to-end request headers and apply the loopback rewrite.
 * @param {http.IncomingMessage} request - inbound request.
 * @param {URL} upstream - local DSH origin.
 * @param {{secure: boolean, ip: string}} context - gateway facts.
 * @returns {Record<string, string|string[]>} upstream headers.
 */
function requestHeaders(request, upstream, context) {
  /** @type {Record<string, string|string[]>} */
  const headers = {}
  for (const [name, value] of Object.entries(request.headers)) {
    if (value === undefined || HOP_BY_HOP.has(name)) continue
    headers[name] = value
  }
  const authority = `127.0.0.1:${upstream.port || '80'}`
  headers.host = authority
  if (headers.origin !== undefined) headers.origin = `http://${authority}`
  headers['x-forwarded-proto'] = context.secure ? 'https' : 'http'
  headers['x-forwarded-host'] = request.headers.host ?? ''
  headers['x-real-ip'] = context.ip
  const previous = request.headers['x-forwarded-for']
  headers['x-forwarded-for'] = typeof previous === 'string' && previous.length > 0 ? `${previous}, ${context.ip}` : context.ip
  return headers
}

/**
 * Copy response headers, dropping hop-by-hop framing that Node re-creates.
 * @param {http.IncomingHttpHeaders} headers - upstream headers.
 * @returns {Record<string, string|string[]>} downstream headers.
 */
function responseHeaders(headers) {
  /** @type {Record<string, string|string[]>} */
  const copy = {}
  for (const [name, value] of Object.entries(headers)) {
    if (value === undefined || HOP_BY_HOP.has(name)) continue
    copy[name] = value
  }
  return copy
}

/**
 * Rewrite an absolute upstream redirect so the phone stays on the gateway.
 * @param {string|string[]} location - upstream Location value.
 * @param {URL} upstream - local DSH origin.
 * @param {string} publicBase - gateway root URL.
 * @returns {string|string[]} rewritten value.
 */
function rewriteLocation(location, upstream, publicBase) {
  if (typeof location !== 'string') return location
  try {
    const target = new URL(location)
    if (target.host === upstream.host) {
      const base = new URL(publicBase)
      base.pathname = target.pathname
      base.search = target.search
      base.hash = target.hash
      return base.href
    }
  } catch {
    // Relative Location values are already correct.
  }
  return location
}

/**
 * Deliver a small plain-text failure when upstream cannot answer.
 * @param {http.ServerResponse} response - downstream response.
 * @param {number} status - HTTP status.
 * @param {string} message - body text.
 */
function fail(response, status, message) {
  if (response.headersSent) {
    response.destroy()
    return
  }
  response.writeHead(status, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' })
  response.end(message)
}

/**
 * Forward one ordinary HTTP request to the local DSH listener.
 * @param {http.IncomingMessage} request - inbound request.
 * @param {http.ServerResponse} response - downstream response.
 * @param {{upstream: URL, secure: boolean, publicBase: string, ip: string}} options - gateway facts.
 */
export function forwardRequest(request, response, options) {
  const { upstream } = options
  const transport = upstream.protocol === 'https:' ? https : http
  const headers = requestHeaders(request, upstream, options)
  const upstreamRequest = transport.request(
    {
      protocol: upstream.protocol,
      hostname: upstream.hostname,
      port: upstream.port || (upstream.protocol === 'https:' ? 443 : 80),
      method: request.method,
      path: request.url,
      headers,
      rejectUnauthorized: false,
    },
    (upstreamResponse) => {
      const headersOut = responseHeaders(upstreamResponse.headers)
      if (headersOut.location !== undefined) headersOut.location = rewriteLocation(headersOut.location, upstream, options.publicBase)
      response.writeHead(upstreamResponse.statusCode ?? 502, headersOut)
      upstreamResponse.pipe(response)
      upstreamResponse.on('error', () => response.destroy())
    },
  )

  // SSE and long-lived event streams must not be cut by a socket timeout.
  upstreamRequest.setTimeout(0)
  upstreamRequest.on('error', (error) => {
    fail(response, 502, `DSH 上游不可达：${error.message}`)
  })
  response.on('close', () => {
    if (!response.writableEnded) upstreamRequest.destroy()
  })
  request.pipe(upstreamRequest)
}

/**
 * Forward one WebSocket (or other upgrade) connection.
 * @param {http.IncomingMessage} request - upgrade request.
 * @param {import('node:stream').Duplex} socket - client socket.
 * @param {Buffer} head - bytes already read from the client.
 * @param {{upstream: URL, secure: boolean, publicBase: string, ip: string}} options - gateway facts.
 */
export function forwardUpgrade(request, socket, head, options) {
  const { upstream } = options
  const transport = upstream.protocol === 'https:' ? https : http
  const headers = requestHeaders(request, upstream, options)
  headers.connection = 'Upgrade'
  headers.upgrade = request.headers.upgrade ?? 'websocket'

  const upstreamRequest = transport.request({
    protocol: upstream.protocol,
    hostname: upstream.hostname,
    port: upstream.port || (upstream.protocol === 'https:' ? 443 : 80),
    method: request.method,
    path: request.url,
    headers,
    rejectUnauthorized: false,
  })

  upstreamRequest.setTimeout(20_000)
  upstreamRequest.on('timeout', () => upstreamRequest.destroy(new Error('upstream upgrade timeout')))
  upstreamRequest.on('error', () => {
    if (!socket.destroyed) socket.end('HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n')
  })

  upstreamRequest.on('upgrade', (upstreamResponse, upstreamSocket, upstreamHead) => {
    upstreamRequest.setTimeout(0)
    let headText = `HTTP/1.1 ${upstreamResponse.statusCode ?? 101} ${upstreamResponse.statusMessage ?? 'Switching Protocols'}\r\n`
    for (const [name, value] of Object.entries(upstreamResponse.headers)) {
      if (Array.isArray(value)) {
        for (const item of value) headText += `${name}: ${item}\r\n`
      } else if (value !== undefined) {
        headText += `${name}: ${value}\r\n`
      }
    }
    headText += '\r\n'
    socket.write(headText)
    if (upstreamHead.length > 0) socket.write(upstreamHead)
    if (head.length > 0) upstreamSocket.write(head)
    upstreamSocket.setNoDelay?.(true)
    socket.setNoDelay?.(true)
    upstreamSocket.pipe(socket)
    socket.pipe(upstreamSocket)
    const close = () => {
      upstreamSocket.destroy()
      socket.destroy()
    }
    upstreamSocket.on('error', close)
    socket.on('error', close)
    upstreamSocket.on('close', () => socket.destroy())
    socket.on('close', () => upstreamSocket.destroy())
  })

  upstreamRequest.on('response', (upstreamResponse) => {
    // A non-101 answer means the upstream refused the upgrade; relay it as-is.
    let text = `HTTP/1.1 ${upstreamResponse.statusCode ?? 400} ${upstreamResponse.statusMessage ?? 'Bad Request'}\r\n`
    for (const [name, value] of Object.entries(upstreamResponse.headers)) {
      if (Array.isArray(value)) {
        for (const item of value) text += `${name}: ${item}\r\n`
      } else if (value !== undefined) text += `${name}: ${value}\r\n`
    }
    socket.end(`${text}\r\n`)
  })

  upstreamRequest.end()
}
