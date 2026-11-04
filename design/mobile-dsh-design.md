# DSH 完整手机端设计（v1 草案）

## 1. 问题

现有 App 是「会话 + 聊天」的视觉升级版：四个 Tab 里只有会话列表是完整的，审批、产物、自动化没有一等对象。要做完整手机端，先换产品模型，再定信息架构，最后才是页面。

## 2. 产品模型

```
Task（一次运行）
 ├─ goal / todos / plan
 ├─ timeline（工具调用、步骤、耗时）
 ├─ Approval / Question（需要人）
 ├─ Artifact（文件、diff、报告、截图）
 └─ result（完成 / 失败 / 中断）

Inbox = 所有 Task 中「需要你」的事件聚合
Workspace = 文件与产物的组织方式（按目录 / 任务）
Automation = 定时运行的 Task 模板
```

**会话只是 Task 的一种视图**，聊天只是 Task 的一种交互；手机端首页以「需要你」为第一优先级。

## 3. 信息架构

底部四 Tab：

| Tab | 内容 | 首要动作 |
| --- | --- | --- |
| 收件箱 | 审批、提问、失败、完成 | 批准 / 拒绝 / 回答 / 查看 |
| 任务 | 运行中、待处理、已完成；任务详情 | 停止、引导、看产物 |
| 工作区 | 目录、文件、diff、产物 | 预览、下载、分享 |
| 设置 | 连接、模型、通知、自动化、关于 | 配置 |

任务详情为全屏页：目标、todo、审批条、时间线、产物、底部操作栏（停止 / 输入引导）。

## 4. 页面清单

**P0**
- 收件箱（审批 / 提问 / 失败 / 完成四类卡片）
- 任务列表（运行中优先）+ 任务详情
- 会话流（现有聊天页，作为任务的一种视图）
- 通知动作：批准 / 拒绝 / 回答 / 打开

**P1**
- 工作区（目录树、文件预览、diff）
- 全局搜索（任务 / 会话 / 文件）
- 产物中心（按任务聚合）

**P2**
- 自动化列表与运行记录
- 模型 / 权限 / 用量
- 专家与技能快捷调用

**P3**
- 子代理 / 团队树
- 离线缓存、多端接力、服务端推送

## 5. 关键交互

- **审批**：收件箱与通知都能直接批准/拒绝；高风险操作二次确认；审计记录（谁、何时、哪台设备）。
- **提问**：单选直接点选项回传；多选/多问题进入详情；回答后卡片自动消失。
- **任务详情**：目标与 todo 常驻；时间线默认折叠为最近 5 条；审批条固定在上方；产物可横向滑动。
- **失败**：错误摘要 + 一键重试 / 查看日志 / 让 Agent 修复。
- **工作区**：按 cwd 分组；文件只读预览；产物可分享。

## 6. Mobile BFF 接口草案

在插件内提供稳定接口，客户端不再解析原始事件：

```
GET  /phone/api/mobile/v1/inbox                 -> Approval/Question/Failure/Done 列表
GET  /phone/api/mobile/v1/tasks?state=running   -> TaskSummary[]
GET  /phone/api/mobile/v1/tasks/:id             -> TaskDetail（goal/todos/timeline/artifacts）
POST /phone/api/mobile/v1/tasks/:id/prompt      -> 发送引导
POST /phone/api/mobile/v1/tasks/:id/stop        -> 停止
POST /phone/api/mobile/v1/approvals/:id         -> {decision: approve|deny, remember: bool}
POST /phone/api/mobile/v1/questions/:id/answer  -> {answers: [{id, selected[], custom}]}
GET  /phone/api/mobile/v1/workspaces            -> 目录与最近产物
GET  /phone/api/mobile/v1/artifacts/:id         -> 下载/预览
GET  /phone/api/mobile/v1/search?q=             -> 任务/会话/文件
WS   /phone/api/remote.mux                      -> 增量事件（task/approval/artifact）
```

## 7. 数据映射

| 移动端对象 | DSH 来源 |
| --- | --- |
| Task | session + turn + running 状态 |
| Approval | 工具授权 / 计划确认事件 |
| Question | `ask_user_question` |
| Todo | `todo_write` 参数 |
| Artifact | 工具产物（文件写入、diff、截图） |
| Workspace | session.cwd |

## 8. 分阶段与验收

- **P0**：收件箱能完成批准/拒绝/回答；任务详情能看到目标、todo、时间线、产物；通知可动作。验收：断网重连不丢审批；回答后任务继续。
- **P1**：工作区可预览文件与 diff；搜索能命中三类对象。
- **P2**：自动化可启停并查看记录；模型/权限可切换。
- **P3**：子代理树可见；离线可读最近任务。

## 9. 与现有实现的差距

| 能力 | 现状 | 目标 |
| --- | --- | --- |
| 收件箱 | 只有当前会话提问 | 跨任务聚合 + 审批 |
| 任务 | 会话列表 + 任务进度页 | 任务为一等对象，详情页 |
| 产物 | 无 | 按任务聚合、可预览 |
| 自动化 | 无 | 列表/启停/记录 |
| 数据层 | 客户端解析事件 | Mobile BFF 稳定协议 |
