# com.openjiuwen.core.graph.visualization.DrawableSubgraphNode

## 类 DrawableSubgraphNode

```java
public class DrawableSubgraphNode extends DrawableNode
```

表示带嵌套 `DrawableGraph` 的节点，主要用于循环体和子工作流可视化。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `subgraph` | `DrawableGraph` | `null` | 当前节点承载的内部子图。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DrawableSubgraphNode(String id)` | 仅以节点 ID 创建子图节点。 |
| `public DrawableSubgraphNode(String id, DrawableGraph subgraph)` | 同时设置节点 ID 与内部子图。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public DrawableGraph getSubgraph()` | 返回内部子图。 |
| `public void setSubgraph(DrawableGraph subgraph)` | 更新内部子图。 |
