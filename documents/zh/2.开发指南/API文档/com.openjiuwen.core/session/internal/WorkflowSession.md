# com.openjiuwen.core.session.internal.WorkflowSession

## 类 WorkflowSession

```java
public class WorkflowSession extends BaseSession
```

`WorkflowSession` 是 workflow 运行时的内部会话。它可以独立创建，也可以挂在父会话之下并复用父级的 `config`、`tracer` 与 `checkpointer`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public WorkflowSession(String workflowId, BaseSession parent, String sessionId, State state, CallbackManager callbackManager)` | 使用完整参数创建 workflow 会话；无父会话时会生成随机 `sessionId` 并创建默认 `Config`。 |
| `public WorkflowSession(String workflowId, BaseSession parent)` | 仅指定 workflow ID 与父会话。 |
| `public WorkflowSession(String workflowId)` | 创建无父会话的 workflow。 |
| `public WorkflowSession()` | 面向测试兼容的空 workflow 构造方法。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static WorkflowSession create()` | 创建一个默认 `WorkflowSession`。 |
| `public static WorkflowSession create(String sessionId)` | 创建一个显式指定 `sessionId` 的 `WorkflowSession`。 |
| `public void setStreamWriterManager(StreamWriterManager streamWriterManager)` | 仅在当前未设置时写入流管理器。 |
| `public void setTracer(Object tracer)` | 直接覆盖当前 tracer。 |
| `public void setActorManager(ActorManager actorManager)` | 仅在当前未设置时写入 actor manager。 |
| `public void setWorkflowId(String workflowId)` | 更新 workflow ID。 |
| `public String workflowId()` | 返回当前 workflow ID。 |
| `public String mainWorkflowId()` | 返回当前 workflow ID。 |
| `public int workflowNestingDepth()` | 顶层 `WorkflowSession` 固定返回 `0`。 |
| `public BaseSession parent()` | 返回父会话。 |
| `public ActorManager actorManager()` | 返回绑定的 actor manager。 |
| `public Config config()` | 返回配置对象。 |
| `public State state()` | 返回当前 workflow 状态。 |
| `public Object tracer()` | 返回当前 tracer。 |
| `public StreamWriterManager streamWriterManager()` | 返回流写入管理器。 |
| `public CallbackManager callbackManager()` | 返回回调管理器。 |
| `public String sessionId()` | 返回 session ID。 |
| `public Object checkpointer()` | 有父会话时复用父检查点；否则从 `CheckpointerFactory` 获取默认实现。 |
| `public void close()` | 若存在 actor manager，则调用 `shutdown()`。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`InMemoryCheckpointerTest`、`SessionBasicTest`、`SessionTest`、`WorkflowInteractionTest`。
- 当父会话不为空时，`config()` 和 `tracer()` 会直接复用父作用域的对象。
