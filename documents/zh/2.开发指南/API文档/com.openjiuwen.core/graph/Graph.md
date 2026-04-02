# com.openjiuwen.core.graph.Graph

## 抽象类 Graph

```java
public abstract class Graph
```

负责节点、边与编译流程管理的抽象图定义。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Graph startNode(String nodeId)` | 设置图的开始节点。 |
| `public Graph endNode(String nodeId)` | 设置图的结束节点。 |
| `public abstract Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll)` | 向图中注册节点；`waitForAll = true` 表示启用 barrier 语义。 |
| `public Graph addNode(String nodeId, Executable<?, ?> node)` | 以默认非 barrier 模式注册节点。 |
| `public abstract Graph addEdge(Object sourceNodeId, String targetNodeId)` | 从单个或多个来源节点向目标节点添加边。 |
| `public abstract Graph addConditionalEdges(String sourceNodeId, Object router)` | 为来源节点注册条件路由边。 |
| `public abstract ExecutableGraph<?, ?> compile(BaseSession session)` | 将图编译为可执行形式。 |
| `public ExecutableGraph<?, ?> compile(BaseSession session, Map<String, Object> kwargs)` | 带额外关键字参数编译图；默认直接委托给无参重载。 |
| `public abstract Map<String, Executable<?, ?>> getNodes()` | 返回当前图中的节点映射。 |

## 相关测试

- `PregelTest`
