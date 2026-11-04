# updates

- `android.json`：Android 客户端更新清单（ECDSA 签名，App「设置 → 检查更新」读取）。
- `phone-remote.json`：插件更新清单（插件详情页「在线升级」读取）。
- `publish-android.mjs`：发布脚本，用法见 `../android/README.md`。

## 0.19.1+ 手动安装一次（签名轮换）

0.19.1 更换了 APK 签名证书与更新清单密钥，**不能覆盖安装**，需要重装一次：

1. 下载：`https://raw.githubusercontent.com/ctomall/dsh-phone-remote/main/updates/DSHNative-0.19.1.apk`
2. 卸载旧版（会清掉服务器配置，重新配对即可）：
   `adb uninstall com.ila3.dshnative`，或手机设置里卸载。
3. 安装 APK，重新扫码配对。

装好 0.19.1 之后，**App 内自动更新恢复**：以后版本只要用同一套密钥发布，`android.json` 会正常提示更新。
当前最新为 **0.19.13**（任务详情产物区；插件 1.3.5 移动 BFF），0.19.1+ 用户可直接在 App 内更新。

## 密钥与备份

本轮轮换后的私钥/证书**不进仓库**，存放位置：

- 更新清单签名私钥：`~/.dsh/phone-remote/keys/manifest-signing-2026-11.pem`
- release keystore：`android/keystore/dshnative-release.jks` + `keystore.properties`，
  另有备份 `~/.dsh/phone-remote/keys/dshnative-release-2026-11.{jks,properties}`

**务必离线备份这几个文件**：丢失清单私钥 → 之后的更新清单无法签名；丢失 keystore → 之后的 APK 无法覆盖安装。
`android/keystore/` 已被 gitignore，不会随仓库分发。

## 发布新版本

```bash
cd android && ./gradlew assembleRelease
cd .. && ANDROID_MANIFEST_KEY=~/.dsh/phone-remote/keys/manifest-signing-2026-11.pem \
  node updates/publish-android.mjs android/app/build/outputs/apk/release/app-release.apk "更新说明"
git add updates/ && git commit -m "release: <版本>" && git push
```
