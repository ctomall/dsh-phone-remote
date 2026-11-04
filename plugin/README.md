# 手机远程（phone-remote）

当前版本：**1.2.0**，新增同一局域网/VPN 的内网入口，以及无公网 IP 时的 Cloudflare 隧道入口。

给 DSH 增加**只允许已配对设备进入**的手机入口，支持公网 HTTPS、同一局域网/VPN 内的 HTTP，以及**没有公网 IP 时**通过 Cloudflare 隧道出站接入。电脑端生成一次性配对码，手机扫码后完成设备授权；之后手机（包括 Android / 鸿蒙 APP）就能使用完整的 DSH Web。

它不是把 DSH 的端口直接暴露出去，而是一个独立的小型反向代理网关：

```
Android / 鸿蒙 / 手机浏览器
   │ 公网 HTTPS        │ 内网 HTTP           │ 隧道 HTTPS（无公网 IP）
   ▼                   ▼                     ▼
nginx 443          10.0.4.7:8080      Cloudflare 边缘
   └──────────┬────────┴──────────┬──────────┘
              ▼                   ▲
     phone-remote 网关（本机监听）  │ cloudflared 出站连接
              │ 只代理已配对设备；Host/Origin 改写为回环
              ▼
     本机 DSH Web（10.0.4.7:3080 / 127.0.0.1:3080）
```

## 功能

- **一次性配对码**：8 位、5 分钟有效、单次使用；默认按 IP 限制尝试次数（10 分钟 15 次）。
- **设备级授权**：配对成功写入 `HttpOnly` 设备 cookie（`dsh_phone`），每个请求都在网关校验；撤销后下一次请求立即被拦下。
- **配对二维码**：设置页直接展示 SVG 二维码，扫码即可带出地址与配对码。
- **两种部署形态**：
  - 直连：网关监听 `0.0.0.0:8443`，自带自签名 HTTPS 证书（首次启动用 `openssl` 生成，按公网 IP 生成 SAN）。
  - 反代：网关只监听 `127.0.0.1`，由 nginx/Caddy 终止 TLS，通过 `/phone/` 前缀转发（本机默认采用这种）。
- **内网入口**：额外监听一个私有 IPv4 的 HTTP 端口（默认 `10.0.4.7:8080`），手机在同一局域网/VPN 时不需要域名、TLS 或公网出口即可使用；与公网入口共享设备清单和配对码。
- **无公网 IP 隧道**：通过 `cloudflared` 主动出站连接 Cloudflare，得到一个 `https://*.trycloudflare.com`（或自有域名）入口，不需要入站端口、端口映射或公网 IP；仍走设备配对。
- **多入口二维码**：设置页用「公网入口 / 内网入口 / 隧道入口」标签切换，分别生成二维码与链接。
- **设备清单与审计**：显示设备名、平台、最近 IP、最近活跃时间；支持逐台撤销。
- **可达性诊断**：从服务端探测每个入口的上游与入口地址，区分“服务没起来”和“防火墙没放行”。
- **Agent 工具**：`phone_remote`，支持 `status` / `pair` / `devices` / `revoke`；`pair` 可指定 `entry: public | lan | all`。
- **WebSocket 透传**：DSH 的 `/api/remote.mux` 长连接、SSE 流都能穿过网关。

## 安装

```ts
plugin_manager({
  action: 'install_bundle',
  target: '/home/ubuntu/workspace/phone-remote',
})
```

安装后刷新页面。**设置 → 手机远程** 会出现新的一页。

## 配置

`cordis.patch.yml` 中的 `config`（全部可选）：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启动网关 |
| `host` | `0.0.0.0` | 监听地址。反代模式请设 `127.0.0.1` |
| `port` | `8443` | 监听端口 |
| `tlsMode` | `self-signed` | `self-signed` / `files` / `off` |
| `certFile` / `keyFile` | 空 | `tlsMode: files` 时的 PEM 证书链与私钥 |
| `tlsHosts` | `[]` | 追加到自签名证书 SAN 的域名/IP |
| `publicUrl` | 空 | 手机实际打开的根地址；路径会作为挂载前缀，例如 `https://example.com/phone/` |
| `publicHost` | 空 | 用于生成二维码与证书 SAN 的公网 IP/域名；不填则自动探测 |
| `basePath` | 从 `publicUrl` 推导 | 挂载前缀，例如 `/phone` |
| `trustProxy` | `false` | 信任 `X-Forwarded-For`/`X-Forwarded-Proto`，仅在网关只监听回环时开启 |
| `pairingTtlSec` | `300` | 配对码有效期（60–900 秒） |
| `deviceMaxAgeDays` | `180` | 设备 cookie 有效期 |
| `lanEnabled` | `true` | 是否启动内网 HTTP 入口 |
| `lanHost` | 自动探测 | 内网监听地址/主机名；默认取第一个私有 IPv4（如 `10.0.4.7`） |
| `lanPort` | `8080` | 内网监听端口 |
| `lanBasePath` | `/phone` | 内网挂载前缀；`/phone` 对应 `http://<lanHost>:<lanPort>/phone/` |
| `lanPublicUrl` | 自动生成 | 覆盖内网二维码/链接的根地址，例如 `http://10.0.4.7:8080/phone/` |
| `lanLabel` | `内网入口` | 设置页和工具里显示的名称 |
| `tunnelEnabled` | `false` | 是否启动 Cloudflare 隧道入口 |
| `tunnelMode` | `quick` | `quick` 临时隧道 / `named` 命名隧道 |
| `tunnelName` | 空 | `named` 模式下的 Cloudflare 隧道名 |
| `tunnelUrl` | 空 | `named` 模式的固定公网地址，如 `https://dsh.example.com` |
| `cloudflaredPath` | `cloudflared` | `cloudflared` 可执行文件路径 |
| `tunnelProtocol` | `http2` | `http2` 或 `quic`；防火墙严格时用 http2 |
| `tunnelTarget` | 自动 | 隧道回源的本地地址，默认指向公网网关监听 |
| `tunnelStartTimeoutSec` | `45` | 等待公网 URL 的超时 |
| `tunnelAutoRestart` | `true` | 异常退出后自动重试 |
| `tunnelLabel` | `隧道入口` | 设置页和工具里显示的名称 |
| `dataDir` | `$DSH_HOME/phone-remote` | 设备清单与自签名证书目录 |
| `updateMode` | `auto` | 升级策略：`auto`（在 git 工作区内走快进，否则用更新包）/ `git` / `zip` |
| `updateFeedUrl` | 空 | 更新包模式的 HTTPS 签名清单地址 |
| `updatePublicKey` | 内置公钥 | 覆盖清单签名公钥（PEM 或裸 base64） |
| `updateRequireSignature` | `true` | 清单缺少签名时是否拒绝更新 |
| `updateGitRemote` | `origin` | Git 快进使用的远端名 |

### 直连模式

```yaml
- id: phone-remote
  config:
    host: 0.0.0.0
    port: 8443
    tlsMode: self-signed
    publicUrl: https://43.162.117.251:8443/
```

需要云防火墙/安全组放行 TCP 8443。首次启动会生成：

```
~/.dsh/phone-remote/tls/remote-cert.pem
~/.dsh/phone-remote/tls/remote-key.pem   (0600)
```

证书覆盖探测到的公网 IP、局域网 IP、`127.0.0.1` 与 `localhost`。公网 IP 变化时会自动重签。

### 反向代理模式（推荐，复用已有 443 证书）

网关只监听回环，nginx 终止 TLS：

```yaml
- id: phone-remote
  config:
    host: 127.0.0.1
    port: 8443
    tlsMode: off
    trustProxy: true
    publicUrl: https://harness.ila3.net/phone/
```

nginx（保留前缀转发，网关自行剥离 `/phone`）：

```nginx
location = /phone {
    return 301 /phone/;
}

location ^~ /phone/ {
    auth_basic off;
    proxy_pass http://127.0.0.1:8443;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
    proxy_buffering off;
    client_max_body_size 100m;
}
```

本机已把这段配置写入 `/etc/nginx/snippets/phone-remote.conf`，并在 `/etc/nginx/sites-available/dsh` 的 443 server 块中用 `include` 引入；已验证 `https://harness.ila3.net/phone/` 可用。原站点根路径仍由原来的 basic auth 保护。

### 内网入口（同一局域网 / VPN）

默认开启，自动绑定第一个私有 IPv4：

```text
http://10.0.4.7:8080/phone/
```

配置示例：

```yaml
- id: phone-remote
  config:
    enabled: true
    host: 127.0.0.1          # 公网入口仍由 nginx 转发
    port: 8443
    tlsMode: off
    trustProxy: true
    publicUrl: https://harness.ila3.net/phone/
    lanEnabled: true
    lanHost: 10.0.4.7        # 留空则自动探测
    lanPort: 8080
    lanBasePath: /phone
```

特点与限制：

- 内网入口是 **HTTP**，不校验证书，不需要 DNS，也不依赖公网出口；只应在可信局域网或 VPN 内使用。
- 仍然需要一次性配对码和设备 cookie；配对后设备会出现在同一个设备清单里。
- 不要把 `lanPort` 直接映射到公网，也不要把 `lanHost` 设为 `0.0.0.0` 除非你明确知道自己在做什么。
- 如果 `lanHost` 自动选错（Docker / VPN 网卡），显式配置正确的网卡地址。
- 内网端口被占用时，设置页的「内网入口」标签会显示未运行，网关其余入口不受影响。
- Android App 已允许 HTTP 明文，可直接扫描内网二维码；鸿蒙 App 的系统 WebView 是否允许 HTTP 取决于设备策略，优先使用公网 HTTPS。

### 没有公网 IP：Cloudflare 隧道

适用于只有内网地址、运营商 CGNAT、没有可开放端口的环境。`cloudflared` 从本机主动连出，Cloudflare 把公网请求转回本机网关，**不需要公网 IP，也不需要入站端口**。

安装 `cloudflared`：

```bash
curl -L -o /usr/local/bin/cloudflared \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
sudo chmod +x /usr/local/bin/cloudflared
cloudflared --version
```

快速隧道（无需 Cloudflare 账号，URL 每次启动随机）：

```yaml
- id: phone-remote
  config:
    tunnelEnabled: true
    tunnelMode: quick
    cloudflaredPath: /usr/local/bin/cloudflared
```

启动后设置页出现「隧道入口」标签，地址形如：

```text
https://random-words.trycloudflare.com/phone/
```

注意事项：

- 只出站访问 Cloudflare（默认 `http2` 走 TCP 443）；不需要改云安全组入站规则。
- quick 隧道 URL 每次重启都会变化；URL 变化后旧设备需要重新配对。
- 隧道回源的是 **phone-remote 网关**，不是 DSH 的 3080；因此设备配对仍然生效。
- 临时/个人使用足够；生产建议使用命名隧道 + 自有域名。
- 流量会经过 Cloudflare，敏感部署需自行评估。

命名隧道（固定域名）：

```yaml
tunnelEnabled: true
tunnelMode: named
tunnelName: dsh-phone
tunnelUrl: https://dsh.example.com
```

需要先完成 `cloudflared tunnel login`、创建隧道并把域名 DNS 指到该隧道；`cloudflared` 以 `run <name>` 方式启动，`tunnelUrl` 用于生成二维码。

## 使用

1. 电脑端打开 **设置 → 手机远程**。
2. 选择「公网入口」「内网入口」或「隧道入口」标签，点「生成配对码」，得到对应二维码和 8 位配对码。
3. 手机相机扫码，或打开配对链接输入配对码。
4. 配对成功后自动跳到 DSH 首页；设备出现在「已配对设备」里。
5. 换手机或怀疑泄露时，在设备清单点「撤销」。

各入口是独立 host，设备 cookie 按 host 隔离；在公网配对的手机若要改用内网或隧道地址，需要在对应入口重新配对一次。

### 鸿蒙 APP

配套工程在 [`../dsh-mobile-harmony`](../dsh-mobile-harmony/README.md) 与 [`../dsh-mobile-android`](../dsh-mobile-android/README.md)：扫码或手填地址 + 配对码，App 打开 `/pair?code=…&auto=1`，网关自动完成配对与登录，随后进入 WebView。

Android App 可以直接扫描内网入口或隧道入口的二维码；隧道入口是 HTTPS，无需额外配置。鸿蒙 App 若系统限制内网明文 HTTP，请使用公网或隧道入口。

## HTTP 接口

浏览器端（需要 DSH 登录 cookie，由 `ctx.connection.fetch` 保护）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/phone-remote/status` | 入口状态、地址、证书、设备、告警 |
| POST | `/api/phone-remote/pairing` | 生成一次性配对码与二维码；body `{ entry: "public" \| "lan" \| "all" }` |
| GET | `/api/phone-remote/devices` | 设备清单 |
| POST | `/api/phone-remote/devices/revoke` | 撤销设备，body `{ id }` |
| POST | `/api/phone-remote/diagnose` | 每个入口的上游/入口可达性探测 |

网关公开路径（无需设备 cookie）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `{basePath}/healthz` | 健康检查，不泄露设备信息 |
| GET | `{basePath}/pair` | 配对页；`code` 预填，`auto=1` 自动提交 |
| POST | `{basePath}/pair` | 校验配对码、写设备 cookie、303 到 DSH 登录 |

其余所有路径（包括 `/api` 与 WebSocket）都必须携带有效设备 cookie。

## 安全模型

- **配对码**：32 字符表随机 8 位（约 1.1×10¹² 组合），5 分钟 TTL，单次消费，按 IP 限速；POST 拒绝明确的外站 `Origin`，但接受 WebView 在 `Referrer-Policy` 下发出的 `Origin: null`。
- **设备 token**：32 字节随机，cookie 值形如 `<deviceId>.<secret>`，服务端只存 SHA-256；比较使用 `timingSafeEqual`。
- **cookie**：`HttpOnly`、`SameSite=Lax`、`Path=/`；HTTPS 入口带 `Secure`。
- **上游隔离**：网关把 `Host` 与 `Origin` 改写为 `127.0.0.1:<DSH 端口>`，DSH 的浏览器信任栅栏只看到回环；公网地址无需写进 `--trusted-host`。
- **撤销**：设备记录被标记后，网关对每个请求重新校验，撤销立即生效；网关不缓存授权结果。
- **审计**：记录设备名、平台、最近 IP、最近活跃时间；不记录对话内容。
- **最小暴露面**：`/healthz` 与 `/pair` 是仅有的匿名路径；配对页 CSP 默认 `none`，只允许内联样式/脚本、`data:` 图片与同源表单。
- **内网入口**：只绑定指定的私有 IPv4，使用 HTTP，不携带证书；仍强制配对。端口不应映射到公网，`lanHost` 不建议设为 `0.0.0.0`。
- **隧道入口**：回源到本机网关而不是 DSH；quick URL 是公开地址但必须配对；流量经 Cloudflare 转发。

### 已知限制

- DSH 的浏览器会话本身是进程级 token；撤销设备阻止的是**网关入口**，如果手机曾经把带 token 的 URL 存进历史，且能绕过网关直连 DSH，则仍可能访问。因此网关必须监听回环或独立端口，不要把 DSH 的 3080 直接暴露到公网。
- 自签名证书需要手机首次手动信任；鸿蒙 APP 会弹窗确认，浏览器会显示警告。生产使用建议用受信任证书。
- 直连模式需要放行端口；本机实测云防火墙只放行 22/80/443，8443 被拦截，所以本机采用 nginx `/phone/` 反代模式。
- 内网入口使用 HTTP，适合同一局域网或 VPN；手机不在该网络时不可达。公网、内网、隧道入口的 cookie 按 host 隔离，切换入口通常需要重新配对。
- quick 隧道 URL 是临时的，cloudflared 重启/断线重连后可能变化；需要稳定地址请用 named 隧道。

## 验证记录

本机已完成：

- 直连 `https://127.0.0.1:8443`：健康检查、配对页、配对 303、设备 cookie、反向代理、绝对重定向改写、WebSocket 101、伪造 cookie 401、撤销后 302。
- 前缀模式 `https://harness.ila3.net/phone/`：配对 → DSH token 跳转 → 首页 200 → 静态资源 200 → `/api/phone-remote/status` 200 → `/phone/api/remote.mux` WebSocket 101。
- 内网模式 `http://10.0.4.7:8080/phone/`：配对 303 → 首页 200 → 管理 API 200 → `/phone/api/remote.mux` WebSocket 101；设置页「公网/内网」标签与 LAN 二维码渲染通过。
- 隧道模式：`cloudflared` quick 隧道生成 `https://*.trycloudflare.com/phone/`，配对 303 → 首页 200 → API 200 → WebSocket 101。
- 单元/集成测试：`node tests/gateway.test.js`、`node tests/gateway-prefix.test.js`。
- 设置页在 JSDOM + React 18 中完成渲染测试：CSS 注入、状态卡片、配对码/二维码、撤销请求均通过。
- `phone_remote` 工具的 `pair` 生成配对码后，经网关兑换、登录、设备入库、撤销的完整链路通过；配对页 `name` 预填与 `auto=1` 自动提交通过。

## 故障排查

| 现象 | 检查 |
| --- | --- |
| 设置页显示「启动失败」 | 端口被占用 / 无权限；改 `port`，或 `ss -lntp \| grep 8443` |
| 手机打不开入口 | `curl https://<publicUrl>/healthz`；云安全组是否放行；`host` 是否为 `0.0.0.0` 或反代链路 |
| 配对页能开，配对后 401 | 反向代理是否保留 `/phone/` 前缀；`publicUrl` 与访问地址是否一致；DSH 是否在运行 |
| 页面能开但白屏 | 代理是否透传 WebSocket/SSE；浏览器控制台是否 `/api` 403；检查 `trustProxy` |
| 证书警告 | 自签名属预期；或在 nginx 配好证书后改 `tlsMode: off` |
| 撤销后仍能访问 | 确认访问的是网关地址；直接暴露的 DSH 端口不受网关撤销影响 |
| 站点配置被重写后 401 | 确认 443 server 块里仍有 `include /etc/nginx/snippets/phone-remote.conf;`，然后 `nginx -t && systemctl reload nginx` |
| 安卓/鸿蒙 App 提示「配对没有完成」，nginx 日志里 POST `/phone/pair` 为 403 | 旧版网关把 WebView 的 `Origin: null` 当成跨站请求。已修复：`sameOriginPost` 接受缺失/不透明 Origin，仍拒绝明确的外站 Origin。更新插件并重新加载后，**重新生成一个配对码**再扫。 |
| 配对码总是无效 | 配对码只存在内存中，插件重载/DSH 重启会清空；每次重载后必须重新生成。 |
| 内网入口打不开 | 手机与服务器是否在同一局域网/VPN；`ss -lntp \| grep 8080`；`curl http://<lanHost>:8080/phone/healthz`；`lanHost` 是否被 Docker/VPN 网卡误选 |
| 内网标签显示「未运行」 | `lanPort` 被占用或 `lanHost` 不是本机地址；看设置页告警或 `ss -lntp` |
| 内网 HTTP 被鸿蒙 WebView 拦截 | 使用公网 HTTPS 或隧道入口；Android 客户端已允许内网 HTTP |
| 隧道标签一直「启动中」 | `cloudflared --version` 是否存在；出站 TCP 443 是否可用；`tunnelStartTimeoutSec` 是否太短 |
| 隧道启动失败 / URL 为空 | 看设置页告警；quick 模式需要访问 `api.cloudflare.com` 与 Cloudflare 边缘；必要时把 `tunnelProtocol` 改为 `quic` 或 `http2` |
| 隧道 URL 变化后旧手机失效 | 重新生成隧道二维码并配对；或改用 named 隧道固定域名 |

## 开发

```
phone-remote/
├── index.js              # Host 入口：state、gateway、routes、tools
├── client.js             # 设置页
├── lib/state.js          # 设备清单持久化（0600）
├── lib/pairing.js        # 一次性配对码
├── lib/gateway.js        # HTTP(S) 监听、鉴权、配对、反代调度
├── lib/service.js        # 多入口协调与聚合状态
├── lib/tunnel.js         # Cloudflare 隧道进程、状态与入口适配
├── lib/proxy.js          # HTTP/WebSocket 代理与 Host/Origin 改写
├── lib/tls.js            # 自签名证书生成/校验
├── lib/pages.js          # 配对页（无外部资源）
├── lib/qr.js             # SVG 二维码
├── lib/routes.js         # 设置页 API
├── lib/tools.js          # phone_remote 工具
├── lib/vendor/qrcode.cjs # qrcode-generator (MIT)
└── tests/                # 独立网关集成测试
```

```bash
node tests/gateway.test.js
node tests/gateway-prefix.test.js
```


## 在线升级

设置页「在线升级」卡片按安装形态自动选择升级方式（`updateMode: auto`）：

| 安装形态 | 升级方式 | 行为 |
| --- | --- | --- |
| `link:` 到 git 克隆（源码安装） | **Git 快进** | 对插件目录执行 `git merge --ff-only <上游>`，随后做安装校验；失败自动 `git reset --hard` 回滚。工作区有未提交改动时中止。 |
| 打包安装（无 git 工作区） | **更新包** | 下载 HTTPS 清单里的 zip，校验 ECDSA 签名 + SHA-256、解压后做安装校验，再替换插件目录，旧目录保留为 `.bak-<时间戳>`。 |

两种方式都会把「替换了什么」写入插件目录旁的 `.phone-remote-update.json`，设置页的「回滚上一次更新」据此撤销：

- Git 安装：`git reset --hard <升级前提交>`；
- 更新包安装：把 `.bak-*` 备份目录换回来。

安装校验会检查 `package.json`、`index.js`、`client.js`、`cordis.patch.yml`、`lib/routes.js`、`lib/updater.js` 是否存在，并对其中 JS 跑 `node --check`；校验失败的更新包不会动现有目录。

所有升级/回滚都要**重启 DSH** 才生效：运行中的 Host 仍持有旧模块。

### 更新清单

```json
{
  "version": "1.3.2",
  "url": "https://…/phone-remote-1.3.2.zip",
  "sha256": "…",
  "notes": "…",
  "signature": "MEUCIQ…"
}
```

签名是 ECDSA P-256 / SHA-256 / DER，覆盖的字节（UTF-8，无尾随换行）为：

```text
version\nurl\nsha256(小写)\nnotes
```

内置公钥与 Android 客户端清单用的是同一把，可用 `updatePublicKey` 覆盖；`updateRequireSignature` 默认 `true`，清单缺少签名即拒绝。Git 快进模式不读取清单签名：代码来自 HTTPS 的 origin，由 git 自身的 TLS + 提交历史保证完整性。
