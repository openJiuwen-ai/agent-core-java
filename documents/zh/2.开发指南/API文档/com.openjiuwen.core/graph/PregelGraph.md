# com.openjiuwen.core.graph.PregelGraph

## 类 PregelGraph

```java
public class PregelGraph extends Graph
```

基于 Pregel 的工作流图构建器，负责注册节点、边、分支并编译成可执行图。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pregel` | `Pregel` | `-` | 编译后缓存的 `Pregel` 引擎实例。 |
| `checkpointer` | `Checkpointer` | `-` | 从当前 `session` 提取并注入到 `CompiledGraph` 的 checkpoint 组件。 |
| `session` | `BaseSession` | `-` | 最近一次编译时绑定的 `BaseSession`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PregelGraph()` | 创建空的 `PregelGraph` 构建器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Graph startNode(String nodeId)` | 校验 `nodeId` 后，将 `__start__` 到该节点的边加入图定义。 |
| `public Graph endNode(String nodeId)` | 将节点标记为结束节点，并补充到 `__end__` 的边。 |
| `public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll)` | 校验并注册节点，将 `Executable` 包装为 `Vertex`；`waitForAll` 为 `true` 时启用 barrier 汇聚。 |
| `public Map<String, Executable<?, ?>> getNodes()` | 返回 `nodeId -> Executable` 的当前节点映射。 |
| `public Vertex getVertex(String nodeId)` | 返回指定节点对应的内部 `Vertex` 包装器。 |
| `public Graph addEdge(Object sourceNodeId, String targetNodeId)` | 为单个或多个来源节点添加到目标节点的边。 |
| `public Graph addConditionalEdges(String sourceNodeId, Object router)` | 为来源节点注册条件分支路由。 |
| `public ExecutableGraph<?, ?> compile(BaseSession session)` | 使用默认参数编译当前图定义。 |
| `public ExecutableGraph<?, ?> compile(BaseSession session, Map<String, Object> kwargs)` | 初始化全部 `Vertex`，构建或复用 `Pregel` 引擎，并返回 `CompiledGraph`。 |
| `public void reset()` | 重置所有 `Vertex`，以便重复执行当前图。 |

## 嵌套公开类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| `Branch` | `静态类` | 条件分支定义，内部封装路由条件并在需要时转换为 `Function<Object, Object>`。 |
