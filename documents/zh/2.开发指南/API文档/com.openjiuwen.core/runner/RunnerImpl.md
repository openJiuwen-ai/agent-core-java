# com.openjiuwen.core.runner.RunnerImpl

## 类 RunnerImpl

```java
public class RunnerImpl
```

`RunnerImpl` 负责装配资源管理器、本地消息队列、回调框架与分布式运行支撑，并提供 workflow、agent 与 agent group 的实际执行入口。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `DEFAULT_RUNNER_ID` | `String` | `"global"` | 默认的 runner 标识。 |
| `DEFAULT_AGENT_SESSION_ID` | `String` | `"default_session"` | 未显式提供会话 ID 时使用的默认 agent 会话标识。 |
| `AGENT_CONVERSATION_ID` | `String` | `"conversation_id"` | 从输入映射中读取会话 ID 时使用的键名。 |
| `runnerId` | `String` | `-` | 当前 `RunnerImpl` 实例的 runner 标识。 |
| `resourceManager` | `ResourceMgr` | `-` | 管理 workflow、agent、agent group、tool、model 与 prompt 等资源的资源管理器。 |
| `messageQueue` | `LocalMessageQueue` | `-` | 进程内发布/订阅使用的本地消息队列。 |
| `callbackFramework` | `CallbackFramework` | `-` | 当前 runner 绑定的回调框架。 |
| `distributeMessageQueue` | `MessageQueueBase` | `-` | 分布式模式下使用的消息队列实例；未启用分布式模式时为 `null`。 |
| `systemReplySub` | `ReplyTopicSubscription` | `-` | 分布式模式下的系统回复主题订阅器；未启用分布式模式时为 `null`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public RunnerImpl()` | 使用默认 runner ID `global` 创建实例。 |
| `public RunnerImpl(String runnerId, RunnerConfig config)` | 使用给定 runner ID 与配置创建实例；`runnerId` 为 `null` 时回退到 `global`，`config` 为 `null` 时回退到 `RunnerConfig.DEFAULT`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ResourceMgr getResourceMgr()` | 返回 workflow、agent、agent group、tool、model 与 prompt 等资源共用的 `ResourceMgr`。 |
| `public LocalMessageQueue getPubsub()` | 返回进程内发布/订阅通信使用的 `LocalMessageQueue`。 |
| `public CallbackFramework getCallbackFramework()` | 返回当前 runner 绑定的 `CallbackFramework`。 |
| `public MessageQueueBase getDistPubsub()` | 返回跨进程通信使用的分布式消息队列；未启用分布式模式时返回 `null`。 |
| `public ReplyTopicSubscription getSystemReplySub()` | 返回分布式模式下的系统回复主题订阅器；未启用分布式模式时返回 `null`。 |
| `public void setConfig(RunnerConfig config)` | 设置当前生效的 `RunnerConfig`。 |
| `public RunnerConfig getConfig()` | 返回当前生效的 `RunnerConfig`。 |
| `public boolean start()` | 启动 runner，并在配置要求时初始化 checkpointer、分布式消息队列和系统回复订阅。 |
| `public boolean stop()` | 停止 runner、关闭消息队列并释放资源；若停止过程抛出异常则返回 `false`。 |
| `public Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context, Map<String, Object> envs)` | 根据工作流 ID 或 `Workflow` 实例执行工作流。 |
| `public Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs)` | 以流式方式执行工作流，并返回 `WorkflowChunk` 迭代器。 |
| `public Object runAgent(Object agent, Object inputs, Object session, ModelContext context, Map<String, Object> envs)` | 执行单个 agent，并在执行前后调用 `AgentSessionApi.preRun()` 与 `postRun()`。 |
| `public Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs)` | 以流式方式执行单个 agent；当返回迭代器被消费完毕时自动补做 `postRun()`。 |
| `public Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context, Map<String, Object> envs)` | 执行 agent group。 |
| `public Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs)` | 以流式方式执行 agent group。 |
| `public void release(String sessionId)` | 通过当前 checkpointer 释放指定会话 ID 关联的状态与资源。 |
| `public static String generateWorkflowKey(String workflowId, String workflowVersion)` | 按 `workflowId + "_" + workflowVersion` 生成工作流键；当 `workflowVersion` 为 `null` 时后半段为空字符串。 |

## 说明

- 相关测试：`RunnerTest`。
