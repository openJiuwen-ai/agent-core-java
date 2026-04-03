# com.openjiuwen.core.graph.visualization.DrawableGraph

## 类 DrawableGraph

```java
public class DrawableGraph
```

保存可绘制图的节点、边以及开始/结束/break 节点集合。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `nodes` | `Map<String, DrawableNode>` | `new LinkedHashMap<>()` | 节点 ID 到 `DrawableNode` 的映射。 |
| `edges` | `List<DrawableEdge>` | `new ArrayList<>()` | 图中的全部边，保持添加顺序。 |
| `startNodes` | `List<DrawableNode>` | `new ArrayList<>()` | 被标记为开始节点的节点列表。 |
| `endNodes` | `List<DrawableNode>` | `new ArrayList<>()` | 被标记为结束节点的节点列表。 |
| `breakNodes` | `List<DrawableNode>` | `new ArrayList<>()` | 子图 break 节点列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DrawableGraph()` | 创建包含空节点映射与空边列表的图容器。 |
| `public DrawableGraph(Map<String, DrawableNode> nodes, List<DrawableEdge> edges, List<DrawableNode> startNodes, List<DrawableNode> endNodes, List<DrawableNode> breakNodes)` | 直接以现成节点、边和起止节点集合构造图容器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, DrawableNode> getNodes()` | 返回节点映射。 |
| `public List<DrawableEdge> getEdges()` | 返回边列表。 |
| `public List<DrawableNode> getStartNodes()` | 返回开始节点列表。 |
| `public List<DrawableNode> getEndNodes()` | 返回结束节点列表。 |
| `public List<DrawableNode> getBreakNodes()` | 返回 break 节点列表。 |
| `public void setBreakNodes(List<DrawableNode> breakNodes)` | 整体替换 break 节点列表。 |
