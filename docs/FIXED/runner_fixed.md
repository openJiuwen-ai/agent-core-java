# Runner 模块缺漏复核

## 说明

- 本文件基于 `agent-core-python/openjiuwen/core/runner/**` 与 `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/runner/**` 的第二轮逐类、逐方法复核整理。
- 目标不是重复列出所有映射，而是修正旧版缺漏清单中的误报，只保留仍然真实存在的缺口与行为差异。

## 已确认属于旧文档误报或已经补齐的项

- `Runner.dist_pubsub` / `Runner.system_reply_sub`
  - Java `Runner.java` 已公开 `distPubsub()` / `systemReplySub()`
- `Runner.runWorkflow*`、`runAgent*`、`runAgentGroup*` 的 `envs` 形参
  - Java `Runner.java` 已提供带 `envs` 的重载
- `Runner.start()` / `Runner.stop()` 的分布式启动与清理
  - Java `RunnerImpl.start()/stop()` 已显式处理 distributed mode、reply subscription 与 distributed MQ
- `CallbackFramework.registerSync()`、`triggerDelayed()`、`triggerGenerator()`、`saveState()`
  - Java `callback/CallbackFramework.java` 已实现
- `ReplyTopicSubscription.isActive()`、`CollectorKey`
  - Java 已实现 `isActive()`，并以公开嵌套 `record CollectorKey` 暴露
- `ResponseCollector.isCancelled()/isExpired()/isActive()/checkMessage()`
  - Java `ResponseCollector.java` 已实现
- `CancelReason` / `CancelEvent`
  - Java `dsubscription/CancelReason.java` 与 `CancelEvent.java` 已存在
- `MessageTask`
  - Java `drunner/server_adapter/MessageTask.java` 已存在
- `MqRemoteClient` 提前取消发送 `STOP`
  - Java `MqRemoteClient.java` 已保留 `sendStopMessage()`
- `MqServerAdapter` 内部取消后的错误回写
  - Java `MqServerAdapter.java` 已在 `cancelTask(..., innerCancel=true)` 中回写取消错误
- `ResourceMgr.refreshMcpServer()` / `getMcpToolInfos()`
  - Java `ResourceMgr.java` 已实现
- `TagMgr.display()`
  - Java `TagMgr.java` 已实现 `display()` / `display(boolean)`
- `ThreadSafeDict`
  - Java `ThreadSafeDict.java` 已存在公开类，只是表面 API 仍有差异

## 第二轮新增确认的真实缺口

| 类别 | 缺口 | Python 现状 | Java 现状 | 影响 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| Callback DSL | `AsyncCallbackFramework.on()` | Python 直接支持 decorator 注册 | Java 无直接入口 | 无法按 Python 风格声明式注册回调 | `P1` |
| Callback DSL | `trigger_on_call()` | Python 支持函数调用前自动触发事件 | Java 无对应 API | 装饰式事件埋点无法 1:1 迁移 | `P1` |
| Callback DSL | `emits()` / `emits_stream()` / `emit_around()` | Python 支持结果回传、流式逐项触发、前后置触发 | Java 无对应 API | 事件编排 DSL 缺失 | `P1` |
| Callback DSL | `transform_io()` 及 `create_transform_io_*` | Python 支持基于事件或函数的输入/输出转换 | Java 无对应 API | I/O 变换型 callback 用法无法直接迁移 | `P1` |
| ReplyTopicSubscription | `register_collector()` 的并发上限保护 | Python 会检查 `distributed_config.max_request_concurrency` | Java `registerCollector()` 未校验 collector 数量 | 高并发下缺少与 Python 一致的保护阈值 | `P0` |
| MessageSerializer | 深度递归、类型注册、`datetime` 序列化 | Python 有 `TYPE_REGISTRY`、`MAX_RECURSE_DEPTH` 和 `datetime` 自定义处理 | Java 仅做普通 JSON 映射 | 复杂 payload 的跨端兼容性不足 | `P0` |
| MqMessageUtils | `build_error_response()` 错误码透传 | Python 使用 `error.error_code` 与 `error.message` | Java 当前固定写入 `errorCode = -1` | 远端错误诊断粒度下降 | `P0` |
| Fake MQ helper | `FakeSubscription` | Python 公开独立 helper 类型 | Java 无单独公开类型 | 若上层依赖 fake subscription 类型，将无法直接迁移 | `P2` |
| ThreadSafeDict | `items()` / `setdefault()` / `pop()` / `update()` | Python 公开完整容器风格 API | Java 改为 `snapshot()` / `remove()` / `putAll()` 等替代 | 表面兼容性不是 1:1 | `P2` |

## 第二轮补充的行为差异

| 项目 | Python 行为 | Java 行为 | 结论 |
| --- | --- | --- | --- |
| `ReplyTopicSubscription(topic=None)` | 可从 `RunnerConfig` 自动推导 reply topic | Java 构造器必须显式传入 topic | 低优先级便利性差异 |
| `CollectorKey` 暴露形态 | Python 顶层 dataclass | Java 公共嵌套 `record` | 不是缺漏，属于公开位置变化 |
| `McpServerResource` / `SysOpToolResource` | Python 顶层类 | Java `ToolMgr` 嵌套 `record` | 不是缺漏，属于建模方式变化 |
| `ResourceMgr.add_tool()` | Python 单个/批量都由同一 API 承担 | Java 拆成 `addTool()` + `addTools()` | 不是缺漏，属于重载拆分 |

## 建议修复顺序

1. 先补 `ReplyTopicSubscription.registerCollector()` 的并发上限保护、`MessageSerializer` 的深度类型序列化、`MqMessageUtils.buildErrorResponse()` 的错误码透传。这三项会直接影响分布式跨端一致性。
2. 再补 `AsyncCallbackFramework` 的 decorator DSL 与 I/O transform 入口，恢复 Python 侧最显著的声明式 API。
3. 最后按兼容性需要补 `FakeSubscription` 与 `ThreadSafeDict` 的精确表面方法。
