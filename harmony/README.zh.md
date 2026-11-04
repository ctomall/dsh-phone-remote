# DSH 原生 · HarmonyOS（NEXT）

与 `android/` 同一个产品、同一套设计语言（`design/design-tokens.json`），当前是**可运行的工程骨架**：配对、会话列表、会话流、发送，以及会话/收件箱/工作区/设置四 Tab。

## 环境

- DevEco Studio 5.0+（HarmonyOS NEXT，API 12）
- 签名：首次打开后在 `File → Project Structure → Signing Configs` 勾选自动签名

> 本仓库的 Linux 构建机上没有 HarmonyOS SDK / hvigor，所以这部分尚未编译验证；需要在 DevEco 里构建。

## 结构

```
harmony/
  AppScope/app.json5                 bundleName = com.ila3.dshnative
  entry/src/main/ets/
    common/Theme.ets                 Ink 设计令牌（与 Android 一致）
    model/Models.ets                 Session / ChatMessage
    net/DshApi.ets                   配对、session/list、session/prompt、session/follow WebSocket
    entryability/EntryAbility.ets
    pages/Index.ets                  四 Tab：会话 / 收件箱 / 工作区 / 设置
    pages/Chat.ets                   会话流 + 输入框（流式文本）
    pages/Pair.ets                   服务器地址 + 配对码
    pages/Settings.ets
```

## 协议

与 Android 完全一致：

- 配对：`POST {base}pair`（form: code/name/next），跟随 3xx 拿 Cookie
- 列表：`POST {base}api/session/list`，`{"type":"client-request","rpcId":…,"method":"session/list","payload":{"args":{"_request":{}}}}`
- 会话流：`ws(s)://{host}api/remote.mux`，发 `{"type":"open","streamId":…,"endpoint":"session/follow","payload":{"args":{"request":{"sessionId":…}}}}`

## 下一步

1. 审批/提问收件箱（对齐 Android 的 `pendingQuestions`）
2. 附件上传（`uploadFileBinary` + 图片压缩）
3. 文件预览与终端（Web 组件 fallback）
4. Push Kit 通知（对应 Android 的通知）
5. 长连接保活（后台任务 + 通知）
