# com.openjiuwen.core.common.logging.events.WorkflowEvent

## 类 WorkflowEvent

```java
public class WorkflowEvent extends BaseLogEvent
```

`WorkflowEvent` 用于记录 Workflow 执行、组件运行、分支决策、输出分片和最终输出数据。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `workflowId` | `String` | `workflow_id` | 关联的 Workflow 标识。 |
| `workflowName` | `String` | `workflow_name` | Workflow 名称。 |
| `componentId` | `String` | `component_id` | 组件标识。 |
| `componentName` | `String` | `component_name` | 组件名称。 |
| `componentTypeStr` | `String` | `component_type_str` | 组件类型字符串。 |
| `branchCondition` | `String` | `branch_condition` | 分支判断条件。 |
| `selectedBranch` | `String` | `selected_branch` | 最终命中的分支。 |
| `inputs` | `Map<String, Object>` | `inputs` | 输入数据或入参快照。 |
| `outputs` | `Object` | `outputs` | 输出数据或执行结果。 |
| `chunk` | `Object` | `chunk` | 当前分片或流式片段。 |
| `chunkIdx` | `Integer` | `chunk_idx` | Workflow 输出分片序号。 |
| `outputData` | `Map<String, Object>` | `output_data` | 输出结果或中间产物。 |
| `executionTimeMs` | `Double` | `execution_time_ms` | 执行耗时，单位毫秒。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.WORKFLOW`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 workflow execute/component/branch/output chunk 相关枚举值映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `output_data` 做脱敏。
- `StructuredLogEventTest` 覆盖了 `workflowId`、`workflowName`、`componentId` 和 `componentName` 的典型赋值。
