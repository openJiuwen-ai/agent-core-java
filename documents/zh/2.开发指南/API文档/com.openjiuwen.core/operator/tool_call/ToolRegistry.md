# com.openjiuwen.core.operator.tool_call.ToolRegistry

## interface ToolRegistry

```java
public interface ToolRegistry
```

`ToolRegistry` 定义了 `ToolCallOperator` 暴露 `tool_description` tunable 所需的最小工具注册表契约。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getToolDefs()` | `List<Map<String, Object>>` | 默认返回空列表，可用于暴露工具定义元数据。 |
| `getTools()` | `Map<String, Tool>` | 默认返回空映射，可用于暴露已注册工具实例。 |
| `setToolDescription(String toolName, String description)` | `void` | 更新指定工具的描述文本，供 `ToolCallOperator.setParameter("tool_description", ...)` 调用。 |

## 说明

- 只有向 `ToolCallOperator` 注入该接口实现后，算子才会在 `getTunables()` 中暴露 `tool_description`。
