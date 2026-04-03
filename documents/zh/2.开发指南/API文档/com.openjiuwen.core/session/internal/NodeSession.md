# com.openjiuwen.core.session.internal.NodeSession

## 类 NodeSession

```java
public class NodeSession extends BaseSession
```

`NodeSession` 为 workflow 节点创建独立的执行作用域，并在可能时从 `WorkflowStateCollection` 派生节点级状态。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public NodeSession(BaseSession session, String nodeId, String nodeType, boolean skipTrace)` | 基于父会话创建节点会话，并显式设置节点类型与是否跳过 trace。 |
| `public NodeSession(BaseSession session, String nodeId, String nodeType)` | 使用默认 `skipTrace=false` 创建节点会话。 |
| `public NodeSession(BaseSession session, String nodeId)` | 仅指定节点 ID，节点类型留空。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String nodeId()` | 返回节点 ID。 |
| `public String nodeType()` | 返回节点类型。 |
| `public String executableId()` | 返回节点可执行 ID；若存在父节点，会按 `parentId.nodeId` 级联生成。 |
| `public String parentId()` | 返回父节点的 `executableId()`；根节点为空字符串。 |
| `public String workflowId()` | 返回当前节点所属 workflow ID。 |
| `public String mainWorkflowId()` | 返回最外层 workflow ID。 |
| `public int workflowNestingDepth()` | 返回 workflow 嵌套深度。 |
| `public BaseSession parent()` | 返回父 `BaseSession`。 |
| `public Config config()` | 委托返回父会话配置。 |
| `public State state()` | 返回节点作用域状态；若父状态是 `WorkflowStateCollection`，则为节点派生状态。 |
| `public Object tracer()` | 委托返回父会话 tracer。 |
| `public StreamWriterManager streamWriterManager()` | 委托返回父会话流写入管理器。 |
| `public CallbackManager callbackManager()` | 委托返回父会话回调管理器。 |
| `public String sessionId()` | 委托返回父会话 session ID。 |
| `public Object checkpointer()` | 委托返回父会话检查点。 |
| `public boolean skipTrace()` | 返回该节点是否应跳过 trace。 |
| `public Object actorManager()` | 从父会话读取 actor manager。 |
| `public Object nodeConfig()` | 从 workflow 配置中读取当前节点组件配置，支持 `WorkflowConfig` 与 `Map` 两种路径。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`InMemoryCheckpointerTest`、`SessionBasicTest`、`SessionTest`、`WorkflowInteractionTest`。
- 当父会话本身是 `NodeSession` 或 `WorkflowSession` 时，`workflowId()`、`mainWorkflowId()` 与嵌套深度会继承父作用域。
