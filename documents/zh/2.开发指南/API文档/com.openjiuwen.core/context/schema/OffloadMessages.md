# com.openjiuwen.core.context.schema.OffloadMessages

## class OffloadMessages

```java
public final class OffloadMessages
```

`OffloadMessages` 是一个静态工厂容器，用于创建带有卸载句柄的消息对象。它按消息角色选择具体的卸载消息类型，并尽可能保留原消息上的附加字段。

## 嵌套类型

| 类型 | 继承关系 | 说明 |
|---|---|---|
| `OffloadUserMessage` | `UserMessage implements OffloadMixin` | 表示被卸载的用户消息。 |
| `OffloadAssistantMessage` | `AssistantMessage implements OffloadMixin` | 表示被卸载的助手消息，可保留工具调用和推理附加字段。 |
| `OffloadSystemMessage` | `SystemMessage implements OffloadMixin` | 表示被卸载的系统消息。 |
| `OffloadToolMessage` | `ToolMessage implements OffloadMixin` | 表示被卸载的工具消息，并携带卸载句柄与元数据能力。 |

## 通用字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `offloadType` | `String` | 卸载内容所在的存储类型，例如 `in_memory`。 |
| `offloadHandle` | `String` | 后续重载内容使用的唯一句柄。 |
| `metadata` | `Map<String, Object>` | 附加元数据；`getMetadata()` 会在首次访问时懒加载为空 `HashMap`。 |

## 静态工厂

### `public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle, String offloadType)`

按角色创建对应的卸载消息类型。

### `public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle, String offloadType, Map<String, Object> extraFields)`

在创建卸载消息时保留原消息上的额外字段。

**说明**

- `assistant` 角色会额外尝试保留 `tool_calls`、`usage_metadata`、`finish_reason`、`parser_content`、`reasoning_content` 和 `name`。
- `tool` 角色会额外尝试保留 `tool_call_id` 和 `name`。
- `system`、`user` 或未知角色只保留 `name`，未知角色默认回退为 `OffloadUserMessage`。

## 说明

- `OffloadMessagesTest` 覆盖了四种角色消息的创建、未知角色回退和 `metadata` 懒初始化行为。
- 所有嵌套类型都使用 Jackson 注解把 `offload_type`、`offload_handle` 暴露为 JSON 字段名。
