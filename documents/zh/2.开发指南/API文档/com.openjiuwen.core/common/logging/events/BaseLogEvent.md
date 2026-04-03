# com.openjiuwen.core.common.logging.events.BaseLogEvent

## 类 BaseLogEvent

```java
public class BaseLogEvent
```

`BaseLogEvent` 是所有结构化日志事件的公共基类，统一承载事件标识、级别、模块上下文、错误信息和扩展 `metadata`，并负责把对象拍平成适合日志输出的 `Map<String, Object>`。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `eventId` | `String` | `UUID.randomUUID().toString()` | 唯一事件 ID。 |
| `eventType` | `LogEventType` | - | 事件类型。 |
| `logLevel` | `LogLevel` | `LogLevel.INFO` | 日志等级。 |
| `timestamp` | `Instant` | `Instant.now()` | 事件创建时间。 |
| `moduleType` | `ModuleType` | `ModuleType.SYSTEM` | 模块类别。 |
| `moduleId` | `String` | - | 模块实例 ID。 |
| `moduleName` | `String` | - | 模块名称。 |
| `sessionId` | `String` | - | 会话 ID。 |
| `conversationId` | `String` | - | 会话内对话 ID。 |
| `traceId` | `String` | - | 链路追踪 ID。 |
| `correlationId` | `String` | - | 关联事件 ID。 |
| `parentEventId` | `String` | - | 父事件 ID。 |
| `status` | `EventStatus` | `EventStatus.SUCCESS` | 事件状态。 |
| `errorCode` | `String` | - | 错误码。 |
| `errorMessage` | `String` | - | 错误消息。 |
| `message` | `String` | - | 主要日志消息。 |
| `stacktrace` | `String` | - | 栈追踪文本。 |
| `exceptionDetail` | `String` | - | 额外异常细节；序列化时写入 `exception` 键。 |
| `metadata` | `Map<String, Object>` | `new LinkedHashMap<>()` | 额外扩展字段；非空时才写入输出映射。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BaseLogEvent()` | 初始化 `eventId`、`timestamp`、`logLevel`、`moduleType`、`status` 和空 `metadata`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> toMap()` | 输出扁平化、snake_case 键名的结构化映射。 |

## 说明

- 该类型使用 Lombok 的 `@Data` 与 `@SuperBuilder` 生成 getter/setter 和 builder。
- `toMap()` 会把 `eventType/logLevel/moduleType/status` 写成它们各自的 `value` 字符串，并在 `metadata` 非空时额外输出 `metadata`。
- 子类通过覆写受保护扩展点 `addFieldsToMap(Map<String, Object> map)` 追加自己的序列化字段；基类内部还提供 `putIfNotNull(...)` 辅助方法，保证空值不会写入输出映射。
- `StructuredLogEventTest` 覆盖了 `eventId`、`logLevel`、`status`、`moduleType`、`timestamp` 和 `metadata` 的默认值，以及 `toMap()` 的 JSON 兼容输出。
