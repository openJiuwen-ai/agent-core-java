# openjiuwen.agent_teams.agent.coordination

`CoordinationPackage` 对应 Python 包初始化文件 `openjiuwen/agent_teams/agent/coordination/__init__.py`，记录 coordination 子系统的公开导出面。

## 导出符号

- `AgentLifecycleHandler`
- `BaseCoordinationHandler`
- `CoordinationEvent`
- `CoordinationKernel`
- `DispatcherHost`
- `EventBus`
- `EventDispatcher`
- `InnerEventMessage`
- `InnerEventType`
- `MemberHandler`
- `MessageHandler`
- `StaleTaskHandler`
- `TaskBoardHandler`
- `WakeCallback`

该 facade 只表达包级公开 surface。dispatcher、event bus、handlers 和 kernel 的业务逻辑由各自模块任务翻译。

## EventDispatcher

`EventDispatcher` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/dispatcher.py`，负责 coordination 事件的入口过滤和回调分发。

- agent 未 ready 时跳过事件。
- inner event 使用 `InnerEventType` 的字符串值触发回调。
- human agent 会静默跳过 `POLL_TASK` 和 `POLL_MAILBOX` inner poll 事件。
- transport event 需要当前 blueprint 存在 `memberName`。
- human agent 的 transport event 只允许 lifecycle、message、broadcast 和 self task-claimed 相关事件。
- 多个 handler 注册同一事件 key 时按 dispatcher 构造顺序稳定 fan-out，例如 `MEMBER_SHUTDOWN` 先触发 member handler，再触发 message drain；`POLL_TASK` 先触发 stale sweep，再触发 team completion。

## EventBus

`EventBus` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/event_bus.py`，是 coordination 的事件唤醒循环。

- `start` 绑定 wake callback，启动后台队列消费循环，并为非 human agent 启动 mailbox/task 周期 poll。
- `stop` 停止运行状态，取消 poll timer，投递 shutdown inner event 并等待 loop 收尾。
- `pausePolls` 只暂停周期 poll，保留主事件 loop。
- `resumePolls` 仅在已暂停且正在运行时恢复 poll；human agent 因周期 poll 禁用，恢复后也不会创建 poll timer。
- `enqueue` 支持 inner event 和 transport `EventMessage`，transport event 会包装为 dispatcher 可消费的 `TransportEvent`。
- wake callback 抛出的异常会记录日志并吞掉，loop 继续处理后续事件。

## CoordinationKernel

`CoordinationKernel` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/kernel.py`，负责 TeamAgent 的 coordination 生命周期、transport wiring 和 inner event 入口。

- `setup` 在 role 已知后构造 `EventBus` 和 `EventDispatcher`；业务回调在 `start` 时作为 wake callback 绑定。
- `start` 初始化 backend/session/workspace/memory 协作者，更新成员状态为 `ready`，启动 event bus，并按 team topic 订阅 transport。
- transport 订阅会先通知本地 event listener，再过滤自己发布的事件，非 self 事件进入 event bus。
- `pause` 仅从 `running` 状态生效，会 drain agent task、持久化 allocator、leader 标记 live teammate 为 `paused`、发布 standby、退订 transport、停止 event bus 并释放 session。
- `stop` 是 terminal teardown，会标记 live teammate 为 `stopped`，取消 recovery task，关闭 spawned handles、memory manager、event bus 和 stream。
- `enqueueUserInput`、`enqueueMailboxAfterFirstIteration`、`wakeMailboxIfInterruptCleared` 和 `finalizeRound` 保留 Python kernel 的 inner event/wakeup/round-finalize 入口。

## coordination.handlers

`CoordinationHandlersPackage` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/__init__.py`，记录 scenario-scoped handler 包的公开导出面。

导出顺序与 Python `__all__` 保持一致：

- `AgentLifecycleHandler`
- `BaseCoordinationHandler`
- `EventCallback`
- `MemberHandler`
- `MessageHandler`
- `StaleTaskHandler`
- `TaskBoardHandler`
- `TeamCompletionHandler`

该 facade 只同步包初始化文件的描述和导出清单，不实现具体 handler 业务逻辑。

## BaseCoordinationHandler

`BaseCoordinationHandler` 和 `EventCallback` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/base.py`。

- 构造函数把同一个 `DispatcherHost` 分别按 round control 和 lifecycle control 暴露给子类。
- `PollController`、`TeamAgentBlueprint`、`TeamInfra` 作为共享依赖保存。
- 子类声明 `event key -> method name` 映射，并通过显式 resolver 绑定为 `EventCallback`。
- `getCallbacks` 保持 Python `EVENT_METHOD_MAP` 的插入顺序。

## AgentLifecycleHandler

`AgentLifecycleHandler` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/agent_lifecycle.py`，处理本地 agent 生命周期相关事件。

- `USER_INPUT` inner event 会读取 payload 的 `content`，缺省为空字符串，并转交 `deliverInput`。
- `STANDBY` transport event 会暂停周期 poll。
- `CLEANED` transport event 对 leader 是 no-op；非 leader 会触发 `shutdownSelf`。
- `TOOL_APPROVAL_RESULT` 只处理 member name 等于当前 blueprint member 的事件，并用 `InteractiveInput` 恢复 HITL interrupt。
- `TASK_PLAN_RESPONSE` 同样只处理目标为自己的事件，且必须带 `tool_call_id`；响应 payload 包含 `approved`、`feedback` 和 `plan_id`。

## MemberHandler

`MemberHandler` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/member.py`，处理六类 `MEMBER_*` lifecycle event。

- leader 会观察所有成员事件并记录本地 lifecycle 状态。
- teammate 只响应 payload `member_name` 等于自己的事件。
- `MEMBER_CANCELED` 指向自己时会调用 `cancelAgent`。
- human agent 的 `MEMBER_SHUTDOWN` 在 `force=true` 或没有 in-flight round 时会直接 `shutdownSelf`；否则等待当前 controller-driven round 自然结束。
- leader 在成员状态从非 idle 进入 `ready` / `error` 时检查 stale claimed tasks，超过 10 分钟且未被节流的任务会合并成一条提醒消息。
- stale nudge throttle map 与 stale-task handler 共享，防止 status flip 与 poll tick 重复提醒同一任务。

## MessageHandler

`MessageHandler` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/message.py`，处理 `MESSAGE`、`BROADCAST`、`POLL_MAILBOX` 以及 `MEMBER_SHUTDOWN` mailbox drain。

- leader 在 direct message 指向 `user` 伪成员时自动标记已读，并把 direct/broadcast 事件转发给 human-agent inbound callback。
- 普通 message/broadcast 事件会恢复 poll，然后读取 direct 与 broadcast 未读消息，按 timestamp 倒序交给当前 agent round。
- `POLL_MAILBOX` 只做 unread mailbox drain；存在 pending interrupt 时暂停处理且不标记消息已读。
- teammate 收到自己的 `MEMBER_SHUTDOWN` 时用 steer 模式排空 mailbox，避免 teardown 前漏掉最终消息。
- bridge agent 的远端 relay 保留为窄接口扩展点，未接入远端 adapter 时回退到普通 teammate 文本格式。

## StaleTaskHandler

`StaleTaskHandler` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/stale_task.py`，在 `POLL_TASK` tick 上扫描长时间未推进的任务。

- 每个成员会检查指派给自己的 `claimed` stale task；leader 还会检查所有成员的 `claimed` stale task。
- 本人持有的 stale claim 会注入本地 agent round；leader 发现其他成员的 stale claim 时通过 message manager 发送 direct nudge。
- stale claim throttle 与 `MemberHandler` 共享同一个 map，避免 poll tick 与成员状态变化在同一窗口重复提醒。
- leader 会额外检查超过 10 分钟仍为 `pending` 的任务，并把列表作为自提示输入给 leader，由模型决定合适的认领提醒对象。
- claimed/pending throttle 都会清理已不再相关的 task id，避免长期保存过期节流状态。

## TaskBoardHandler

`TaskBoardHandler` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/task_board.py`，处理任务看板事件和面向成员的任务通知。

- `TASK_CLAIMED` 指向当前成员时会恢复 poll，并把任务指派提示送入当前 agent round。
- `TASK_CLAIMED` 指向其他成员时，普通成员/leader 会转为看板 nudge；human-agent avatar 会忽略其他成员的 claim，避免自主扫板。
- human-agent 自身被指派任务时，会查找任务标题并使用 HITT 专用通知文案，提示 avatar 等待控制者输入。
- `TASK_PLAN_RESPONSE` 指向当前成员时发送计划批准/驳回提示；带 `tool_call_id` 的响应只恢复 pending interrupt，不额外注入输入。
- task board 事件会恢复 poll 并渲染未完成任务；leader 在无未完成任务时输出 all-done 提示，poll 触发的空看板检查保持静默。
- task board 行通过 `ExternalFormat.renderTaskLine` 渲染，与外部 agent 的任务上下文格式保持一致。

## TeamCompletionHandler

`TeamCompletionHandler` 对应 Python 文件 `openjiuwen/agent_teams/agent/coordination/handlers/team_completion.py`，驱动并消费团队完成生命周期事件。

- leader 的 `POLL_TASK` idle tick 会调用 backend completion 检查；非 leader、正在运行 round 或有 in-flight round 时跳过。
- backend 返回完成快照时发布 `TEAM_COMPLETED`，并用 rising-edge guard 防止同一完成状态重复发布。
- backend 返回未完成时会重新 arm guard，使下一次完成上升沿可以再次发布。
- persistent 生命周期团队完成后会调用 lifecycle conclude，temporary 团队由 leader 后续清理流程处理。
- `TASK_LIST_DRAINED` 会记录 drained task 数，并逐个触发注册的 completion callback；单个 callback 失败不会阻断后续 callback。
- `TEAM_COMPLETED` 事件消费侧只做结构化日志，保留给后续 SDK 通知扩展。
