# graph

`com.openjiuwen.core.graph` 提供工作流图定义、节点执行封装、编译入口，以及基于 Pregel 的图构建能力。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`pregel`](./graph/pregel.README.md) | `com.openjiuwen.core.graph.pregel` 提供 Pregel/BSP 运行时、channel 路由、中断处理、循环执行与任务调度原语。 |
| [`store`](./graph/store.README.md) | `com.openjiuwen.core.graph.store` 提供图状态持久化抽象、序列化器，以及 `CompiledGraph` 使用的内存存储实现。 |
| [`stream_actor`](./graph/stream_actor.README.md) | `com.openjiuwen.core.graph.stream_actor` 提供流式节点、payload 协议、consumer、processor 与 transform 辅助能力。 |
| [`visualization`](./graph/visualization.README.md) | `com.openjiuwen.core.graph.visualization` 提供可绘制图模型，以及生成 Mermaid 工作流可视化所需的辅助类型。 |

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AtomicNode`](./graph/AtomicNode.md) | 执行原子节点的通用基类，负责校验 session、调用内部逻辑并提交组件状态。 |
| [`CompiledGraph`](./graph/CompiledGraph.md) | 封装 `Pregel` 引擎与 `Checkpointer` 的可执行图实现。 |
| [`Executable`](./graph/Executable.md) | 具备 invoke/stream/collect/transform 能力的通用执行体基类。 |
| [`ExecutableGraph`](./graph/ExecutableGraph.md) | 从输入映射中提取 `inputs` 与 `config` 后执行图逻辑的抽象基类。 |
| [`Graph`](./graph/Graph.md) | 负责节点、边与编译流程管理的抽象图定义。 |
| [`GraphNodeState`](./graph/GraphNodeState.md) | 记录已参与执行的来源节点 ID 列表的图级状态对象。 |
| [`PregelGraph`](./graph/PregelGraph.md) | 基于 Pregel 的工作流图构建器。 |
| [`Router`](./graph/Router.md) | 用于计算条件边目标节点的函数式路由接口。 |
| [`Vertex`](./graph/Vertex.md) | 对单个图节点执行生命周期进行封装的运行时包装器。 |

## 说明

- 代表性测试：`CompiledGraphTest`、`PregelTest`。
- 当前根级校验范围同时要求 `store`、`stream_actor`、`visualization` 子包文档在同一导航树中保持可达且结构一致。
