# Runner 模块缺漏复核（修正版）

## 说明

- 本文件基于 `agent-core-python/openjiuwen/core/runner/**` 与 `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/runner/**` 的 2026-03-16 逐层复核结果。
- 目标不是重复列出所有映射，而是修正旧版 `runner_fixed.md` / `runner.md` 中的误报，只保留当前仍然真实存在的缺口或行为差异。
- 本轮重点复核了以下争议点：`Runner` facade、callback DSL、`ReplyTopicSubscription`、`ResponseCollector`、`MessageSerializer`、`MqMessageUtils`、`MessageTask`、`TagMgr`、`ThreadSafeDict`。

## 已确认属于旧文档误报或已经补齐的项

- `Runner.dist_pubsub` / `Runner.system_reply_sub`
  - Java `Runner.java` 已公开 `distPubsub()` / `systemReplySub()`。
- `Runner.runWorkflow*`、`runAgent*`、`runAgentGroup*` 的 `envs` 形参
  - Java `Runner.java` 已提供对应重载。
- `CallbackFramework.registerSync()`、`triggerDelayed()`、`triggerGenerator()`、`saveState()`
  - Java `callback/CallbackFramework.java` 已实现。
- callback decorator DSL
  - Java `CallbackFramework.java` 已实现 `on()`、`triggerOnCall()`、`emits()`、`emitsStream()`、`emitAround()`、`transformIo()`、`transformIoByEvents()`。
- `ReplyTopicSubscription.isActive()` 与 collector 并发上限校验
  - Java `ReplyTopicSubscription.java` 中 `isActive()` 存在，`registerCollector()` 也已检查 `maxRequestConcurrency`。
- `ResponseCollector.isCancelled()/isExpired()/isActive()/checkMessage()`
  - Java `ResponseCollector.java` 已实现。
- `CancelReason` / `CancelEvent`
  - Java `dsubscription/CancelReason.java` 与 `CancelEvent.java` 已存在。
- `MessageTask`
  - Python `mq_server_adapter.py` 和 Java `server_adapter/MessageTask.java` 均已存在，不能再记为缺失。
- `MqRemoteClient` 提前取消发送 `STOP`
  - Java `MqRemoteClient.java` 已保留该逻辑。
- `MqServerAdapter` 内部取消后的错误回写
  - Java `MqServerAdapter.java` 已在任务取消分支中回写错误响应。
- `MqMessageUtils.buildErrorResponse()` 错误码透传
  - Java 版已对 `BaseError` 透传 `getCode()`，不再是固定 `-1`。
- `TagMgr.display()`
  - Java `TagMgr.java` 已实现 `display()` / `display(boolean)`。
- `ThreadSafeDict.items()` / `setdefault()` / `pop()` / `update()`
  - Java `ThreadSafeDict.java` 已补齐这些表面方法。

## 本轮确认的真实缺口或行为差异

| 类别 | 缺口/差异 | Python 现状 | Java 现状 | 影响 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| MessageSerializer 默认类型表 | 内置注册集合不对齐 | Python `TYPE_REGISTRY` 默认已注册 `OutputSchema`、`CustomSchema`、`TraceSchema`、`InteractionOutput`、`WorkflowOutput`、`DmqRequestMessage`、`DmqResponseMessage` | Java 提供 `registerType()` / `unregisterType()` / `getTypeRegistry()`，但未发现同等默认注册 | 跨端收到带 `__class__` 的 payload 时，Java 默认无法直接还原这些类型 | `P0` |
| MessageSerializer 自动类标记 | 任意模型对象自动序列化能力不足 | Python 对带 `model_dump()` 的对象会写入 `__class__` 并递归序列化字段 | Java `serializePayload()` 主要处理 enum、map、collection、array、datetime，不会为一般 POJO 自动写 `__class__` | Java 发出的复杂对象 payload 与 Python 的类型化消息格式不是 1:1 | `P0` |
| ResponseCollector 远端错误异常契约 | 结构化错误未完全对齐 | Python `check_message()` 会构造包含 `error_code/error_msg` 的框架错误对象 | Java 当前抛 `IllegalStateException("Remote error code: msg")` | 上层难以直接按结构化错误码处理远端异常 | `P1` |
| ReplyTopicSubscription 构造便利性 | 缺省 topic 推导 | Python `ReplyTopicSubscription(topic=None)` 可从 `RunnerConfig` 自动推导 reply topic | Java 当前构造器必须显式传入 topic | 迁移 Python 调用时需要多写一层 topic 计算 | `P2` |
| Fake MQ 公开类型 | `FakeSubscription` 独立类型缺失 | Python 公开 `FakeSubscription` | Java 仅公开 `FakeMessageQueue`，内部直接复用 `SubscriptionInMemory` | 若调用方显式依赖 fake subscription 类型，将无法直接 1:1 迁移 | `P2` |
| decorator helper 模块 | `create_*_decorator()` 独立 API 缺失 | Python 在 `callback/decorator.py` 中公开一组 helper 函数 | Java 功能收敛在 `CallbackFramework` 成员方法中，没有独立 helper 模块 | 不是主链路缺口，但公开 API 表面不完全相同 | `P3` |

## 需要特别说明的非缺漏项

以下项目虽然与 Python 形态不同，但不应继续计入缺漏：

- Java callback DSL 不是语言级 decorator，而是返回/包装 `Function<Map<String, Object>, Object>`；这是语言差异，不是能力缺失。
- Java `CollectorKey`、`McpServerResource`、`SysOpToolResource` 以嵌套 `record` 暴露，而 Python 是顶层 dataclass / class；这是公开位置差异。
- Java `ThreadSafeDict` 额外公开 `put()`、`snapshot()`、`containsKey()`、`size()` 等显式方法；这属于 Java 风格增强，不是缺项。
- Java `Runner` 是静态 facade，Python `Runner` 通过类属性代理 `_RunnerImpl`；主功能保持对齐。

## 建议修复顺序

1. 先补 `MessageSerializer` 的默认类型注册与自动 `__class__` 标记能力。这是当前最实质的跨端兼容差异。
2. 再补 `ResponseCollector.checkMessage()` 的结构化远端错误封装，避免上层只能解析字符串异常。
3. 最后按兼容性需要补 `ReplyTopicSubscription` 的默认 topic 构造和 `FakeSubscription` 独立类型。
