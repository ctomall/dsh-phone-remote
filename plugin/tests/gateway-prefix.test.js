/**
 * Mounted-mode check: the gateway serves under `/phone/`, strips the prefix
 * before proxying, and keeps pairing/redirects inside the prefix. This is the
 * shape used behind an existing HTTPS reverse proxy.
 */
import assert from 'node:assert/strict'
import http from 'node:http'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { PhoneRemoteState } from '../lib/state.js'
import { PairingCodes } from '../lib/pairing.js'
import { PhoneGateway, normalizeConfig } from '../lib/gateway.js'

const upstreamPort = 39281
const gatewayPort = 39282
let seen = []
const upstream = http.createServer((request, response) => {
  seen.push(request.url)
  response.writeHead(200, { 'content-type': 'text/plain' })
  response.end(`upstream:${request.url}`)
})
upstream.on('upgrade', (request, socket) => {
  socket.write('HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n')
  socket.once('data', (chunk) => {
    socket.write(`echo:${chunk}`)
    socket.destroy()
  })
})
await new Promise((resolve) => upstream.listen(upstreamPort, '127.0.0.1', resolve))

const dir = await mkdtemp(join(tmpdir(), 'phone-remote-prefix-'))
const ctx = {
  webServer: { getOrigin: () => `http://127.0.0.1:${upstreamPort}` },
  connection: { authenticatedUrl: (base) => `${base}?token=tok` },
  logger: { info() {}, warn() {} },
}
const state = await PhoneRemoteState.open(dir)
const gateway = new PhoneGateway({
  ctx,
  config: normalizeConfig({
    host: '127.0.0.1',
    port: gatewayPort,
    tlsMode: 'off',
    trustProxy: true,
    publicHost: 'phone.example.test',
    publicUrl: `https://phone.example.test/phone/`,
  }),
  state,
  pairing: new PairingCodes({ ttlSeconds: 300 }),
  logger: ctx.logger,
})
await gateway.start()
assert.equal(gateway.phase, 'running', gateway.error)
assert.equal(gateway.baseUrl(), 'https://phone.example.test/phone/')

const outside = await fetch(`http://127.0.0.1:${gatewayPort}/healthz`)
assert.equal(outside.status, 404)
const inside = await fetch(`http://127.0.0.1:${gatewayPort}/phone/healthz`)
assert.equal(inside.status, 200)
console.log('✓ prefix isolation')

const gate = await fetch(`http://127.0.0.1:${gatewayPort}/phone/`, { headers: { accept: 'text/html' }, redirect: 'manual' })
assert.equal(gate.status, 302)
assert.equal(gate.headers.get('location'), '/phone/pair')
console.log('✓ unpaired redirect stays in prefix')

const page = await fetch(`http://127.0.0.1:${gatewayPort}/phone/pair`)
assert.equal(page.status, 200)
const html = await page.text()
assert.match(html, /action="\/phone\/pair"/u)
console.log('✓ pairing page form action includes prefix')

const issued = gateway.createPairing({})
assert.match(issued.url, /^https:\/\/phone\.example\.test\/phone\/pair\?code=/u)
const paired = await fetch(`http://127.0.0.1:${gatewayPort}/phone/pair`, {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ code: issued.formatted, name: '手机' }),
  redirect: 'manual',
})
assert.equal(paired.status, 303)
assert.equal(paired.headers.get('location'), '/phone/?token=tok')
const cookie = (paired.headers.getSetCookie?.() ?? [paired.headers.get('set-cookie')])[0].split(';')[0]
console.log('✓ pairing redirect includes prefix')

const proxied = await fetch(`http://127.0.0.1:${gatewayPort}/phone/foo?x=1`, { headers: { cookie } })
assert.equal(await proxied.text(), 'upstream:/foo?x=1')
assert.equal(seen.at(-1), '/foo?x=1')
console.log('✓ prefix stripped before upstream')

await new Promise((resolve, reject) => {
  const request = http.request({
    host: '127.0.0.1',
    port: gatewayPort,
    path: '/phone/socket',
    headers: {
      connection: 'Upgrade',
      upgrade: 'websocket',
      'sec-websocket-key': 'dGhlIHNhbXBsZSBub25jZQ==',
      'sec-websocket-version': '13',
      cookie,
    },
  })
  request.on('upgrade', (_response, socket) => {
    socket.write('ping')
    socket.once('data', (chunk) => {
      assert.equal(chunk.toString(), 'echo:ping')
      socket.destroy()
      resolve()
    })
  })
  request.on('response', (response) => reject(new Error(`upgrade ${response.statusCode}`)))
  request.on('error', reject)
  request.end()
})
console.log('✓ prefixed upgrade proxies')

await gateway.stop()
upstream.closeAllConnections?.()
upstream.close()
await rm(dir, { recursive: true, force: true })
console.log('\nall prefix checks passed')
process.exit(0)
