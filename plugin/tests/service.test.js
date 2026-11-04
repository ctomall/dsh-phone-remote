/**
 * Coordinator check: one shared state/pairing table, two independently
 * listening entries, one code rendered for either network.
 */
import assert from 'node:assert/strict'
import http from 'node:http'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { PhoneRemoteState } from '../lib/state.js'
import { PairingCodes } from '../lib/pairing.js'
import { PhoneGateway, normalizeConfig } from '../lib/gateway.js'
import { PhoneRemoteService } from '../lib/service.js'

const upstreamPort = 39381
const publicPort = 39382
const lanPort = 39383
const upstream = http.createServer((request, response) => {
  response.writeHead(200, { 'content-type': 'text/plain' })
  response.end('ok')
})
await new Promise((resolve) => upstream.listen(upstreamPort, '127.0.0.1', resolve))

const dir = await mkdtemp(join(tmpdir(), 'phone-remote-service-'))
const ctx = {
  webServer: { getOrigin: () => `http://127.0.0.1:${upstreamPort}` },
  connection: { authenticatedUrl: (base) => `${base}?token=tok` },
  logger: { info() {}, warn() {} },
}
const state = await PhoneRemoteState.open(dir)
const pairing = new PairingCodes({ ttlSeconds: 300 })
const publicGateway = new PhoneGateway({
  ctx,
  config: normalizeConfig({
    host: '127.0.0.1',
    port: publicPort,
    tlsMode: 'off',
    publicHost: 'public.example.test',
    publicUrl: `https://public.example.test/phone/`,
  }),
  state,
  pairing,
  logger: ctx.logger,
})
const lanGateway = new PhoneGateway({
  ctx,
  config: normalizeConfig({
    host: '127.0.0.1',
    port: lanPort,
    tlsMode: 'off',
    kind: 'lan',
    publicHost: '127.0.0.1',
    publicUrl: `http://127.0.0.1:${lanPort}/phone/`,
  }),
  state,
  pairing,
  logger: ctx.logger,
})
const service = new PhoneRemoteService({
  entries: [
    { id: 'public', label: '公网入口', gateway: publicGateway },
    { id: 'lan', label: '内网入口', gateway: lanGateway },
  ],
  state,
  pairing,
  logger: ctx.logger,
})
await service.start()
assert.equal(publicGateway.phase, 'running', publicGateway.error)
assert.equal(lanGateway.phase, 'running', lanGateway.error)

const status = service.status()
assert.equal(status.entries.length, 2)
assert.equal(status.entries.find((entry) => entry.id === 'lan').urls.public, `http://127.0.0.1:${lanPort}/phone/`)
console.log('✓ aggregated status lists both entries')

const lanPairing = service.createPairing({ entry: 'lan' })
assert.equal(lanPairing.length, 1)
assert.match(lanPairing[0].url, /^http:\/\/127\.0\.0\.1:\d+\/phone\/pair\?code=/u)
console.log('✓ LAN pairing URL uses the LAN base')

const all = service.createPairing({ entry: 'all' })
assert.equal(all.length, 2)
assert.equal(all[0].code, all[1].code)
assert.ok(all.every((item) => item.qr.startsWith('data:image/svg+xml;base64,')))
console.log('✓ one code renders for both entries')

const diagnosis = await service.diagnose()
assert.equal(diagnosis.entries.length, 2)
assert.equal(diagnosis.entries.find((entry) => entry.entry === 'lan').public.ok, true)
console.log('✓ diagnostics covers both entries')

await service.stop()
upstream.closeAllConnections?.()
upstream.close()
await rm(dir, { recursive: true, force: true })
console.log('\nall service checks passed')
process.exit(0)
