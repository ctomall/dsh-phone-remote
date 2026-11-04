/**
 * Host half of the phone-remote plugin.
 *
 * One durable device catalog ({@link PhoneRemoteState}) records which phones
 * may enter. Two independently listening gateways can share it:
 *
 * - the public entry, usually behind nginx at an HTTPS domain;
 * - the LAN entry, an HTTP listener bound to one private interface for phones
 *   on the same network (or reachable through a VPN).
 *
 * Both require a paired-device cookie, both proxy to the same local DSH Web
 * server, and the settings page can mint a code for either network.
 *
 * @module @local/phone-remote
 */
import os from 'node:os'
import { readFileSync } from 'node:fs'
import { PhoneRemoteState, resolveDataDir } from './lib/state.js'
import { PairingCodes } from './lib/pairing.js'
import { PhoneGateway, normalizeConfig } from './lib/gateway.js'
import { PhoneRemoteService } from './lib/service.js'
import { CloudflareTunnel, TunnelEntry, normalizeTunnelConfig } from './lib/tunnel.js'
import { createPhoneRemoteRoutes } from './lib/routes.js'
import { UpdateManager } from './lib/updater.js'
import { registerPhoneRemoteTools } from './lib/tools.js'

/** Loader entry name. */
export const name = 'phone-remote'

/**
 * Version of this package, read once from `package.json`. Update checks and
 * the settings page report this instead of a hard-coded literal, so a git
 * fast-forward or an installed archive is reflected after a restart.
 */
export const version = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')).version

/** The gateway needs the Web carrier; the tool needs the Tool registry. */
export const inject = ['webServer', 'tools']

/**
 * Pick the first usable private IPv4 address for the LAN listener.
 * @returns {string} address or empty string.
 */
export function detectLanAddress() {
  const candidates = []
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries ?? []) {
      if (entry.internal) continue
      if (entry.family !== 'IPv4' && entry.family !== 4) continue
      const address = entry.address.split('%')[0]
      if (address.length > 0 && !candidates.includes(address)) candidates.push(address)
    }
  }
  const isPrivate = (address) =>
    /^10\./u.test(address) || /^192\.168\./u.test(address) || /^172\.(1[6-9]|2\d|3[01])\./u.test(address)
  return candidates.find(isPrivate) ?? candidates[0] ?? ''
}

/**
 * Build the LAN gateway options from the plugin row and the detected address.
 * @param {object} raw - plugin row config.
 * @param {{host: string, port: number, basePath: string, ttlSeconds: number, deviceMaxAgeDays: number}} defaults - inherited settings.
 * @param {string} address - LAN IPv4 address.
 * @returns {object} gateway options.
 */
function lanGatewayOptions(raw, defaults, address) {
  const port = Number.isInteger(raw.lanPort) ? raw.lanPort : Number(raw.lanPort)
  const safePort = Number.isInteger(port) && port >= 1 && port <= 65_535 ? port : 8080
  const requestedBase = typeof raw.lanBasePath === 'string' ? raw.lanBasePath.trim() : '/phone'
  const basePath = requestedBase === '/' ? '' : requestedBase.replace(/\/+$/u, '')
  const configuredUrl = typeof raw.lanPublicUrl === 'string' ? raw.lanPublicUrl.trim() : ''
  const publicUrl = configuredUrl.length > 0
    ? configuredUrl
    : `http://${address}:${safePort}${basePath.length > 0 ? `${basePath}/` : '/'}`
  return normalizeConfig({
    enabled: true,
    host: address,
    port: safePort,
    tlsMode: 'off',
    publicHost: address,
    publicUrl,
    basePath,
    trustProxy: false,
    kind: 'lan',
    pairingTtlSec: defaults.ttlSeconds,
    deviceMaxAgeDays: defaults.deviceMaxAgeDays,
  })
}

/**
 * Activate the plugin.
 * @param {import('@deepseek-ai/cordis').Context} ctx - plugin context.
 * @param {object} [config] - row config from `cordis.patch.yml`.
 * @returns {Promise<void>}
 */
export async function apply(ctx, config = {}) {
  const raw = typeof config === 'object' && config !== null ? config : {}
  const options = normalizeConfig(raw)
  const dataDir = resolveDataDir(raw.dataDir)
  const state = await PhoneRemoteState.open(dataDir)
  const pairing = new PairingCodes({ ttlSeconds: options.pairingTtlSec })

  const publicGateway = new PhoneGateway({
    ctx,
    config: options,
    state,
    pairing,
    logger: ctx.logger,
  })
  const entries = [{ id: 'public', label: '公网入口', gateway: publicGateway }]

  const lanAddress = typeof raw.lanHost === 'string' && raw.lanHost.trim().length > 0
    ? raw.lanHost.trim()
    : detectLanAddress()
  const lanEnabled = raw.lanEnabled !== false && lanAddress.length > 0
  if (lanEnabled) {
    const lanOptions = lanGatewayOptions(raw, {
      ttlSeconds: options.pairingTtlSec,
      deviceMaxAgeDays: options.deviceMaxAgeDays,
    }, lanAddress)
    entries.push({
      id: 'lan',
      label: typeof raw.lanLabel === 'string' && raw.lanLabel.trim().length > 0 ? raw.lanLabel.trim() : '内网入口',
      gateway: new PhoneGateway({ ctx, config: lanOptions, state, pairing, logger: ctx.logger }),
    })
  } else if (raw.lanEnabled !== false) {
    ctx.logger?.warn?.('phone-remote：未检测到可用的局域网 IPv4 地址，内网入口未启动。')
  }

  const tunnelOptions = normalizeTunnelConfig(raw, {
    host: options.host,
    port: options.port,
    tlsMode: options.tlsMode,
    basePath: options.basePath,
  })
  if (tunnelOptions.enabled) {
    entries.push({
      id: 'tunnel',
      label: tunnelOptions.label,
      gateway: new TunnelEntry({
        manager: new CloudflareTunnel({ config: tunnelOptions, logger: ctx.logger }),
        gateway: publicGateway,
        basePath: options.basePath,
        label: tunnelOptions.label,
      }),
    })
  }

  const updater = new UpdateManager({
    currentVersion: version,
    feedUrl: typeof raw.updateFeedUrl === 'string' ? raw.updateFeedUrl.trim() : '',
    publicKey: typeof raw.updatePublicKey === 'string' && raw.updatePublicKey.trim().length > 0
      ? raw.updatePublicKey.trim()
      : undefined,
    requireSignature: raw.updateRequireSignature !== false,
    mode: raw.updateMode === 'git' || raw.updateMode === 'zip' ? raw.updateMode : 'auto',
    remote: typeof raw.updateGitRemote === 'string' && raw.updateGitRemote.trim().length > 0
      ? raw.updateGitRemote.trim()
      : 'origin',
    logger: ctx.logger,
  })
  const service = new PhoneRemoteService({ entries, state, pairing, updater, logger: ctx.logger })

  ctx.effect(() => ctx.provide('phoneRemote', service), 'phone-remote: service')
  ctx.effect(
    () => () => {
      void service.stop()
    },
    'phone-remote: gateway lifetime',
  )

  // Listening includes a bounded public-IP lookup and certificate generation,
  // so activation does not block the Host on that work.
  void service.start().catch((error) => {
    ctx.logger?.warn?.(`phone-remote：入口启动异常：${error instanceof Error ? error.message : String(error)}`)
  })

  ctx.inject(['connection'], (connectionCtx) => {
    for (const route of createPhoneRemoteRoutes(service)) {
      connectionCtx.effect(() => connectionCtx.connection.fetch.register(route), `phone-remote: route ${route.path}`)
    }
  })

  ctx.effect(() => registerPhoneRemoteTools(ctx, service), 'phone-remote: tool')

  ctx.logger?.info?.(
    `phone-remote：已加载 ${entries.length} 个入口（${entries.map((entry) => entry.id).join('、')}），设备 ${state.count()} 台，数据目录 ${dataDir}`,
  )
}
