/**
 * Standalone check for the merged self-update manager: git fast-forward
 * upgrades and rollback, archive installs with tree validation and directory
 * rollback, plus feed signature verification. Run with
 * `node tests/updater.test.js`.
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
  verifyFeed,
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
  return `${JSON.stringify({ name: '@local/phone-remote', version, private: true, type: 'module' }, null, 2)}\n`
}

/** Files `validateInstall` requires, with syntax that passes `node --check`. */
const REQUIRED_FILES = {
  'index.js': 'export const entry = 1\n',
  'client.js': '// client marker\n',
  'cordis.patch.yml': '[]\n',
  'lib/routes.js': 'export const routes = []\n',
  'lib/updater.js': 'export const updater = 1\n',
}

async function writePluginTree(dir, version) {
  await mkdir(join(dir, 'lib'), { recursive: true })
  await writeFile(join(dir, 'package.json'), packageJson(version))
  for (const [rel, body] of Object.entries(REQUIRED_FILES)) {
    await writeFile(join(dir, rel), body)
  }
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
  await writePluginTree(join(seed, 'plugin'), '1.3.1')
  await commit(seed, 'release: 1.3.1')
  await git(seed, ['remote', 'add', 'origin', origin])
  await git(seed, ['push', '-q', 'origin', 'main'])
  await run('git', ['-C', origin, 'symbolic-ref', 'HEAD', 'refs/heads/main'])
  await run('git', ['clone', '-q', origin, work])
  return { root, origin, seed, work, installDir: join(work, 'plugin') }
}

async function publish(fixture, version, note) {
  await writePluginTree(join(fixture.seed, 'plugin'), version)
  await commit(fixture.seed, note)
  await git(fixture.seed, ['push', '-q', 'origin', 'main'])
}

function makeSigningKeys() {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' })
  return { privateKey, publicKeyPem: publicKey.export({ type: 'spki', format: 'pem' }) }
}

function signedFeed(fields, privateKey) {
  const feed = { ...fields }
  feed.signature = sign('sha256', Buffer.from(canonicalFeedPayload(feed), 'utf8'), {
    key: privateKey,
    dsaEncoding: 'der',
  }).toString('base64')
  return feed
}

async function buildZip(destination, version, treeFiles = REQUIRED_FILES) {
  const tree = `${destination}-tree`
  const pluginDir = join(tree, 'phone-remote')
  await mkdir(join(pluginDir, 'lib'), { recursive: true })
  await writeFile(join(pluginDir, 'package.json'), packageJson(version))
  for (const [rel, body] of Object.entries(treeFiles)) {
    await writeFile(join(pluginDir, rel), body)
  }
  await run('python3', [
    '-c',
    'import shutil, sys; shutil.make_archive(sys.argv[1], "zip", sys.argv[2])',
    destination.replace(/\.zip$/u, ''),
    tree,
  ])
  return readFile(destination)
}

async function testSignatures() {
  const { privateKey, publicKeyPem } = makeSigningKeys()
  const feed = signedFeed(
    { version: '9.9.9', url: 'https://example.test/phone-remote-9.9.9.zip', sha256: 'AB'.repeat(32), notes: 'signed' },
    privateKey,
  )
  assert.equal(verifyFeed(feed, feed.signature, publicKeyPem), true, 'valid signature verifies')
  assert.equal(verifyFeed({ ...feed, notes: 'tampered' }, feed.signature, publicKeyPem), false, 'notes are covered')
  assert.equal(verifyFeed({ ...feed, sha256: 'CD'.repeat(32) }, feed.signature, publicKeyPem), false, 'sha256 is covered')
  assert.equal(verifyFeed(feed, feed.signature), false, 'the pinned key rejects a foreign key')
  assert.equal(verifyFeed({ version: '1.0.0', url: 'https://x/y.zip' }, '', publicKeyPem), false, 'missing signature')
  assert.equal(compareVersions('1.10.0', '1.9.9'), 1)
  assert.equal(compareVersions('1.3.1', '1.3.1'), 0)
  assert.equal(compareVersions('1.2.9', '1.3.1'), -1)
  ok('签名: 合法签名通过，篡改 notes / sha256 / 外部公钥均被拒绝')
}

async function testGitUpgrade() {
  const fixture = await makeGitFixture()
  const manager = new UpdateManager({ currentVersion: '1.3.1', installDir: fixture.installDir, feedUrl: '' })

  assert.equal(await manager.resolveMode(), 'git', 'link-ish install upgrades over git')
  let status = await manager.check()
  assert.equal(status.mode, 'git')
  assert.equal(status.available, false)
  assert.equal(status.commitsBehind, 0)
  ok('git: 识别 link 安装并以快进作为升级方式')

  await publish(fixture, '1.3.2', 'release: 1.3.2 git upgrade')
  status = await manager.check()
  assert.equal(status.available, true, 'a pushed commit is offered')
  assert.equal(status.commitsBehind, 1)
  assert.equal(status.latestVersion, '1.3.2')

  const beforeHead = await git(fixture.work, ['rev-parse', 'HEAD'])
  const result = await manager.apply()
  assert.equal(result.mode, 'git')
  assert.equal(result.installedVersion, '1.3.2')
  assert.equal(result.restartRequired, true)
  const installed = JSON.parse(await readFile(join(fixture.installDir, 'package.json'), 'utf8'))
  assert.equal(installed.version, '1.3.2', 'work tree really fast-forwarded')
  await stat(join(fixture.work, '.phone-remote-update.json'))
  ok('git: 上游新提交 → 立即升级 → 快进并写入回滚记录')

  const rolledBack = await manager.rollback()
  assert.equal(rolledBack.mode, 'git')
  assert.equal(rolledBack.rolledBackTo, '1.3.1')
  assert.equal(await git(fixture.work, ['rev-parse', 'HEAD']), beforeHead, 'rollback resets to the previous commit')
  const restored = JSON.parse(await readFile(join(fixture.installDir, 'package.json'), 'utf8'))
  assert.equal(restored.version, '1.3.1')
  await assert.rejects(() => stat(join(fixture.work, '.phone-remote-update.json')), 'rollback clears the record')
  ok('git: 回滚重置到升级前的提交并清理记录')

  const dirtyHead = await git(fixture.work, ['rev-parse', 'HEAD'])
  await writeFile(join(fixture.installDir, 'client.js'), '// dirty\n')
  status = await manager.check()
  assert.equal(status.dirty, true)
  await assert.rejects(() => manager.apply(), /未提交改动/)
  assert.equal(await git(fixture.work, ['rev-parse', 'HEAD']), dirtyHead, 'abort leaves HEAD alone')
  await git(fixture.work, ['reset', '--hard', 'HEAD'])
  ok('git: 插件目录有未提交改动时中止，HEAD 不动')

  await publish(fixture, '1.3.3', 'release: 1.3.3')
  await writeFile(join(fixture.installDir, 'client.js'), '// local change\n')
  await commit(fixture.work, 'local: unrelated commit')
  const divergedHead = await git(fixture.work, ['rev-parse', 'HEAD'])
  await assert.rejects(() => manager.apply(), /已回滚/)
  assert.equal(await git(fixture.work, ['rev-parse', 'HEAD']), divergedHead, 'failed merge rolled back')
  ok('git: 分叉导致 --ff-only 失败时自动回滚')
}

async function testArchiveUpgrade() {
  const root = await mkdtemp(join(tmpdir(), 'pr-update-zip-'))
  roots.push(root)
  const installDir = join(root, 'installed')
  await writePluginTree(installDir, '1.3.1')
  const zipPath = join(root, 'phone-remote-2.0.0.zip')
  const bytes = await buildZip(zipPath, '2.0.0')
  const sha256 = createHash('sha256').update(bytes).digest('hex')
  const { privateKey, publicKeyPem } = makeSigningKeys()
  const feed = signedFeed(
    { version: '2.0.0', url: 'https://example.test/phone-remote-2.0.0.zip', sha256, notes: 'signed archive' },
    privateKey,
  )
  const feedJson = JSON.stringify(feed)

  const manager = new UpdateManager({
    currentVersion: '1.3.1',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyPem,
    mode: 'zip',
    fetchImpl: async (url) => {
      const target = String(url)
      if (target === 'https://example.test/feed.json') return new Response(feedJson, { status: 200 })
      if (target === feed.url) return new Response(bytes, { status: 200 })
      return new Response('missing', { status: 404 })
    },
  })

  const status = await manager.check()
  assert.equal(status.mode, 'zip')
  assert.equal(status.signed, true)
  assert.equal(status.available, true)

  const result = await manager.apply()
  assert.equal(result.mode, 'zip')
  assert.equal(result.installedVersion, '2.0.0')
  const installed = JSON.parse(await readFile(join(installDir, 'package.json'), 'utf8'))
  assert.equal(installed.version, '2.0.0', 'archive swapped in')
  await stat(result.backup)
  ok('更新包: 签名 + SHA-256 + 树校验后替换目录并保留备份')

  const rolledBack = await manager.rollback()
  assert.equal(rolledBack.rolledBackTo, '1.3.1')
  const restored = JSON.parse(await readFile(join(installDir, 'package.json'), 'utf8'))
  assert.equal(restored.version, '1.3.1', 'directory rollback restored the backup')
  ok('更新包: 回滚还原备份目录')

  const unsigned = new UpdateManager({
    currentVersion: '1.3.1',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyPem,
    mode: 'zip',
    fetchImpl: async () => new Response(JSON.stringify({ ...feed, signature: '' }), { status: 200 }),
  })
  assert.match((await unsigned.check()).error, /缺少签名/)

  const tampered = new UpdateManager({
    currentVersion: '1.3.1',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyPem,
    mode: 'zip',
    fetchImpl: async () => new Response(JSON.stringify({ ...feed, notes: 'tampered' }), { status: 200 }),
  })
  assert.match((await tampered.check()).error, /签名无效/)
  ok('更新包: 未签名 / 被篡改清单被拒绝')

  const brokenZip = await buildZip(join(root, 'broken.zip'), '2.0.0', { 'index.js': 'export const entry = 1\n' })
  const brokenFeed = signedFeed(
    { version: '2.0.0', url: 'https://example.test/broken.zip', sha256: createHash('sha256').update(brokenZip).digest('hex'), notes: 'broken tree' },
    privateKey,
  )
  const broken = new UpdateManager({
    currentVersion: '1.3.1',
    installDir,
    feedUrl: 'https://example.test/feed.json',
    publicKey: publicKeyPem,
    mode: 'zip',
    fetchImpl: async (url) => new Response(
      String(url) === 'https://example.test/feed.json' ? JSON.stringify(brokenFeed) : brokenZip,
      { status: 200 },
    ),
  })
  await assert.rejects(() => broken.apply(), /ENOENT|no such file/u)
  const untouched = JSON.parse(await readFile(join(installDir, 'package.json'), 'utf8'))
  assert.equal(untouched.version, '1.3.1', 'invalid archive never touches the install')
  ok('更新包: 缺文件的更新包被拒绝，安装目录不变')

  assert.equal(await new UpdateManager({ currentVersion: '1.3.1', installDir }).resolveMode(), 'zip')
  ok('非 git 安装自动回退更新包模式')
}

await testSignatures()
await testGitUpgrade()
await testArchiveUpgrade()
await Promise.all(roots.map((root) => rm(root, { recursive: true, force: true })))
console.log('updater.test.js: all checks passed')
