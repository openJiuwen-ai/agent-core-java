# com.openjiuwen.core.session.AgentSession

## 类 AgentSession

```java
public class AgentSession extends BaseSession
```

`AgentSession` 表示一次 agent 执行的根会话，内部会组装 `AgentStateCollection`、`StreamWriterManager`、`CallbackManager`、`Tracer` 与 `Checkpointer`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card)` | 使用显式 `sessionId`、配置、检查点与卡片信息创建会话。 |
| `public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card, List<StreamMode> streamModes)` | 在完整参数基础上额外指定启用的流模式。 |
| `public AgentSession(String sessionId, Config config, Checkpointer checkpointer)` | 省略 `card` 的便捷构造方法。 |
| `public AgentSession(String sessionId, Config config)` | 使用默认检查点和空 `card` 创建会话。 |
| `public AgentSession(String sessionId)` | 面向测试兼容的最简构造方法，内部会创建默认 `Config`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Config config()` | 返回会话配置。 |
| `public State state()` | 返回 `AgentStateCollection` 状态对象。 |
| `public Object tracer()` | 以 `Object` 形式返回 tracer。 |
| `public Tracer tracerTyped()` | 以强类型返回 `Tracer`。 |
| `public StreamWriterManager streamWriterManager()` | 返回流写入管理器。 |
| `public CallbackManager callbackManager()` | 返回回调管理器。 |
| `public String sessionId()` | 返回当前会话 ID。 |
| `public Object checkpointer()` | 以 `Object` 形式返回检查点实例。 |
| `public Checkpointer checkpointerTyped()` | 以强类型返回 `Checkpointer`。 |
| `public TraceAgentSpan span()` | 返回当前 agent 会话绑定的根 `TraceAgentSpan`。 |
| `public WorkflowSession createWorkflowSession()` | 基于当前 agent 的全局状态创建新的 `WorkflowSession`。 |
| `public String agentId()` | 优先从 `config.getAgentConfig()` 读取 ID，缺失时回退到 `card`。 |
| `public String agentName()` | 从 `card` 读取 agent 名称。 |
| `public String agentDescription()` | 从 `card` 读取 agent 描述。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`InMemoryCheckpointerTest`、`SessionTest`、`TracerDecoratorTest`。
- 当构造参数中的 `checkpointer` 为 `null` 时，会通过 `CheckpointerFactory.getCheckpointer()` 获取默认实现。
