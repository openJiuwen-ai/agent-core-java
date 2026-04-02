# events

`com.openjiuwen.core.common.logging.events` 定义结构化日志事件的基类、事件类型枚举、工厂注册表、脱敏工具，以及面向不同模块的事件载荷对象。

## 基础类型

| 类型 | 说明 |
| --- | --- |
| [`BaseLogEvent`](./events/BaseLogEvent.md) | 所有结构化日志事件的公共基类，负责通用元数据和 `toMap()` 序列化。 |
| [`EventClassRegistry`](./events/EventClassRegistry.md) | 内建 `LogEventType -> 事件类` 映射和自定义事件工厂注册中心。 |
| [`EventSanitizer`](./events/EventSanitizer.md) | 按字段名把敏感内容替换为 `<REDACTED>` 的工具类。 |

## 枚举

| 类型 | 说明 |
| --- | --- |
| [`EventStatus`](./events/EventStatus.md) | 事件状态枚举。 |
| [`LogEventType`](./events/LogEventType.md) | logging 子系统的全部内建事件类型键。 |
| [`LogLevel`](./events/LogLevel.md) | 结构化事件记录使用的日志等级枚举。 |
| [`ModuleType`](./events/ModuleType.md) | 结构化事件所属模块类别枚举。 |

## 事件载荷

| 类型 | 说明 |
| --- | --- |
| [`AgentEvent`](./events/AgentEvent.md) | Agent 执行、迭代和输入输出相关事件。 |
| [`ContextEvent`](./events/ContextEvent.md) | Context 写入、读取和清理相关事件。 |
| [`GraphEvent`](./events/GraphEvent.md) | 图执行、顶点调用、super step 和图存储相关事件。 |
| [`LLMEvent`](./events/LLMEvent.md) | 模型调用、流式输出、工具调用与用量统计事件。 |
| [`MemoryEvent`](./events/MemoryEvent.md) | 记忆增删改查和处理过程事件。 |
| [`PerformanceEvent`](./events/PerformanceEvent.md) | 性能指标和资源度量事件。 |
| [`RetrievalEvent`](./events/RetrievalEvent.md) | 检索查询、返回文档和得分信息事件。 |
| [`RunnerEvent`](./events/RunnerEvent.md) | Runner 生命周期和资源管理相关事件。 |
| [`SessionEvent`](./events/SessionEvent.md) | Session、checkpoint 与 checkpointer store 相关事件。 |
| [`StoreEvent`](./events/StoreEvent.md) | Store 增删改查和装载事件。 |
| [`StreamEvent`](./events/StreamEvent.md) | 流式事件的公共扩展基类。 |
| [`SysOperationEvent`](./events/SysOperationEvent.md) | 系统操作执行与流式输出事件。 |
| [`SystemEvent`](./events/SystemEvent.md) | 系统启动、关闭和错误事件。 |
| [`ToolEvent`](./events/ToolEvent.md) | 工具调用、入参与结果事件。 |
| [`UserInteractionEvent`](./events/UserInteractionEvent.md) | 用户输入和反馈事件。 |
| [`WorkflowEvent`](./events/WorkflowEvent.md) | Workflow 执行、组件运行、分支选择和输出事件。 |
| [`WorkflowStreamEvent`](./events/WorkflowStreamEvent.md) | Workflow 组件流式输出事件。 |

## 说明

- `StructuredLogEventTest` 覆盖 `EventClassRegistry` 的静态/动态注册、`BaseLogEvent` 默认值、若干事件字段赋值、`toMap()` 输出，以及 `EventSanitizer` 的默认脱敏行为。
- `WorkflowStreamEvent` 不是静态枚举映射的一部分，而是在 `EventClassRegistry.createEvent(LogEventType, Map<String, Object>)` 检测到 workflow 相关属性时由 `StreamEvent` 智能升级得到。
