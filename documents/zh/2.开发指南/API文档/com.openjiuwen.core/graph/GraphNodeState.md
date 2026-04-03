# com.openjiuwen.core.graph.GraphNodeState

## 类 GraphNodeState

```java
public class GraphNodeState
```

记录已参与执行的来源节点 ID 列表的图级状态对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `sourceNodeId` | `List<String>` | `-` | 已访问来源节点 ID 列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphNodeState()` | 创建空的 `GraphNodeState`。 |
| `public GraphNodeState(List<String> sourceNodeId)` | 基于给定来源节点列表创建 `GraphNodeState`；构造时会复制输入列表。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<String> getSourceNodeId()` | 返回当前 `sourceNodeId`。 |
| `public void setSourceNodeId(List<String> sourceNodeId)` | 更新 `sourceNodeId`。 |
| `public void merge(GraphNodeState other)` | 将另一个状态中的来源节点 ID 追加到当前列表。 |
