# com.openjiuwen.core.controller.legacy.event.Event

## class Event

```java
public class Event
```

`Event` 是旧版控制器的兼容事件模型，使用 Lombok builder 管理事件类型、优先级、来源、内容、上下文和元数据。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `eventId` | `String` | 随机 UUID | 事件 ID。 |
| `eventType` | `EventType` | `USER_INPUT` | 事件类型。 |
| `priority` | `EventPriority` | `NORMAL` | 事件优先级。 |
| `source` | `EventSource` | `("unknown", SYSTEM, null)` | 事件来源。 |
| `content` | `EventContent` | 新实例 | 事件载荷。 |
| `context` | `EventContext` | 新实例 | 关联上下文。 |
| `createdAt` | `Instant` | `Instant.now()` | 创建时间。 |
| `metadata` | `Map<String, Object>` | 空映射 | 补充元数据。 |
| `receiverId` | `String` | `null` | 定向接收者。 |
| `customEventType` | `String` | `null` | 自定义事件类型。 |

## 主要工厂方法

| 方法 | 说明 |
|---|---|
| `createUserEvent(...)` | 创建用户输入事件。 |
| `createTaskCompleted(...)` | 创建任务完成事件。 |
| `createTaskInterrupted(...)` | 创建任务中断事件。 |
| `createErrorEvent(...)` | 创建错误事件。 |
| `createInfoEvent(...)` | 创建信息事件。 |
| `createAgentResponse(...)` | 创建 Agent 响应事件。 |
| `createAgentHandoff(...)` | 创建 Agent 交接事件。 |
| `toDict()` | 把事件转换成普通映射。 |

## 嵌套类型

| 类型 | 说明 |
|---|---|
| `EventType` | 事件类别枚举，覆盖用户输入、Agent 响应、Agent 交接、任务完成、任务中断、错误和信息事件。 |
| `EventPriority` | 事件优先级枚举，提供低、普通、高和紧急四档。 |
| `SourceType` | 事件来源类别枚举，用于标记用户、Agent、任务、工作流或系统来源。 |
| `EventSource` | 记录会话标识、来源类别和用户标识。 |
| `EventContent` | 承载查询文本、交互输入、流式数据、任务结果和扩展字段，并提供聚合文本能力。 |
| `EventContext` | 记录关联 ID、会话 ID、任务 ID 和工作流 ID。 |

## 说明

- `setCorrelation()`、`setConversation()`、`isFromUser()`、`isFromAgent()`、`isTaskRelated()`、`isWorkflowRelated()` 为旧版控制器提供了便捷判断方法。
- 该页面不再把嵌套类型拆成单独文件，统一保留在宿主页中说明。
