/**
 * Mobile BFF integration check: a fake DSH upstream and a handler server.
 * Run with `node tests/mobile.test.js`.
 */
import assert from 'node:assert/strict'
import http from 'node:http'
import { handleMobileRequest } from '../lib/mobile.js'

const upstreamPort = 39381
const handlerPort = 39382
const seen = []

const upstream = http.createServer((request, response) => {
  let body = ''
  request.on('data', (chunk) => { body += chunk.toString('utf8') })
  request.on('end', () => {
    const envelope = JSON.parse(body)
    seen.push({ method: envelope.method, args: envelope.payload?.args, cookie: request.headers.cookie })
    let value = {}
    if (envelope.method === 'session/list') {
      value = {
        items: [
          { sessionId: 's-run', updatedAt: 3, running: true, cwd: '/work/a', projections: { values: { title: '修复构建' } } },
          { sessionId: 's-done', updatedAt: 2, running: false, cwd: '/work/b', projections: { values: { title: '发布版本' } } },
        ],
      }
    } else if (envelope.method === 'session/projections') {
      const sessionId = envelope.payload.args.request.sessionId
      value = sessionId === 's-run'
        ? {
            values: {
              userQuestions: {
                active: [{
                  callId: 'call-1',
                  questions: [{
                    id: 'q1',
                    header: '确认',
                    question: '是否继续？',
                    multiSelect: false,
                    options: [{ label: '继续' }, { label: '暂不', description: '保留改动' }],
                  }],
                }],
              },
            },
          }
        : { values: { userQuestions: { active: [] } } }
    }
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(JSON.stringify({ result: { ok: true, value } }))
  })
})

const handler = http.createServer((request, response) => {
  handleMobileRequest(request, response, { upstream: new URL(`http://127.0.0.1:${upstreamPort}`) })
})

const fetchJson = async (path, options = {}) => {
  const response = await fetch(`http://127.0.0.1:${handlerPort}${path}`, options)
  return { status: response.status, body: await response.json() }
}

await new Promise((resolve) => upstream.listen(upstreamPort, '127.0.0.1', resolve))
await new Promise((resolve) => handler.listen(handlerPort, '127.0.0.1', resolve))

const inbox = await fetchJson('/api/mobile/v1/inbox')
assert.equal(inbox.status, 200)
assert.equal(inbox.body.counts.running, 1)
assert.equal(inbox.body.questions.length, 1)
assert.equal(inbox.body.questions[0].callId, 'call-1')
assert.equal(inbox.body.questions[0].sessionTitle, '修复构建')
assert.equal(inbox.body.questions[0].options[1].description, '保留改动')
assert.equal(inbox.body.running[0].id, 's-run')

const tasks = await fetchJson('/api/mobile/v1/tasks')
assert.equal(tasks.body.tasks.length, 2)
assert.equal(tasks.body.tasks[0].title, '修复构建')

const detail = await fetchJson('/api/mobile/v1/tasks/s-run')
assert.equal(detail.body.task.id, 's-run')
assert.equal(detail.body.questions[0].questionId, 'q1')

const missing = await fetchJson('/api/mobile/v1/tasks/nope')
assert.equal(missing.status, 404)

const answer = await fetchJson('/api/mobile/v1/questions/s-run/call-1/answer', {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ answers: [{ id: 'q1', selected: ['继续'] }] }),
})
assert.equal(answer.status, 200)
assert.equal(answer.body.ok, true)
const answerCall = seen.find((entry) => entry.method === 'userQuestions/answer')
assert.equal(answerCall.args.agentId, 's-run')
assert.equal(answerCall.args.callId, 'call-1')
assert.deepEqual(answerCall.args.answer.answers, [{ id: 'q1', selected: ['继续'] }])

assert.ok(seen.every((entry) => entry.cookie === undefined || typeof entry.cookie === 'string'))

await new Promise((resolve) => handler.close(resolve))
await new Promise((resolve) => upstream.close(resolve))
console.log('mobile.test.js ok')
