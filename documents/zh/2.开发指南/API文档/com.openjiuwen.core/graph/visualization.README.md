# visualization

`com.openjiuwen.core.graph.visualization` 提供工作流可视化所需的可绘制图模型、条件分支标签与子图节点封装。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`Drawable`](./visualization/Drawable.md) | 将工作流组件树转换为 `DrawableGraph`，并导出 Mermaid 文本、PNG 或 SVG。 |
| [`DrawableBranchRouter`](./visualization/DrawableBranchRouter.md) | 保存条件分支的目标节点列表与展示标签。 |
| [`DrawableEdge`](./visualization/DrawableEdge.md) | 表示可绘制图中的一条边，支持条件边和流式边标记。 |
| [`DrawableGraph`](./visualization/DrawableGraph.md) | 保存节点、边、开始节点、结束节点与 break 节点集合。 |
| [`DrawableNode`](./visualization/DrawableNode.md) | 可绘制节点的基础数据模型，包含 `id`、展示名和元数据。 |
| [`DrawableSubgraphNode`](./visualization/DrawableSubgraphNode.md) | 带嵌套 `DrawableGraph` 的节点，用于循环体和子工作流。 |

## 说明

- `Drawable` 会把 `LoopComponent`、`AdvancedLoopComponent`、`SubWorkflowComponent`、`BranchComponent` 与 `IntentDetectionComponentImpl` 转换为适合 Mermaid 渲染的图结构。
- Mermaid 文本生成与 PNG/SVG 渲染由同包内非 public helper 完成，本页只列出 public 类型。
- 当前任务未包含 `visualization` 包的直接测试，说明依据源码人工核对。
