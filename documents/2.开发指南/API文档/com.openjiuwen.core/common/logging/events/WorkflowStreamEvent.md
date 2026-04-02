# com.openjiuwen.core.common.logging.events.WorkflowStreamEvent

## 类 WorkflowStreamEvent

```java
public class WorkflowStreamEvent extends StreamEvent
```

`WorkflowStreamEvent` 在 `StreamEvent` 的基础上补充 Workflow 和组件维度字段，供工作流流式输出使用。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `workflowId` | `String` | `workflow_id` | 关联的 Workflow 标识。 |
| `workflowName` | `String` | `workflow_name` | Workflow 名称。 |
| `componentId` | `String` | `component_id` | 组件标识。 |
| `componentName` | `String` | `component_name` | 组件名称。 |
| `componentTypeStr` | `String` | `component_type_str` | 组件类型字符串。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.WORKFLOW_COMPONENT`。
- 通用元数据字段仍由 `BaseLogEvent` 提供，流式基础字段由 `StreamEvent` 提供。
- `EventClassRegistry.createEvent(LogEventType, Map<String, Object>)` 在检测到 `workflowId`、`componentId`、`componentTypeStr` 这些属性时，会优先创建该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- `addFieldsToMap()` 会先复用 `StreamEvent` 的序列化逻辑，再追加 workflow 相关键。
