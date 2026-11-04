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
function callDsh(upstream, cookie, method, args) {
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
          host: upstream.host,
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

async function loadQuestions(upstream, cookie, session) {
  const value = await callDsh(upstream, cookie, 'session/projections', { request: { sessionId: session.sessionId } })
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
async function composeInbox(upstream, cookie) {
  const value = await callDsh(upstream, cookie, 'session/list', { _request: {} })
  const sessions = (value.items ?? []).filter((item) => typeof item.sessionId === 'string')
  const running = sessions.filter((item) => item.running === true)
  const questions = []
  for (const session of running.slice(0, 8)) {
    try {
      questions.push(...(await loadQuestions(upstream, cookie, session)))
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
      json(response, await composeInbox(context.upstream, cookie))
      return true
    }
    if (path === '/tasks' && request.method === 'GET') {
      const value = await callDsh(context.upstream, cookie, 'session/list', { _request: {} })
      const tasks = (value.items ?? []).filter((item) => typeof item.sessionId === 'string').map(mapSession)
      json(response, { tasks })
      return true
    }
    const taskMatch = /^\/tasks\/([^/]+)$/u.exec(path)
    if (taskMatch && request.method === 'GET') {
      const sessionId = decodeURIComponent(taskMatch[1])
      const value = await callDsh(context.upstream, cookie, 'session/list', { _request: {} })
      const item = (value.items ?? []).find((entry) => entry.sessionId === sessionId)
      if (!item) {
        json(response, { error: 'task not found' }, 404)
        return true
      }
      const questions = await loadQuestions(context.upstream, cookie, item).catch(() => [])
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
      })
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
