# com.openjiuwen.core.graph.Vertex

## 类 Vertex

```java
public class Vertex extends AtomicNode implements StreamConsumer
```

对单个图节点执行生命周期进行封装的运行时包装器。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `SUB_WORKFLOW_COMPONENT` | `String` | `"sub_workflow"` | 用于识别子工作流组件的固定类型标识。 |
| `nodeId` | `String` | `-` | 当前图节点 ID。 |
| `executable` | `Executable<Object, Object>` | `-` | 当前节点绑定的执行体。 |
| `context` | `Object` | `-` | 初始化时注入的附加上下文对象。 |
| `session` | `NodeSession` | `-` | 当前节点专用的 `NodeSession`。 |
| `streamCalledTimeout` | `int` | `10` | 等待 stream-in 完成的超时时间，单位秒。 |
| `streamDone` | `CompletableFuture<Object>` | `-` | 标记 stream-in 执行完成状态的 future。 |
| `callCount` | `int` | `0` | batch-in 调用次数。 |
| `streamCallCount` | `int` | `0` | stream-in 调用次数。 |
| `isEndNode` | `boolean` | `false` | 当前节点是否为结束节点。 |
| `isStarted` | `boolean` | `false` | 是否已经发送组件开始追踪事件。 |
| `isCallStarted` | `boolean` | `false` | 是否已经发送输入追踪事件。 |
| `nodeConfig` | `NodeConfig` | `-` | 从 `session` 中解析出的节点配置。 |
| `componentAbility` | `List<ComponentAbility>` | `-` | 当前节点生效的能力列表。 |
| `hasStreamCall` | `boolean` | `false` | 是否包含 `COLLECT` 或 `TRANSFORM` 等 stream-in 能力。 |
| `hasCall` | `boolean` | `false` | 是否包含 `INVOKE` 或 `STREAM` 等 batch-in 能力。 |
| `isFirstInit` | `boolean` | `true` | 是否首次初始化，用于控制一次性初始化日志。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Vertex(String nodeId, Executable<?, ?> executable)` | 基于节点 ID 与执行体创建 `Vertex`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean init(BaseSession session, Map<String, Object> kwargs)` | 基于 `BaseSession` 创建 `NodeSession`，解析 `context`、`NodeConfig`、能力集与 stream 超时配置。 |
| `public Map<String, Object> call(GraphNodeState state, Object config) throws Exception` | 执行节点的 batch-in 能力；成功时返回包含 `source_node_id` 的结果映射。 |
| `public void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback)` | 异步执行 `COLLECT`、`TRANSFORM` 等 stream-in 能力，并通过 `latch` 与 `errorCallback` 报告状态。 |
| `public boolean isDone()` | 返回当前节点的 batch/stream 调用是否已经完成。 |
| `public boolean shouldHandleMessage()` | 返回当前节点是否具备 stream-in 能力，需要处理 actor 消息。 |
| `public void reset()` | 清空调用计数与 stream 状态，以便复用当前节点。 |
| `public String getNodeId()` | 返回当前 `nodeId`。 |
| `public Executable<Object, Object> getExecutable()` | 返回当前 `executable`。 |
| `public NodeSession getSession()` | 返回当前 `session`。 |
| `public boolean isEndNode()` | 返回当前 `isEndNode` 标记。 |
| `public void setEndNode(boolean endNode)` | 更新 `isEndNode` 标记。 |

## 嵌套公开类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| `MixModeAware` | `接口` | 支持混合模式（stream + batch）执行的标记接口。 |
