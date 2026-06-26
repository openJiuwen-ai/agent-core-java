# com.openjiuwen.core.operator.tool_call.ToolExecutionResult

## record ToolExecutionResult

```java
public record ToolExecutionResult(Object result, ToolMessage toolMessage)
```

`ToolExecutionResult` 封装路由模式下单个工具调用的业务结果，以及可选的 `ToolMessage` 响应消息。

## 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `result` | `Object` | 工具执行后的业务结果对象。 |
| `toolMessage` | `ToolMessage` | 可选的工具消息响应，可为 `null`。 |
