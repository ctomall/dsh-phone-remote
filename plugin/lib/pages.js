/**
 * Server-rendered pages for the public entry. The pairing page is deliberately
 * dependency-free: a phone opens it before it holds any credential, so it
 * carries inline CSS only, no scripts, no remote assets, and a CSP that allows
 * nothing else.
 *
 * @module @local/phone-remote/pages
 */

/**
 * Escape one string for HTML text or a double-quoted attribute.
 * @param {unknown} value - raw value.
 * @returns {string} escaped text.
 */
export function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

/** Shared response headers for pre-authentication pages. */
export const PAGE_HEADERS = {
  'content-type': 'text/html; charset=utf-8',
  'cache-control': 'no-store',
  'referrer-policy': 'same-origin',
  'x-content-type-options': 'nosniff',
  'x-frame-options': 'DENY',
  'content-security-policy':
    "default-src 'none'; style-src 'unsafe-inline'; img-src data:; script-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
}

/**
 * Render the pairing form.
 * @param {object} options - page state.
 * @param {string} [options.code] - prefilled pairing code.
 * @param {string} [options.name] - prefilled device name.
 * @param {string} [options.error] - error message.
 * @param {string} [options.next] - post-pairing path.
 * @param {string} [options.action] - form action URL.
 * @param {number} [options.ttlMinutes] - code lifetime in minutes.
 * @param {string} options.baseUrl - gateway root URL.
 * @param {string} [options.fingerprint] - certificate fingerprint.
 * @param {boolean} [options.secure] - whether the page is served over HTTPS.
 * @param {boolean} [options.auto] - submit the form automatically when a code is prefilled.
 * @returns {string} complete HTML document.
 */
export function renderPairPage(options = {}) {
  const code = escapeHtml(options.code ?? '')
  const name = escapeHtml(options.name ?? '')
  const error = typeof options.error === 'string' && options.error.length > 0 ? escapeHtml(options.error) : ''
  const next = escapeHtml(options.next ?? '')
  const action = escapeHtml(options.action ?? '/pair')
  const ttl = Number.isFinite(options.ttlMinutes) ? options.ttlMinutes : 5
  const baseUrl = escapeHtml(options.baseUrl ?? '')
  const fingerprint = escapeHtml(options.fingerprint ?? '')
  const secure = options.secure === true
  const auto = options.auto === true && code.length > 0
  const shortFingerprint = fingerprint.length > 24 ? `${fingerprint.slice(0, 24)}…` : fingerprint

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="dark">
<meta name="theme-color" content="#0b1220">
<title>连接 DSH · 手机远程</title>
<style>
  :root {
    --bg: #070d18;
    --bg-2: #0c1728;
    --card: #101c30;
    --card-2: #122238;
    --line: rgba(148, 197, 255, 0.14);
    --text: #e8f1ff;
    --muted: #8ea6c4;
    --accent: #2dd4bf;
    --accent-2: #38bdf8;
    --danger: #fca5a5;
    --danger-bg: rgba(248, 113, 113, 0.12);
  }
  * { box-sizing: border-box; }
  html, body { height: 100%; }
  body {
    margin: 0;
    min-height: 100dvh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: calc(20px + env(safe-area-inset-top)) 20px calc(20px + env(safe-area-inset-bottom));
    background:
      radial-gradient(1100px 620px at 12% -12%, rgba(45, 212, 191, 0.18), transparent 60%),
      radial-gradient(900px 560px at 108% 10%, rgba(56, 189, 248, 0.16), transparent 58%),
      linear-gradient(160deg, var(--bg) 0%, var(--bg-2) 100%);
    color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
    -webkit-font-smoothing: antialiased;
  }
  .wrap { width: 100%; max-width: 400px; }
  .brand { display: flex; align-items: center; gap: 10px; margin: 0 2px 18px; }
  .mark {
    display: grid; place-items: center;
    width: 40px; height: 40px; border-radius: 12px;
    background: linear-gradient(145deg, rgba(45, 212, 191, 0.18), rgba(56, 189, 248, 0.12));
    border: 1px solid rgba(94, 234, 212, 0.28);
    box-shadow: 0 0 24px rgba(45, 212, 191, 0.12);
  }
  .brand-title { font-size: 15px; font-weight: 650; letter-spacing: 0.02em; }
  .brand-sub { font-size: 11px; color: var(--muted); letter-spacing: 0.12em; text-transform: uppercase; }
  .card {
    padding: 24px 20px 20px;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: linear-gradient(160deg, rgba(18, 34, 56, 0.94), rgba(12, 23, 39, 0.94));
    box-shadow: 0 24px 70px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.04);
    backdrop-filter: blur(8px);
  }
  h1 { margin: 0 0 6px; font-size: 22px; font-weight: 650; letter-spacing: -0.01em; }
  .lead { margin: 0 0 20px; color: var(--muted); font-size: 13px; line-height: 1.6; }
  .error {
    margin: 0 0 16px; padding: 10px 12px;
    border: 1px solid rgba(248, 113, 113, 0.32); border-radius: 10px;
    background: var(--danger-bg); color: var(--danger); font-size: 13px; line-height: 1.45;
  }
  label { display: block; margin-bottom: 7px; color: #b9cde5; font-size: 12px; letter-spacing: 0.04em; }
  .field { margin-bottom: 16px; }
  input {
    width: 100%; appearance: none; border: 1px solid var(--line); border-radius: 12px;
    background: rgba(5, 12, 22, 0.72); color: var(--text); outline: none;
    font: inherit; transition: border-color 0.16s ease, box-shadow 0.16s ease;
  }
  input:focus { border-color: rgba(45, 212, 191, 0.65); box-shadow: 0 0 0 3px rgba(45, 212, 191, 0.13); }
  #code {
    padding: 15px 10px; text-align: center;
    font-family: ui-monospace, SFMono-Regular, "JetBrains Mono", Menlo, monospace;
    font-size: 25px; font-weight: 600; letter-spacing: 0.3em; text-indent: 0.3em;
    text-transform: uppercase;
  }
  #name { padding: 12px 13px; font-size: 14px; }
  button {
    width: 100%; margin-top: 4px; padding: 14px 16px; border: 0; border-radius: 12px;
    background: linear-gradient(135deg, #2dd4bf, #38bdf8);
    color: #04212a; font: inherit; font-size: 15px; font-weight: 700; letter-spacing: 0.02em;
    cursor: pointer; box-shadow: 0 10px 26px rgba(45, 212, 191, 0.22);
  }
  button:active { transform: translateY(1px); }
  .hint { margin: 16px 0 0; color: #7f98b8; font-size: 11.5px; line-height: 1.65; }
  .hint strong { color: #a9c2dd; font-weight: 600; }
  .meta {
    margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--line);
    display: flex; flex-direction: column; gap: 5px;
    color: #6f88a7; font-size: 10.5px; line-height: 1.5; word-break: break-all;
  }
  .meta b { color: #93aac6; font-weight: 600; }
  .foot { margin: 16px 2px 0; color: #5f7796; font-size: 10.5px; line-height: 1.6; text-align: center; }
  .badge { display: inline-flex; align-items: center; gap: 5px; padding: 3px 8px; border-radius: 999px; border: 1px solid var(--line); color: var(--muted); font-size: 10px; }
  .dot { width: 5px; height: 5px; border-radius: 50%; background: var(--accent); }
</style>
</head>
<body>
  <div class="wrap">
    <div class="brand">
      <div class="mark" aria-hidden="true">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
          <rect x="6.5" y="2.5" width="11" height="19" rx="3.2" stroke="#5eead4" stroke-width="1.5"/>
          <path d="M10 5.4h4" stroke="#5eead4" stroke-width="1.5" stroke-linecap="round"/>
          <path d="M9.5 10.5h5v4h-5z" stroke="#38bdf8" stroke-width="1.3"/>
          <path d="M10.6 10.5v-1a1.4 1.4 0 0 1 2.8 0v1" stroke="#38bdf8" stroke-width="1.3"/>
        </svg>
      </div>
      <div>
        <div class="brand-title">手机远程</div>
        <div class="brand-sub">DSH Remote</div>
      </div>
    </div>
    <main class="card">
      <h1>连接 DSH</h1>
      <p class="lead">输入电脑端「设置 → 手机远程」生成的配对码。配对码 <strong>${ttl} 分钟</strong>内有效，且只能使用一次。</p>
      ${error.length > 0 ? `<div class="error" role="alert">${error}</div>` : ''}
      <form method="post" action="${action}" autocomplete="off">
        <input type="hidden" name="next" value="${next}">
        <div class="field">
          <label for="code">配对码</label>
          <input id="code" name="code" value="${code}" placeholder="XXXX-XXXX" inputmode="text"
                 autocapitalize="characters" autocorrect="off" spellcheck="false" maxlength="12"
                 autocomplete="one-time-code" ${code.length === 0 ? 'autofocus' : ''} required>
        </div>
        <div class="field">
          <label for="name">设备名称</label>
          <input id="name" name="name" value="${name}" placeholder="我的手机" maxlength="40" autocomplete="off">
        </div>
        <button type="submit">连接</button>
      </form>
      <p class="hint">在电脑上打开 <strong>设置 → 手机远程</strong>，点击「生成配对码」，二维码或 8 位配对码会显示在那里。</p>
      <div class="meta">
        <span><b>入口</b> ${baseUrl}</span>
        ${secure ? `<span><b>HTTPS</b> 已启用${shortFingerprint.length > 0 ? ` · 证书 ${shortFingerprint}` : ''}</span>` : '<span><b>警告</b> 未启用 HTTPS，请仅在可信网络中继续</span>'}
        <span><b>设备</b> 配对后此设备会出现在电脑端的设备清单中，可随时撤销</span>
      </div>
    </main>
    <p class="foot">只把配对码交给自己的设备。任何人拿到配对码，都能在有效期内接入这台 DSH。</p>
  </div>
  ${auto ? `<script>
    window.addEventListener('load', function () {
      setTimeout(function () {
        var form = document.querySelector('form');
        if (form) form.submit();
      }, 250);
    });
  </script>` : ''}
</body>
</html>`
}
