# 工作流

本栏目聚焦 openJiuwen Java 的工作流图模型、组件体系和执行入口。Java 侧这一块的 API 与示例最完整，因此栏目会按“先理解整体，再学习组件，再进入高阶执行”的顺序组织内容。

## 页面映射

| 页面 | 关注点 | 主要 Java 依据 | 说明 |
| --- | --- | --- | --- |
| [概述](概述.md) | 工作流定位、适用场景、栏目阅读顺序 | `com.openjiuwen.core.workflow`、`examples/workflow_agent` | 从整体视角理解 Java 工作流在 agent 体系中的位置。 |
| [关键概念](关键概念.md) | 工作流图、组件、边、会话、输出、流式块等术语 | `Workflow`、`WorkflowCard`、`WorkflowSessions`、`WorkflowOutput` | 统一后续页面的术语口径。 |
| [构建工作流](构建工作流.md) | 创建实例、注册组件、连边、执行 `invoke` / `stream` | `workflow` 根包与 `examples/workflow_agent` | 栏目主干教程页。 |
| [工作流可视化](工作流可视化.md) | Mermaid 文本、PNG / SVG 导出与 `DrawableGraph` 路线 | `graph.visualization`、`Workflow.draw(...)`、`drawBytes(...)` | 解释图结构如何被导出和展示。 |
| [使用组件](使用组件/README.md) | 组件目录入口与组件编排路线 | `com.openjiuwen.core.workflow.component` | 进入组件层细节前的导航页。 |

## 组件子目录映射

`使用组件/` 子目录会继续展开以下页面：

- [使用预置组件](使用组件/使用预置组件.md)
- [定义组件输入输出格式](使用组件/定义组件输入输出格式.md)
- [组件支持的能力类型](使用组件/组件支持的能力类型.md)
- [编排组件](使用组件/编排组件.md)

## 推荐阅读顺序

1. 先读 [概述](概述.md) 和 [关键概念](关键概念.md)，建立图模型和执行术语。
2. 再读 [构建工作流](构建工作流.md)，形成最小工作流搭建路径。
3. 在开始排查连边、子图或流式路径时，继续看 [工作流可视化](工作流可视化.md)。
4. 之后进入 [使用组件](使用组件/README.md) 深入组件层。

## 参考入口

- [API 文档：workflow](../API文档/com.openjiuwen.core/workflow.README.md)
- [API 文档：workflow.component](../API文档/com.openjiuwen.core/workflow/component.README.md)
- [示例：Workflow Agent Java Example](../../../../examples/workflow_agent/README.md)

## 本栏说明

- 本栏目围绕 Java 工作流的整体定位、概念、构建、可视化和组件使用展开。
- 代码路径、组件命名和执行步骤只引用 Java 当前真实存在的 API、示例和测试。
- `使用组件` 子目录会明确区分当前稳定组件能力与兼容/legacy 组件布局。
- `工作流可视化` 页面直接以 `Workflow.draw(...)` / `drawBytes(...)` 这条 Java 主线展开。
