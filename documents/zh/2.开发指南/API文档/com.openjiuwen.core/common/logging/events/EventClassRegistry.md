# com.openjiuwen.core.common.logging.events.EventClassRegistry

## 类 EventClassRegistry

```java
public final class EventClassRegistry
```

`EventClassRegistry` 维护 `LogEventType` 到具体事件类构造器的内建映射，并支持按字符串事件键动态注册自定义事件工厂。

## 内建映射摘要

| 类别 | 事件类 | 数量 |
| --- | --- | ---: |
| Agent 事件 | `AgentEvent` | 5 |
| Workflow 事件 | `WorkflowEvent` | 8 |
| LLM 事件 | `LLMEvent` | 4 |
| Tool 事件 | `ToolEvent` | 3 |
| Store 事件 | `StoreEvent` | 5 |
| Memory 事件 | `MemoryEvent` | 5 |
| Session / Checkpoint 事件 | `SessionEvent` | 11 |
| Context 事件 | `ContextEvent` | 3 |
| Retrieval 事件 | `RetrievalEvent` | 3 |
| Performance 事件 | `PerformanceEvent` | 1 |
| 用户交互事件 | `UserInteractionEvent` | 2 |
| System 事件 | `SystemEvent` | 3 |
| SysOperation 事件 | `SysOperationEvent` | 4 |
| Graph 事件 | `GraphEvent` | 24 |
| Runner / ResourceManager 事件 | `RunnerEvent` | 8 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static void register(String eventTypeKey, Supplier<? extends BaseLogEvent> factory)` | 注册自定义字符串事件类型；若与内建 `LogEventType.value` 冲突则抛出 `IllegalArgumentException`。 |
| `public static boolean unregister(String eventTypeKey)` | 注销自定义事件类型。 |
| `public static Supplier<? extends BaseLogEvent> getFactory(LogEventType eventType)` | 按枚举值读取事件工厂；优先级为自定义注册表，再到静态映射，最后退回 `BaseLogEvent::new`。 |
| `public static Supplier<? extends BaseLogEvent> getFactory(String eventTypeKey)` | 按字符串事件键读取事件工厂；未知键退回 `BaseLogEvent::new`。 |
| `public static BaseLogEvent createEvent(LogEventType eventType)` | 创建内建事件对象，并写入 `eventType`。 |
| `public static BaseLogEvent createEvent(String eventTypeKey)` | 优先按字符串匹配内建枚举，否则走自定义工厂。 |
| `public static BaseLogEvent createEvent(LogEventType eventType, Map<String, Object> properties)` | 创建事件并通过反射 setter 批量填充属性。 |
| `public static boolean validateEvent(BaseLogEvent event)` | 校验事件是否具备合法 `eventId/eventType/logLevel/moduleType`。 |

## 说明

- `createEvent(LogEventType, Map<String, Object>)` 在探测到 `StreamEvent` 且属性中含有 `workflowId`、`componentId`、`componentTypeStr` 时，会自动升级为 `WorkflowStreamEvent`。
- 反射填充属性时，未匹配到 setter 或类型不兼容的字段会被忽略，并通过 JUL `Logger` 打告警。
- `StructuredLogEventTest` 覆盖了静态映射、自定义字符串注册、冲突键拒绝、重新注册覆盖、注销回退，以及 `validateEvent()` 的关键行为。
