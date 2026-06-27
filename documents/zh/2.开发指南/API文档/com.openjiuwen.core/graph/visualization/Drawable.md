# com.openjiuwen.core.graph.visualization.Drawable

## 类 Drawable

```java
public class Drawable
```

负责把工作流组件树转换为 `DrawableGraph`，并导出 Mermaid 文本、PNG 或 SVG。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `graph` | `DrawableGraph` | `new DrawableGraph()` | 保存当前可视化图结构。 |
| `loopNodes` | `Set<String>` | `new HashSet<>()` | 记录需要强制生成条件自环的循环节点 ID。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Drawable()` | 创建空的可绘制图与循环节点集合。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addNode(String nodeId, ComponentComposable component)` | 按组件类型创建普通节点、子图节点或条件分支节点；`LoopComponent` / `AdvancedLoopComponent` 会补齐子图结束节点并添加条件自环。 |
| `public void addSimpleNode(String nodeId)` | 直接添加一个只包含 `id` 的普通 `DrawableNode`。 |
| `public void setStartNode(String nodeId)` | 将已存在节点标记为开始节点；节点不存在时抛出 `DRAWABLE_GRAPH_START_NODE_INVALID` 对应异常。 |
| `public void setEndNode(String nodeId)` | 将已存在节点标记为结束节点；节点不存在时抛出 `DRAWABLE_GRAPH_END_NODE_INVALID` 对应异常。 |
| `public void setBreakNode(String nodeId)` | 将已存在节点标记为 break 节点；节点不存在时抛出 `DRAWABLE_GRAPH_BREAK_NODE_INVALID` 对应异常。 |
| `public void addEdge(String source, String target, boolean conditional, boolean streaming, Object data)` | 添加普通边、流式边或条件边；循环节点会被改写为条件自环，`data` 也可提供条件分支目标与展示标签。 |
| `public void addEdge(String source, String target)` | 使用非条件、非流式默认参数添加普通边。 |
| `public String toMermaid(String title, int expandSubgraph, boolean enableAnimation)` | 将当前 `DrawableGraph` 导出为 Mermaid `flowchart TD` 文本；`expandSubgraph` 控制子图展开层级。 |
| `public String toMermaid()` | 使用空标题、不展开子图、关闭动画的默认参数导出 Mermaid 文本。 |
| `public byte[] toMermaidPng(String title, int expandSubgraph)` | 先生成 Mermaid 文本，再通过 `MermaidRenderer.renderPng` 调用 `mermaid.ink` 生成 PNG；文本为空时返回空字节数组。 |
| `public byte[] toMermaidSvg(String title, int expandSubgraph)` | 先生成 Mermaid 文本，再通过 `MermaidRenderer.renderSvg` 生成 SVG；文本为空时返回空字节数组。 |
| `public DrawableGraph getGraph()` | 返回内部维护的 `DrawableGraph`。 |

## 嵌套公共类型

| 类型 | 签名 | 说明 |
| --- | --- | --- |
| `TargetProvider` | `public interface TargetProvider` | 为无法直接从路由对象提取目标列表的自定义对象提供分支目标集合。 |
