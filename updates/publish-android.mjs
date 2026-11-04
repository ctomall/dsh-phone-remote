#!/usr/bin/env node
/**
 * Publish a signed Android APK to the Git update feed.
 *
 * The Android client verifies `updates/android.json` with a pinned ECDSA P-256
 * public key, so the manifest must be signed with the matching private key.
 * The APK itself must be signed with the same release keystore every previous
 * version used, otherwise installed clients cannot apply the update.
 *
 * Usage:
 *   ANDROID_MANIFEST_KEY=/path/to/ec-private.pem \
 *     node updates/publish-android.mjs <app-release.apk> "更新说明"
 */

import { createHash, createPrivateKey, createPublicKey, sign, verify } from 'node:crypto'
import { copyFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const RAW_BASE = 'https://raw.githubusercontent.com/ctomall/dsh-phone-remote/main/updates'
const FEED = join(ROOT, 'updates', 'android.json')
const PINNED_PUBLIC_KEY =
  'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzcsZnRGxY3+4KdnLgCH9cHu/joD7VKxpaZfI45fDh4YSY3IBepXf4QHKKXZRP+UtI3YX5Mvs/sLjpDLKiVY3Cw=='

const fail = (message) => {
  console.error(`error: ${message}`)
  process.exit(1)
}

const [apkArg, notesArg] = process.argv.slice(2)
if (!apkArg) {
  fail('用法: ANDROID_MANIFEST_KEY=<ec-private.pem> node updates/publish-android.mjs <apk> "<更新说明>"')
}
const apkPath = resolve(apkArg)
if (!existsSync(apkPath)) fail(`APK 不存在：${apkPath}`)
const keyPath = process.env.ANDROID_MANIFEST_KEY
if (!keyPath || !existsSync(keyPath)) fail('未设置 ANDROID_MANIFEST_KEY（清单签名私钥 PEM 路径）')

const sdkRoot = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || join(process.env.HOME ?? '', 'Android/Sdk')
const buildTools = join(sdkRoot, 'build-tools')
const findTool = (name) => {
  if (!existsSync(buildTools)) return null
  const versions = execFileSync('ls', [buildTools], { encoding: 'utf8' }).trim().split('\n').filter(Boolean).sort()
  for (const version of versions.reverse()) {
    const candidate = join(buildTools, version, name)
    if (existsSync(candidate)) return candidate
  }
  return null
}
const apksigner = findTool('apksigner')
const aapt2 = findTool('aapt2')
if (!apksigner) fail(`找不到 apksigner（看 ${buildTools}），请设置 ANDROID_HOME`)
if (!aapt2) fail(`找不到 aapt2（看 ${buildTools}），请设置 ANDROID_HOME`)

const certs = execFileSync(apksigner, ['verify', '--print-certs', apkPath], { encoding: 'utf8' })
if (/CN=Android Debug|androiddebugkey/i.test(certs)) {
  fail('这个 APK 是 debug 签名，已拒绝发布：老用户会因签名不一致无法覆盖安装。请用 release keystore 重新构建。')
}
const badging = execFileSync(aapt2, ['dump', 'badging', apkPath], { encoding: 'utf8' })
const versionCode = Number(badging.match(/versionCode='(\d+)'/)?.[1] ?? 0)
const versionName = badging.match(/versionName='([^']+)'/)?.[1] ?? ''
if (!versionCode || !versionName) fail('无法从 APK 读取 versionCode/versionName')

const apkName = `DSHNative-${versionName}.apk`
const dest = join(ROOT, 'updates', apkName)
copyFileSync(apkPath, dest)
const bytes = readFileSync(dest)
const sha256 = createHash('sha256').update(bytes).digest('hex')
const apkUrl = `${RAW_BASE}/${apkName}`
const notes = (notesArg ?? '').trim() || `DSH 原生 ${versionName}`

const canonical = [versionCode, versionName, apkUrl, sha256, notes].join('\n')
const privateKey = createPrivateKey(readFileSync(keyPath))
const signature = sign('sha256', Buffer.from(canonical, 'utf8'), { key: privateKey, dsaEncoding: 'der' }).toString('base64')
const signatureOk = verify(
  'sha256',
  Buffer.from(canonical, 'utf8'),
  createPublicKey(privateKey),
  Buffer.from(signature, 'base64'),
)
if (!signatureOk) fail('清单签名自检失败')
const derivedPublic = createPublicKey(privateKey).export({ type: 'spki', format: 'der' }).toString('base64')
const matchesPinned = derivedPublic === PINNED_PUBLIC_KEY
if (!matchesPinned) {
  console.warn('warning: 这把私钥不是客户端内置的那把公钥对应的私钥，现有客户端会拒绝这份清单。')
}

const feed = { versionCode, versionName, apkUrl, sha256, notes, signature }
writeFileSync(FEED, `${JSON.stringify(feed, null, 2)}\n`)

console.log(`APK     : updates/${apkName} (${bytes.length} bytes)`)
console.log(`sha256  : ${sha256}`)
console.log(`manifest: updates/android.json (v${versionName} / ${versionCode})`)
console.log(`key     : ${matchesPinned ? '与客户端内置公钥匹配 ✓' : '与内置公钥不匹配 ✗'}`)
console.log(`签名串  : ${JSON.stringify(canonical)}`)
console.log('')
console.log('下一步：')
console.log(`  git add updates/${apkName} updates/android.json`)
console.log(`  git commit -m "release: ${versionName}" && git push origin main`)
