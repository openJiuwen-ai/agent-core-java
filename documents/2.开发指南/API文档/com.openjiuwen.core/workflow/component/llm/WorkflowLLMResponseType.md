# com.openjiuwen.core.workflow.component.llm.WorkflowLLMResponseType

## enum WorkflowLLMResponseType

```java
public enum WorkflowLLMResponseType
```

工作流 LLM 组件响应类型枚举。

枚举值 `JSON`、`MARKDOWN`、`TEXT` 分别对应底层字符串 `json`、`markdown`、`text`，用于执行分支判断。

## Enum Constants

| Value | Description |
| --- | --- |
| `JSON` | JSON 结构化响应。 |
| `MARKDOWN` | Markdown 文本响应。 |
| `TEXT` | 纯文本响应。 |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the value. |
