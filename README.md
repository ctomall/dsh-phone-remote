# DSH Phone Remote

DSH 手机远程插件与原生 Android 客户端，带 Git 仓库在线升级。

## 目录

- `plugin/`：`@local/phone-remote` 插件，公网/内网/Cloudflare 隧道入口、设备配对、在线升级。
- `android/`：Kotlin + Compose 原生客户端，源码与 Gradle 工程。
- `updates/`：在线升级清单与安装包；客户端和插件都从这里检查更新。

## 安装插件

```bash
git clone https://github.com/ctomall/dsh-phone-remote.git
dsh plugin --profile web add ./dsh-phone-remote/plugin
```

## 在线升级

Android 客户端设置 → 检查更新，读取：

```text
https://raw.githubusercontent.com/ctomall/dsh-phone-remote/main/updates/android.json
```

插件设置页「在线升级」读取：

```text
https://raw.githubusercontent.com/ctomall/dsh-phone-remote/main/updates/phone-remote.json
```

清单使用 HTTPS + SHA-256 校验；插件更新后需重启 DSH 生效。
