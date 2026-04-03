# com.openjiuwen.core.common.constants.TaskType

## enum TaskType

```java
public enum TaskType
```

`TaskType` 定义任务路由中使用的任务类型标识。

## 枚举值

| 枚举值 | 字符串值 | 说明 |
| --- | --- | --- |
| `PLUGIN` | `"plugin"` | 插件任务。 |
| `WORKFLOW` | `"workflow"` | 工作流任务。 |
| `MCP` | `"mcp"` | MCP 任务。 |
| `UNDEFINED` | `"undefined"` | 未知输入的回退值。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举的字符串值。 |
| `public static TaskType fromValue(String value)` | 解析字符串为枚举；无匹配项时返回 `UNDEFINED`。 |
