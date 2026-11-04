# updates

- `android.json`：Android 客户端更新清单（ECDSA 签名，App「设置 → 检查更新」读取）。
- `phone-remote.json`：插件更新清单（插件详情页「在线升级」读取）。
- `publish-android.mjs`：发布脚本，用法见 `../android/README.md`。

## 0.19.0 手动安装（签名变更）

0.19.0 是「重新安装版」：用当前可用的签名构建，和 0.18.0 的正式证书不同，**不能覆盖安装**。

1. 下载：`https://raw.githubusercontent.com/ctomall/dsh-phone-remote/main/updates/DSHNative-0.19.0.apk`
2. 卸载旧版（会清掉本机服务器配置，重新配对即可）：
   `adb uninstall com.ila3.dshnative`，或手机设置里卸载。
3. 安装 APK，重新扫码配对。

## 关于自动更新

`android.json` 目前仍是 0.18.0（由正式证书 + 原始清单私钥签名的有效清单）。
0.19.0 的清单没有对应私钥，因此**没有**替换 `android.json`：

- 0.18.0 老用户：检查更新仍显示「已是最新」，不会拿到 0.19.0；
- 0.19.0 新用户：同样不会被推送到旧版本，手动安装即可。

恢复自动更新有两条路：

1. 提供原始清单私钥（对应客户端内置公钥），`publish-android.mjs` 可直接签 0.19.x 清单；
2. 或生成新密钥并在 App 里重新固定公钥：0.19.0 之后可以自动更新，但 0.18.x → 0.19.0 仍要手动重装一次。
