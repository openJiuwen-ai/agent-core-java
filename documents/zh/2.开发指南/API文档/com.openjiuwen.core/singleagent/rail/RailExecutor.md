# com.openjiuwen.core.singleagent.rail.RailExecutor

## 类 RailExecutor

```java
public final class RailExecutor
```

包装 rail 生命周期事件并处理重试逻辑的执行工具。

## 方法

| 签名 | 说明 |
|---|---|
| `public static <T> T execute( AgentCallbackContext ctx, AgentCallbackEvent before, AgentCallbackEvent after, AgentCallbackEvent onException, RailBody<T> body )` | 在 `before` / `after` / `onException` 生命周期事件包裹下执行一个可调用体。 |

## 说明

- 相关测试：`RailExecutorTest`。
- `after` 事件总是在 `finally` 块中触发；若 `ctx.consumeRetryRequest()` 返回 `RetryRequest`，则会按 `delaySeconds` 延迟后重试。
