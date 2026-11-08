/**
 * Mobile BFF: stable endpoints for the Android/HarmonyOS clients.
 *
 * The clients should not parse raw DSH events. This module composes the
 * gateway's upstream DSH Web API (session list, projections, answers) into a
 * small mobile protocol under `/api/mobile/v1`.
 *
 * @module @local/phone-remote/mobile
 */
import http from 'node:http'
import { randomUUID } from 'node:crypto'

const PREFIX = '/api/mobile/v1'
const MAX_BODY = 256 * 1024

function json(response, value, status = 200) {
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
  })
  response.end(JSON.stringify(value))
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let body = ''
    request.on('data', (chunk) => {
      body += chunk.toString('utf8')
      if (body.length > MAX_BODY) reject(new Error('body too large'))
    })
    request.on('end', () => {
      if (body.trim().length === 0) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(body))
      } catch (error) {
        reject(new Error(`invalid json: ${error.message}`))
      }
    })
    request.on('error', reject)
  })
}

/**
 * POST one client-request envelope to the local DSH Web API.
 * @param {URL} upstream - local DSH origin.
 * @param {string} cookie - inbound cookie header.
 * @param {string} method - DSH method name.
 * @param {object} args - args payload.
 * @returns {Promise<object>} `result.value`.
 */
function callDsh(upstream, cookie, method, args, trustedAuthority) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      type: 'client-request',
      rpcId: randomUUID(),
      method,
      payload: { args },
    })
    const request = http.request(
      {
        hostname: upstream.hostname,
        port: upstream.port,
        path: `/api/${method}`,
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'content-length': Buffer.byteLength(body),
          host: trustedAuthority && trustedAuthority.length > 0 ? trustedAuthority : upstream.host,
          cookie,
        },
        timeout: 15_000,
      },
      (response) => {
        let text = ''
        response.on('data', (chunk) => { text += chunk.toString('utf8') })
        response.on('end', () => {
          if (response.statusCode !== 200) {
            reject(new Error(`upstream ${method} ${response.statusCode}`))
            return
          }
          try {
            const parsed = JSON.parse(text)
            if (parsed.result?.ok === false) {
              reject(new Error(parsed.result.error?.message ?? `${method} failed`))
              return
            }
            resolve(parsed.result?.value ?? {})
          } catch (error) {
            reject(new Error(`upstream ${method} invalid json: ${error.message}`))
          }
        })
      },
    )
    request.on('timeout', () => request.destroy(new Error('upstream timeout')))
    request.on('error', reject)
    request.end(body)
  })
}

function sessionTitle(item) {
  return item.projections?.values?.title?.trim() || item.sessionId
}

function mapSession(item) {
  return {
    id: item.sessionId,
    title: sessionTitle(item),
    cwd: item.cwd ?? '',
    running: item.running === true,
    updatedAt: item.updatedAt ?? 0,
  }
}

function mapQuestion(session, pending) {
  const first = pending.questions?.[0]
  return {
    kind: 'question',
    sessionId: session.id,
    sessionTitle: sessionTitle(session),
    callId: pending.callId,
    questionId: first?.id ?? '',
    header: first?.header ?? '',
    question: first?.question ?? '',
    detail: first?.detail ?? '',
    multiSelect: first?.multiSelect === true,
    options: (first?.options ?? []).map((option) => ({ label: option.label, description: option.description ?? '' })),
  }
}

async function loadQuestions(upstream, cookie, session, trustedAuthority) {
  const value = await callDsh(upstream, cookie, 'session/projections', { request: { sessionId: session.sessionId } }, trustedAuthority)
  const active = value.values?.userQuestions?.active ?? []
  return active
    .filter((pending) => typeof pending.callId === 'string' && pending.callId.length > 0)
    .map((pending) => mapQuestion(session, pending))
}

/**
 * Build the mobile inbox: questions from running sessions, running tasks and
 * recently finished tasks. Failures degrade to an empty question list rather
 * than failing the whole page.
 * @param {URL} upstream - local DSH origin.
 * @param {string} cookie - inbound cookie header.
 * @returns {Promise<object>} inbox payload.
 */
async function composeInbox(upstream, cookie, trustedAuthority) {
  const value = await callDsh(upstream, cookie, 'session/list', { _request: {} }, trustedAuthority)
  const sessions = (value.items ?? []).filter((item) => typeof item.sessionId === 'string')
  const running = sessions.filter((item) => item.running === true)
  const questions = []
  for (const session of running.slice(0, 8)) {
    try {
      questions.push(...(await loadQuestions(upstream, cookie, session, trustedAuthority)))
    } catch {
      // one broken session must not blank the inbox
    }
  }
  return {
    questions,
    running: running.map(mapSession),
    recent: sessions.filter((item) => item.running !== true).map(mapSession).slice(0, 10),
    counts: { questions: questions.length, running: running.length },
  }
}

/**
 * Collect host facts for the phone monitor page: CPU/memory plus disk usage and
 * the busiest processes. Dependencies are injectable so the fixture test can
 * assert the shape without a real host.
 * @param {object} [deps] - overrides for tests.
 * @param {(args: string[]) => Promise<string>} [deps.run] - command runner.
 * @param {(path: string) => Promise<{bsize:number,blocks:number,bavail:number}>} [deps.statfs] - statfs.
 * @param {() => number} [deps.now] - clock.
 * @returns {Promise<object>} host facts.
 */
export async function collectHostFacts(deps = {}) {
  const os = await import('node:os')
  const run = deps.run ?? (async (args) => {
    const { execFile } = await import('node:child_process')
    return await new Promise((resolve, reject) => {
      execFile(args[0], args.slice(1), { timeout: 4000, maxBuffer: 1 << 20 }, (error, stdout) => {
        if (error) reject(error)
        else resolve(String(stdout))
      })
    })
  })
  const statfs = deps.statfs ?? (async (path) => {
    const fsp = await import('node:fs/promises')
    return await fsp.statfs(path)
  })

  const mem = process.memoryUsage()
  const load = os.loadavg()
  const facts = {
    host: os.hostname(),
    platform: `${os.platform()} ${os.release()}`,
    uptimeSec: Math.round(process.uptime()),
    load1: Number(load[0].toFixed(2)),
    load5: Number(load[1].toFixed(2)),
    rssMb: Math.round(mem.rss / 1048576),
    heapMb: Math.round(mem.heapUsed / 1048576),
    cpus: os.cpus().length,
    disk: [],
    processes: [],
  }

  // 磁盘：根与当前工作目录各一条；任一路径失败不影响另一条。
  for (const path of ['/', process.cwd()]) {
    try {
      const st = await statfs(path)
      const total = Number(st.bsize) * Number(st.blocks)
      const free = Number(st.bsize) * Number(st.bavail)
      if (Number.isFinite(total) && total > 0) {
        facts.disk.push({
          path,
          totalMb: Math.round(total / 1048576),
          freeMb: Math.round(free / 1048576),
          usedPct: Math.round(((total - free) / total) * 1000) / 10,
        })
      }
    } catch {
      // 单个路径不可读就跳过，不让监控页整体失败
    }
  }

  // 进程：ps 的 CPU 前 8 名；非 Linux/无 ps 时留空数组。
  if (os.platform() === 'linux' || deps.run !== undefined) {
    try {
      const out = await run(['ps', '-eo', 'pid=,pcpu=,pmem=,rss=,comm=', '--sort=-pcpu'])
      const rows = String(out).split('\n')
        .map((line) => line.trim())
        .filter((line) => line.length > 0)
        // 先剔除 ps 自己再取前 8，否则采样进程会挤掉一个真实进程
        .filter((line) => !/\sps$/.test(line))
        .slice(0, 8)
        .map((line) => {
          const parts = line.split(/\s+/)
          const pid = Number(parts[0])
          const cpu = Number(parts[1])
          const memPct = Number(parts[2])
          const rssKb = Number(parts[3])
          const name = parts.slice(4).join(' ')
          if (!Number.isFinite(pid)) return null
          return {
            pid,
            cpuPct: Number.isFinite(cpu) ? Math.round(cpu * 10) / 10 : 0,
            memPct: Number.isFinite(memPct) ? Math.round(memPct * 10) / 10 : 0,
            rssMb: Number.isFinite(rssKb) ? Math.round(rssKb / 1024) : 0,
            name: name.slice(0, 40),
          }
        })
        .filter((row) => row !== null)
      facts.processes = rows
    } catch {
      // ps 不可用：保持空数组，前端显示「主机未提供」
    }
  }

  return facts
}

/**
 * Handle one `/api/mobile/v1` request.
 * @param {http.IncomingMessage} request - inbound request.
 * @param {http.ServerResponse} response - downstream response.
 * @param {{upstream: URL}} context - gateway facts.
 * @returns {Promise<boolean>} whether the path was handled.
 */
export async function handleMobileRequest(request, response, context) {
  const url = new URL(request.url ?? '/', 'http://dsh.invalid')
  if (!url.pathname.startsWith(PREFIX)) return false
  const cookie = request.headers.cookie ?? ''
  const path = url.pathname.slice(PREFIX.length)
  try {
    if (path === '/inbox' && request.method === 'GET') {
      json(response, await composeInbox(context.upstream, cookie, context.trustedAuthority))
      return true
    }
    if (path === '/tasks' && request.method === 'GET') {
      const value = await callDsh(context.upstream, cookie, 'session/list', { _request: {} }, context.trustedAuthority)
      const tasks = (value.items ?? []).filter((item) => typeof item.sessionId === 'string').map(mapSession)
      json(response, { tasks })
      return true
    }
    // C5 服务器监控：网关进程所在的机器事实（真实数字，不是编的）
    if (path === '/monitor' && request.method === 'GET') {
      json(response, await collectHostFacts())
      return true
    }
    // 在线升级：清单与 APK 都从网关出，彻底绕开 GitHub 可达性
    // （局域网里只有网关 8080 活着时也能升级）
    if (path === '/update' && request.method === 'GET') {
      const fsp = await import('node:fs/promises')
      const manifestPath = process.env.DSH_POCKET_MANIFEST
        ?? '/var/www/tongpin-demo/dsh-pocket/android.json'
      const text = await fsp.readFile(manifestPath, 'utf8')
      const manifest = JSON.parse(text)
      const origin = `http://${request.headers.host ?? context.upstream.host}`
      manifest.url = `${origin}/phone/api/mobile/v1/apk?v=${encodeURIComponent(manifest.version)}`
      manifest.fallback = undefined
      json(response, manifest)
      return true
    }
    if (path === '/apk' && request.method === 'GET') {
      const fsp = await import('node:fs/promises')
      const pathmod = await import('node:path')
      const version = url.searchParams.get('v') ?? ''
      if (!/^[0-9]+\.[0-9]+\.[0-9]+$/.test(version)) {
        response.writeHead(400, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ error: 'bad version' }))
        return true
      }
      const dir = process.env.DSH_POCKET_DIR ?? '/var/www/tongpin-demo/dsh-pocket'
      const file = pathmod.join(dir, `dsh-pocket-${version}.apk`)
      const bytes = await fsp.readFile(file)
      response.writeHead(200, {
        'content-type': 'application/vnd.android.package-archive',
        'content-length': String(bytes.byteLength),
        'cache-control': 'no-store',
      })
      response.end(bytes)
      return true
    }
    // C1 插件页：读本 profile 真实安装的插件包（node_modules 的 package.json），不是猜的
    if (path === '/plugins' && request.method === 'GET') {
      const fsp = await import('node:fs/promises')
      const modulePath = await import('node:path')
      const roots = [
        modulePath.join(process.env.DSH_PROFILE_DIR ?? '', '..'),
      ]
      const plugins = []
      const dirs = new Set()
      // 依次探测：profile 目录下 node_modules（web/headless 两个 profile 都试）
      for (const base of ['/home/pro/.dsh/profiles/web', '/home/pro/.dsh/profiles/headless']) {
        dirs.add(modulePath.join(base, 'node_modules'))
      }
      for (const dir of dirs) {
        let entries = []
        try { entries = await fsp.readdir(dir, { withFileTypes: true }) } catch { continue }
        for (const ent of entries) {
          const candidates = []
          if (ent.name.startsWith('@')) {
            let subs = []
            try { subs = await fsp.readdir(modulePath.join(dir, ent.name), { withFileTypes: true }) } catch { continue }
            for (const sub of subs) candidates.push(modulePath.join(dir, ent.name, sub.name, 'package.json'))
          } else {
            candidates.push(modulePath.join(dir, ent.name, 'package.json'))
          }
          for (const file of candidates) {
            let pkg
            try { pkg = JSON.parse(await fsp.readFile(file, 'utf8')) } catch { continue }
            const name = typeof pkg.name === 'string' ? pkg.name : ''
            if (!name || !(name.includes('dsh') || name.startsWith('@deepseek-ai') || name.includes('cordis'))) continue
            if (plugins.some((x) => x.name === name)) continue
            plugins.push({
              name,
              version: typeof pkg.version === 'string' ? pkg.version : '',
              description: typeof pkg.description === 'string' ? pkg.description : '',
            })
          }
        }
      }
      plugins.sort((a, b) => a.name.localeCompare(b.name))
      json(response, { plugins, count: plugins.length })
      return true
    }
    const taskMatch = /^\/tasks\/([^/]+)$/u.exec(path)
    if (taskMatch && request.method === 'GET') {
      const sessionId = decodeURIComponent(taskMatch[1])
      const value = await callDsh(context.upstream, cookie, 'session/list', { _request: {} }, context.trustedAuthority)
      const item = (value.items ?? []).find((entry) => entry.sessionId === sessionId)
      if (!item) {
        json(response, { error: 'task not found' }, 404)
        return true
      }
      const questions = await loadQuestions(context.upstream, cookie, item, context.trustedAuthority).catch(() => [])
      json(response, { task: mapSession(item), questions })
      return true
    }
    const answerMatch = /^\/questions\/([^/]+)\/([^/]+)\/answer$/u.exec(path)
    if (answerMatch && request.method === 'POST') {
      const sessionId = decodeURIComponent(answerMatch[1])
      const callId = decodeURIComponent(answerMatch[2])
      const body = await readJson(request)
      const answers = Array.isArray(body.answers) ? body.answers : []
      await callDsh(context.upstream, cookie, 'userQuestions/answer', {
        agentId: sessionId,
        callId,
        answer: { answers },
      }, context.trustedAuthority)
      json(response, { ok: true })
      return true
    }
    json(response, { error: 'not found' }, 404)
    return true
  } catch (error) {
    json(response, { error: error instanceof Error ? error.message : String(error) }, 502)
    return true
  }
}
