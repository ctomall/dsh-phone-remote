/**
 * Standalone check for the self-update manager: git fast-forward upgrades,
 * dirty/diverged abort with rollback, feed signature verification and
 * signed-archive installs. Run with `node tests/updater.test.js`.
 */
import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { createHash, generateKeyPairSync, sign } from 'node:crypto'
import { mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { promisify } from 'node:util'
import {
  UpdateManager,
  canonicalFeedPayload,
  compareVersions,
  verifyFeedSignature,
} from '../lib/updater.js'

const execFileAsync = promisify(execFile)
const roots = []

function ok(label) {
  console.log('✓', label)
}

async function run(file, args, cwd) {
  const { stdout } = await execFileAsync(file, args, { cwd, timeout: 60_000, maxBuffer: 8 * 1024 * 1024 })
  return stdout.trim()
}

function git(cwd, args) {
  return run('git', ['-C', cwd, ...args], cwd)
}

function packageJson(version) {
  return `${JSON.stringify({ name: '@local/phone-remote', version, private: true }, null, 2)}\n`
}

async function commit(dir, message) {
  await git(dir, ['add', '-A'])
  await git(dir, ['-c', 'user.name=test', '-c', 'user.email=test@example.com', 'commit', '-q', '-m', message])
}

async function makeGitFixture() {
  const root = await mkdtemp(join(tmpdir(), 'pr-update-git-'))
  roots.push(root)
  const origin = join(root, 'origin.git')
  const seed = join(root, 'seed')
  const work = join(root, 'work')
  await run('git', ['init', '--bare', '-q', origin])
  await run('git', ['init', '-q', seed])
  await git(seed, ['symbolic-ref', 'HEAD', 'refs/heads/main'])
  await mkdir(join(seed, 'plugin'))
  await writeFile(join(seed, 'plugin', 'package.json'), packageJson('1.3.0'))
  await writeFile(join(seed, 'plugin', 'client.js'), '// marker\n')
  await commit(seed, 'release: 1.3.0')
  await git(seed, ['remote', 'add', 'origin', origin])
  await git(seed, ['push', '-q', 'origin', 'main'])
  await run('git', ['-C', origin, 'symbolic-ref', 'HEAD', 'refs/heads/main'])
  await run('git', ['clone', '-q', origin, work])
  return { root, origin, seed, work, installDir: join(work, 'plugin') }
}

async function publish(fixture, version, note) {
  await writeFile(join(fixture.seed, 'plugin', 'package.json'), packageJson(version))
  await writeFile(join(fixture.seed, 'plugin', 'client.js'), `// ${version}\n`)
  await commit(fixture.seed, note)
  await git(fixture.seed, ['push', '-q', 'origin', 'main'])
}

async function testGitUpgrade() {
  const fixture = await makeGitFixture()
  const manager = new UpdateManager({ currentVersion: '1.3.0', installDir: fixture.installDir, feedUrl: '' })

  assert.equal(await manager.resolveMode(), 'git', 'link-ish install upgrades over git')
  let status = await manager.check()
  assert.equal(status.mode, 'git')
  assert.equal(status.available, false)
  assert.equal(status.commitsBehind, 0)
  assert.equal(status.latestVersion, '1.3.0')
  ok('git: 识别 link 安装并以快进作为升级方式')

  await publish(fixture, '1.4.0', 'release: 1.4.0 git upgrade')
  status = await manager.check()
  assert.equal(status.available, true, 'a pushed commit is offered')
  assert.equal(status.commitsBehind, 1)
  assert.equal(status.latestVersion, '1.4.0')
  assert.equal(status.dirty, false)

  const result = await manager.apply()
  assert.equal(result.mode, 'git')
  assert.equal(result.installedVersion, '1.4.0')
  assert.equal(result.restartRequired, true)
  const installed = JSON.parse(await readFile(join(fixture.installDir, 'package.json'), 'utf8'))
  assert.equal(installed.version, '1.4.0', 'work tree really fast-forwarded')
  assert.equal(await git(fixture.work, ['rev-parse', 'HEAD']), result.commit)
  ok('git: 上游新提交 → 立即升级 → 工作区快进到 1.4.0')

  await assert.rejects(() => manager.apply(), /已是最新版本/, 'second apply is a no-op error')

  // A dirty plugin directory must abort before touching the work tree.
  await publish(fixture, '1.5.0', 'release: 1.5.0')
  const dirtyHead = await git(fixture.work, ['rev-parse', 'HEAD'])
  await writeFile(join(fixture.installDir, 'package.json'), `${packageJson('1.4.0')} `)
  status = await manager.check()
  assert.equal(status.dirty, true)
  await assert.rejects(() => manager.apply(), /未提交改动/)
  assert.equal(await git(fixture.work, ['rev-parse', 'HEAD']), dirtyHead, 'abort leaves HEAD alone')
  ok('git: 插件目录有未提交改动时中止，HEAD 不动')
  await git(fixture.work, ['reset', '--hard', 'HEAD'])

  // A diverged local commit makes --ff-only fail; the pull must roll back.
  await writeFile(join(fixture.installDir, 'client.js'), '// local change\n')
  await commit(fixture.work, 'local: unrelated commit')
  const divergedHead = await git(fixture.work, ['rev-parse', 'HEAD'])
  status = await manager.check()
  assert.equal(status.available, true)
  await assert.rejects(() => manager.apply(), /已回滚/)
  assert.equal(await git(fixture.work, ['rev-parse', 'HEAD']), divergedHead, 'failed merge rolled back')
  ok('git: 分叉导致 --ff-only 失败时自动回滚')
}

function makeSigningKeys() {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' })
  return {
    privateKey,
    publicKeyB64: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
  }
}

function signedFeed(fields, privateKey) {
  const feed = { ...fields }
  feed.signature = sign('sha256', Buffer.from(canonicalFeedPayload(feed), 'utf8'), {
    key: privateKey,
    dsaEncoding: 'der',
  }).toString('base64')
  return feed
}

async function buildZip(destination, version) {
  const tree = `${destination}-tree`
  await mkdir(join(tree, 'phone-remote'), { recursive: true })
  await writeFile(join(tree, 'phone-remote', 'package.json'), packageJson(version))
  await writeFile(join(tree, 'phone-remote', 'client.js'), `// ${version}\n`)
  await run('python3', [
    '-c',
    'import shutil, sys; shutil.make_archive(sys.argv[1], "zip", sys.argv[2])',
    destination.replace(/\.zip$/u, ''),
    tree,
  ])
  return readFile(`${destination}`)
}

async function testSignatures() {
  const { privateKey, publicKeyB64 } = makeSigningKeys()
  const feed = signedFeed(
    { version: '9.9.9', url: 'https://example.test/phone-remote-9.9.9.zip', sha256: 'AB'.repeat(32), notes: 'signed' },
    privateKey,
  )
  assert.equal(verifyFeedSignature(feed, publicKeyB64), true, 'valid signature verifies')
  assert.equal(verifyFeedSignature({ ...feed, notes: 'tampered' }, publicKeyB64), false, 'notes are covered')
  assert.equal(verifyFeedSignature({ ...feed, sha256: 'CD'.repeat(32) }, publicKeyB64), false, 'sha256 is covered')
  assert.equal(verifyFeedSignature(feed), false, 'the pinned key rejects a foreign key')
  assert.equal(verifyFeedSignature({ version: '1.0.0', url: 'https://x/y.zip' }, publicKeyB64), false, 'missing signature')
  assert.equal(compareVersions('1.10.0', '1.9.9'), 1)
  assert.equal(compareVersions('1.3.0', '1.3.0'), 0)
  assert.equal(compareVersions('1.2.9', '1.3.0'), -1)
  ok('签名: 合法签名通过，篡改 notes / sha256 / 缺失签名均被拒绝')
}

async function testArchiveUpgrade() {
  const root = await mkdtemp(join(tmpdir(), 'pr-update-zip-'))
  roots.push(root)
  const installDir = join(root, 'installed')
  await mkdir(installDir)
  await writeFile(join(installDir, 'package.json'), packageJson('1.3.0'))
  const zipPath = join(root, 'phone-remote-2.0.0.zip')
  const bytes = await buildZip(zipPath, '2.0.0')
  const sha256 = createHash('sha256').update(bytes).digest('hex')
  const { privateKey, publicKeyB64 } = makeSigningKeys()
  const feed = signedFeed(
    { version: '2.0.0', url: 'https://example.test/phone-remote-2.0.0.zip', sha256, notes: 'signed archive' },
    privateKey,
  )

  const feedJson = JSON.stringify(feed)
  const fetchImpl = async (url) => {
    const target = String(url)
    if (target === 'https://example.test/feed.json') return new Response(feedJson, { status: 200 })
    if (target === feed.url) return new Response(bytes, { status: 200 })
    return new Response('missing', { status: 404 })
  }
  const manager = new UpdateManager({
    currentVersion: '1.3.0',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyB64,
    mode: 'zip',
    fetchImpl,
  })

  const status = await manager.check()
  assert.equal(status.mode, 'zip')
  assert.equal(status.signed, true)
  assert.equal(status.available, true)
  assert.equal(status.latestVersion, '2.0.0')

  const result = await manager.apply()
  assert.equal(result.mode, 'zip')
  assert.equal(result.installedVersion, '2.0.0')
  assert.equal(result.restartRequired, true)
  const installed = JSON.parse(await readFile(join(installDir, 'package.json'), 'utf8'))
  assert.equal(installed.version, '2.0.0', 'archive really swapped in')
  await stat(result.backup)
  ok('更新包: 签名 + SHA-256 校验后替换目录并保留 .bak 备份')

  // Unsigned or tampered feeds must be rejected before any download.
  const unsigned = new UpdateManager({
    currentVersion: '1.3.0',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyB64,
    mode: 'zip',
    fetchImpl: async () => new Response(JSON.stringify({ ...feed, signature: '' }), { status: 200 }),
  })
  const unsignedStatus = await unsigned.check()
  assert.match(unsignedStatus.error, /缺少签名/)

  const tampered = new UpdateManager({
    currentVersion: '1.3.0',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyB64,
    mode: 'zip',
    fetchImpl: async () => new Response(JSON.stringify({ ...feed, notes: 'tampered' }), { status: 200 }),
  })
  const tamperedStatus = await tampered.check()
  assert.match(tamperedStatus.error, /签名无效/)

  // A wrong archive digest must abort the install.
  const badDigest = signedFeed(
    { version: '2.0.1', url: 'https://example.test/phone-remote-2.0.0.zip', sha256: '00'.repeat(32), notes: 'bad digest' },
    privateKey,
  )
  const digestManager = new UpdateManager({
    currentVersion: '2.0.0',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyB64,
    mode: 'zip',
    fetchImpl: async (url) => new Response(
      String(url) === 'https://example.test/feed.json' ? JSON.stringify(badDigest) : bytes,
      { status: 200 },
    ),
  })
  await assert.rejects(() => digestManager.apply(), /SHA-256 校验失败/)
  ok('更新包: 未签名 / 被篡改清单 / 错误摘要均在下载前后被拒绝')

  // Auto mode outside a work tree falls back to the archive strategy.
  const auto = new UpdateManager({ currentVersion: '2.0.0', installDir })
  assert.equal(await auto.resolveMode(), 'zip')
  ok('非 git 安装自动回退更新包模式')
}

await testSignatures()
await testGitUpgrade()
await testArchiveUpgrade()
await Promise.all(roots.map((root) => rm(root, { recursive: true, force: true })))
console.log('updater.test.js: all checks passed')
