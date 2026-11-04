# DSH Native · HarmonyOS (NEXT)

Same product and design language as `android/` (tokens live in `../design/design-tokens.json`). This is a runnable project skeleton: pairing, session list, session follow stream, prompt, and the four tabs (sessions / inbox / workspace / settings).

## Requirements

- DevEco Studio 5.0+ (HarmonyOS NEXT, API 12)
- Signing: enable automatic signing under `File → Project Structure → Signing Configs`

> The Linux build host for this repo has no HarmonyOS SDK/hvigor, so this module is not compiled here yet — build it in DevEco.

## Layout

```
harmony/
  AppScope/app.json5                 bundleName = com.ila3.dshnative
  entry/src/main/ets/
    common/Theme.ets                 Ink tokens (mirrors Android)
    model/Models.ets                 Session / ChatMessage
    net/DshApi.ets                   pair, session/list, session/prompt, session/follow WebSocket
    pages/Index.ets                  four tabs
    pages/Chat.ets                   stream + composer
    pages/Pair.ets
    pages/Settings.ets
```

## Protocol

Identical to the Android client:

- Pair: `POST {base}pair` (form code/name/next), follow 3xx and keep the cookie
- List: `POST {base}api/session/list` with the client-request envelope
- Follow: `ws(s)://{host}api/remote.mux` + `{"type":"open",…,"endpoint":"session/follow",…}`

## Next

1. Approval/question inbox (parity with Android)
2. Attachments (`uploadFileBinary` + image compression)
3. Files preview and terminal (Web component fallback)
4. Push Kit notifications
5. Background keep-alive
