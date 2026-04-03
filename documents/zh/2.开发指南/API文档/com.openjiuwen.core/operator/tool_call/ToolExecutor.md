# com.openjiuwen.core.operator.tool_call.ToolExecutor

## interface ToolExecutor

```java
public interface ToolExecutor
```

`ToolExecutor` 是 `ToolCallOperator` 路由模式下的执行器接口，用于逐个处理批量 `tool_calls`。

## 核心方法

### `ToolExecutionResult execute(Object toolCall, Session session) throws Exception`

执行单个工具调用请求。

**参数**

- `toolCall`: 来自 `inputs["tool_calls"]` 的单个调用描述对象。
- `session`: 当前会话对象，可用于读写上下文。

**返回**

- `ToolExecutionResult`: 单次工具调用的结果包装。
