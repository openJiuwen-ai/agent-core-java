# com.openjiuwen.core.foundation.llm.schema.AssistantMessage

## 类 AssistantMessage

```java
public class AssistantMessage extends BaseMessage
```

表示 assistant 角色的完整消息对象。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private List<ToolCall> toolCalls` | 保存 `toolCalls` 相关状态或配置。 |
| `private UsageMetadata usageMetadata` | 保存调用用量或附加元信息。 |
| `private String finishReason` | 保存 `finishReason` 相关状态或配置。 |
| `private Object parserContent` | 保存文本或结构化内容。 |
| `private String reasoningContent` | 保存文本或结构化内容。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AssistantMessage(String content) {` | 构造 `AssistantMessage` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getRole() {` | 返回 `role` 属性。 |
| `public static List<ToolCall> convertOpenAiToolCalls(List<Map<String, Object>> rawToolCalls) {` | 执行 `convertOpenAiToolCalls` 公开能力。 |
| `public Map<String, Object> toApiFormat() {` | 执行 `toApiFormat` 公开能力。 |

## 说明

- 所有签名均以当前 Java 源码为准。
