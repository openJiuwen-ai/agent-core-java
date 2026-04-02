# com.openjiuwen.core.common.constants.TaskType

## enum TaskType

```java
public enum TaskType
```

`TaskType` normalizes the task-routing identifiers used by agent execution.

## Enum Values

| Value | Serialized Value | Description |
| --- | --- | --- |
| `PLUGIN` | `"plugin"` | Plugin-backed task. |
| `WORKFLOW` | `"workflow"` | Workflow-backed task. |
| `MCP` | `"mcp"` | MCP-backed task. |
| `UNDEFINED` | `"undefined"` | Fallback constant returned when the input token is unknown. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the serialized task token. |
| `public static TaskType fromValue(String value)` | Resolve an exact string token to the matching enum constant, or return `UNDEFINED` when there is no match. |
