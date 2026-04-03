# com.openjiuwen.core.workflow.component.llm.MessageRole

## enum MessageRole

```java
public enum MessageRole
```

LLM 对话消息角色枚举。

当前实现只定义 `USER`、`ASSISTANT` 和 `FUNCTION` 三种角色，供模板构造与消息序列化逻辑统一使用。

## Enum Constants

| Value | Description |
| --- | --- |
| `USER` | 用户消息。 |
| `ASSISTANT` | 助手消息。 |
| `FUNCTION` | 函数调用相关消息。 |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the value. |
