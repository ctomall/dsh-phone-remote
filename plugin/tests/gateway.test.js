/**
 * Standalone integration check for the phone gateway: fake upstream, pairing,
 * cookie gate, proxy rewrite, revocation and TLS generation. Run with
 * `node tests/gateway.test.js`.
 */
import assert from 'node:assert/strict'
import http from 'node:http'
import https from 'node:https'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { PhoneRemoteState } from '../lib/state.js'
import { PairingCodes } from '../lib/pairing.js'
import { PhoneGateway, normalizeConfig } from '../lib/gateway.js'
import { qrSvg } from '../lib/qr.js'

const upstreamPort = 39181
const gatewayPort = 39182
const tlsPort = 39183

let seen = []

const upstream = http.createServer((request, response) => {
  seen.push({ url: request.url, host: request.headers.host, origin: request.headers.origin })
  if (request.url === '/redirect') {
    response.writeHead(302, { location: `http://127.0.0.1:${upstreamPort}/target` })
    response.end()
    return
  }
  response.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' })
  response.end(`upstream:${request.url}`)
})

upstream.on('upgrade', (request, socket) => {
  socket.write('HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n')
  socket.on('data', (chunk) => socket.write(Buffer.concat([Buffer.from('echo:'), chunk])))
})

const dir = await mkdtemp(join(tmpdir(), 'phone-remote-'))
await new Promise((resolve) => upstream.listen(upstreamPort, '127.0.0.1', resolve))

const ctx = {
  webServer: { getOrigin: () => `http://127.0.0.1:${upstreamPort}` },
  connection: { authenticatedUrl: (base) => `${base}?token=test-launch-token` },
  logger: { info() {}, warn() {} },
}

const state = await PhoneRemoteState.open(dir)
const pairing = new PairingCodes({ ttlSeconds: 300 })
const gateway = new PhoneGateway({
  ctx,
  config: normalizeConfig({ enabled: true, host: '127.0.0.1', port: gatewayPort, tlsMode: 'off', publicHost: '127.0.0.1', publicUrl: `http://127.0.0.1:${gatewayPort}/` }),
  state,
  pairing,
  logger: ctx.logger,
})
await gateway.start()
assert.equal(gateway.phase, 'running', `gateway started: ${gateway.error}`)
console.log('✓ gateway listening')

const health = await fetch(`http://127.0.0.1:${gatewayPort}/healthz`)
assert.equal(health.status, 200)
assert.equal((await health.json()).ok, true)
console.log('✓ healthz')

const gate = await fetch(`http://127.0.0.1:${gatewayPort}/`, { headers: { accept: 'text/html' }, redirect: 'manual' })
assert.equal(gate.status, 302)
assert.match(gate.headers.get('location') ?? '', /^\/pair/u)
console.log('✓ unpaired navigation redirects to /pair')

const apiGate = await fetch(`http://127.0.0.1:${gatewayPort}/api/status`, { headers: { accept: 'application/json' } })
assert.equal(apiGate.status, 401)
console.log('✓ unpaired API is 401')

const pairPage = await fetch(`http://127.0.0.1:${gatewayPort}/pair`)
assert.equal(pairPage.status, 200)
assert.match(await pairPage.text(), /连接 DSH/u)
console.log('✓ pairing page renders')

const issued = gateway.createPairing({ note: 'test' })
assert.match(issued.url, /\/pair\?code=/u)
assert.match(issued.qr, /^data:image\/svg\+xml;base64,/u)
assert.ok(qrSvg('test').includes('<svg'))
console.log('✓ pairing code + QR')

const form = new URLSearchParams({ code: issued.formatted, name: '测试手机' })
const paired = await fetch(`http://127.0.0.1:${gatewayPort}/pair`, {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded', 'user-agent': 'Mozilla/5.0 (Linux; HarmonyOS 5)' },
  body: form,
  redirect: 'manual',
})
assert.equal(paired.status, 303)
assert.equal(paired.headers.get('location'), '/?token=test-launch-token')
const cookie = (paired.headers.getSetCookie?.() ?? [paired.headers.get('set-cookie')])[0].split(';')[0]
assert.match(cookie, /^dsh_phone=/u)
console.log('✓ pairing sets device cookie and token redirect')

const proxied = await fetch(`http://127.0.0.1:${gatewayPort}/`, { headers: { cookie, accept: 'text/html' } })
assert.equal(proxied.status, 200)
assert.equal(await proxied.text(), 'upstream:/')
const last = seen.at(-1)
assert.equal(last.host, `127.0.0.1:${upstreamPort}`)
console.log('✓ paired request proxies to the loopback authority')

const redirecting = await fetch(`http://127.0.0.1:${gatewayPort}/redirect`, { headers: { cookie }, redirect: 'manual' })
assert.equal(redirecting.status, 302)
assert.equal(redirecting.headers.get('location'), `http://127.0.0.1:${gatewayPort}/target`)
console.log('✓ absolute upstream redirect stays on the gateway')

const devices = gateway.state.views()
assert.equal(devices.length, 1)
assert.equal(devices[0].name, '测试手机')
assert.equal(devices[0].platform, 'HarmonyOS')
console.log('✓ device recorded')

// WebSocket-ish upgrade: authenticated handshake, echo through the proxy.
await new Promise((resolve, reject) => {
  const request = http.request({
    host: '127.0.0.1',
    port: gatewayPort,
    path: '/socket',
    headers: {
      connection: 'Upgrade',
      upgrade: 'websocket',
      'sec-websocket-key': 'dGhlIHNhbXBsZSBub25jZQ==',
      'sec-websocket-version': '13',
      cookie,
    },
  })
  request.on('upgrade', (response, socket) => {
    assert.equal(response.statusCode, 101)
    socket.write('ping')
    socket.once('data', (chunk) => {
      assert.equal(chunk.toString(), 'echo:ping')
      socket.destroy()
      resolve()
    })
  })
  request.on('response', (response) => {
    reject(new Error(`upgrade rejected with ${response.statusCode}`))
  })
  request.on('error', reject)
  request.end()
})
console.log('✓ upgrade proxy echoes through the gateway')

const badCookie = await fetch(`http://127.0.0.1:${gatewayPort}/api/status`, { headers: { cookie: 'dsh_phone=deadbeef.bad' } })
assert.equal(badCookie.status, 401)
console.log('✓ forged cookie is refused')

assert.equal(gateway.state.revoke(devices[0].id), true)
const revoked = await fetch(`http://127.0.0.1:${gatewayPort}/`, { headers: { cookie, accept: 'text/html' }, redirect: 'manual' })
assert.equal(revoked.status, 302)
console.log('✓ revoked device is redirected to /pair')

await gateway.stop()
upstream.close()

// TLS: self-signed generation, HTTPS listener and fingerprint.
const tlsDir = await mkdtemp(join(tmpdir(), 'phone-remote-tls-'))
const tlsState = await PhoneRemoteState.open(tlsDir)
const tlsGateway = new PhoneGateway({
  ctx,
  config: normalizeConfig({ enabled: true, host: '127.0.0.1', port: tlsPort, tlsMode: 'self-signed', publicHost: '127.0.0.1', publicUrl: `https://127.0.0.1:${tlsPort}/` }),
  state: tlsState,
  pairing: new PairingCodes({ ttlSeconds: 300 }),
  logger: ctx.logger,
})
await tlsGateway.start()
assert.equal(tlsGateway.phase, 'running', `TLS gateway: ${tlsGateway.error}`)
assert.match(tlsGateway.tls.fingerprint256, /^[0-9A-F:]{95}$/u)
const secure = await new Promise((resolve, reject) => {
  https.get({ host: '127.0.0.1', port: tlsPort, path: '/healthz', rejectUnauthorized: false }, (response) => {
    let body = ''
    response.on('data', (chunk) => {
      body += chunk
    })
    response.on('end', () => resolve({ status: response.statusCode, body }))
  }).on('error', reject)
})
assert.equal(secure.status, 200)
assert.match(secure.body, /dsh-phone-remote/u)
await tlsGateway.stop()
console.log('✓ self-signed TLS listener works')

await rm(dir, { recursive: true, force: true })
await rm(tlsDir, { recursive: true, force: true })
console.log('\nall gateway checks passed')
process.exit(0)
