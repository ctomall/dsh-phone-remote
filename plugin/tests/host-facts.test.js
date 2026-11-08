/**
 * ⑥ 主机事实（磁盘 + 进程）的形状/边界测试。
 * 用注入的 statfs / run 跑，不需要真实 ps，也不碰线上进程。
 * 运行：node tests/host-facts.test.js
 */
import assert from 'node:assert/strict'
import { collectHostFacts } from '../lib/mobile.js'

const fakeStatfs = async (path) => {
  if (path === '/proc-unreadable') throw new Error('EACCES')
  // 100GB 总，40GB 可用 → 已用 60%
  return { bsize: 4096, blocks: 26214400, bavail: 10485760 }
}

const fakePs = [
  '  101  42.5  3.1 204800 node',
  '  102  12.0  1.5  51200 chrome',
  '  103   5.5  0.5  10240 bash',
  '  104   1.0  0.1   2048 ps',
  '  105   0.4  0.1   1024 sshd',
  '  106   0.3  0.1   1024 systemd',
  '  107   0.2  0.1   1024 cron',
  '  108   0.1  0.1   1024 dbus',
  '  109   0.1  0.1   1024 helper',
  '  110   0.1  0.1   1024 trimmed-by-top8',
].join('\n')

{
  const facts = await collectHostFacts({
    statfs: fakeStatfs,
    run: async () => fakePs,
  })

  assert.equal(typeof facts.host, 'string')
  assert.equal(facts.cpus > 0, true, 'cpus should be positive')
  assert.equal(facts.disk.length >= 1, true, 'disk should include at least "/"')
  const root = facts.disk[0]
  assert.equal(root.path, '/')
  assert.equal(root.totalMb, 102400, '100GB -> 102400MB')
  assert.equal(root.freeMb, 40960, '40GB -> 40960MB')
  assert.equal(root.usedPct, 60, 'used = 60%')

  assert.equal(facts.processes.length, 8, 'top 8 only')
  assert.deepEqual(Object.keys(facts.processes[0]).sort(), ['cpuPct', 'memPct', 'name', 'pid', 'rssMb'])
  assert.equal(facts.processes[0].pid, 101)
  assert.equal(facts.processes[0].cpuPct, 42.5)
  assert.equal(facts.processes[0].rssMb, 200, '204800KB -> 200MB')
  assert.equal(facts.processes.some((p) => p.name.includes('trimmed-by-top8')), false,
    '第 9 条之后应被 top8 截断')
  assert.equal(facts.processes.some((p) => p.name === 'ps'), false, 'ps 自身不应占位')
}

{
  // 磁盘读不到 + ps 失败：整体仍然返回，磁盘/进程为空数组而不是抛错
  const facts = await collectHostFacts({
    statfs: async () => { throw new Error('EACCES') },
    run: async () => { throw new Error('ps not found') },
  })
  assert.deepEqual(facts.disk, [])
  assert.deepEqual(facts.processes, [])
  assert.equal(typeof facts.load1, 'number')
}

console.log('host-facts: 2 组用例通过（磁盘 60%、进程 top8、失败降级为空数组）')
