# com.openjiuwen.core.operator.tool_call.ToolCallOperator

## class ToolCallOperator

```java
public class ToolCallOperator extends Operator
```

`ToolCallOperator` 是工具调用算子，既可以直连单个 `Tool`，也可以在路由模式下批量执行 `inputs["tool_calls"]`。

## 构造方法

### `public ToolCallOperator(Tool tool, String toolCallId, ToolExecutor toolExecutor, ToolRegistry toolRegistry)`

创建完整形态的工具调用算子。

**说明**

- `toolCallId` 为 `null` 时会回退到 `tool_call`。
- `toolExecutor` 用于路由模式批处理。
- `toolRegistry` 用于暴露和更新 `tool_description` tunable。

### `public ToolCallOperator(Tool tool)`

创建直连单个 `Tool` 的默认实例。

### `public ToolCallOperator(ToolExecutor toolExecutor)`

创建只支持路由模式的实例。

### `public ToolCallOperator(Tool tool, ToolRegistry toolRegistry)`

创建直连模式且可更新工具描述的实例。

### `public ToolCallOperator()`

创建空配置实例；未注入 `tool` 且未满足路由模式时调用 `invoke()` 会失败。

## 可调参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `tool_description` | `text` | 仅在配置 `ToolRegistry` 时暴露；值应为 `Map<toolName, description>`。 |

## 状态快照

| 字段 | 类型 | 说明 |
|---|---|---|
| `enabled` | `boolean` | 当前是否允许执行工具调用。 |
| `max_retries` | `int` | 最大重试次数，限制在 `[0, 5]`。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getOperatorId()` | `String` | 返回当前算子 ID。 |
| `setParameter(String target, Object value)` | `void` | 在 `target = "tool_description"` 且 `value` 为映射时，通过 `ToolRegistry.setToolDescription(...)` 更新描述。 |
| `loadState(Map<String, Object> state)` | `void` | 恢复 `enabled` 与 `max_retries` 状态。 |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `Object` | 根据输入自动在“路由模式”和“直连模式”之间切换，并在执行前后维护 operator context。 |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<Object>` | 仅在配置了 `tool` 时可用；委托给 `Tool.stream(...)` 并自动清理上下文。 |

## 调用模式

### 路由模式

- 当 `inputs.get("tool_calls")` 是 `List<?>` 且 `toolExecutor` 非空时启用。
- 算子会为每个 `toolCall` 执行 `ToolExecutor.execute(toolCall, session)`，并在 `result() == null` 时按 `max_retries` 继续重试。
- 返回值是 `List<ToolExecutionResult>`。

### 直连模式

- 当未触发路由模式时，算子要求 `tool` 非空。
- 调用 `tool.invoke(inputs, kwargs)`；若抛出异常，会按 `max_retries` 重试。

## 说明

- `enabled = false` 时，无论路由模式还是直连模式都会抛出 `IllegalStateException("ToolCallOperator disabled: ...")`。
- `tool` 为空时，`stream()` 会抛出 `UnsupportedOperationException("tool stream not implemented")`。
- `ToolCallOperatorTest` 覆盖了 registry 更新、路由模式返回值、流提前关闭后的上下文清理以及无效参数忽略行为。
