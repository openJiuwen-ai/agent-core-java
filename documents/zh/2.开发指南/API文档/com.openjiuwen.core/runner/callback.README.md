# callback

`com.openjiuwen.core.runner.callback` 提供 Runner 级全局事件回调框架。Java 0.1.14 的真实主类是 `AsyncCallbackFramework`，它实现 `DecoratorFramework`，并承载注册、触发、过滤器、链式回滚、指标、hooks、断路器、延迟触发和历史记录等能力。

## Types

| 类型 | 说明 |
| --- | --- |
| [`AsyncCallbackFramework`](callback/AsyncCallbackFramework.md) | Runner 全局回调框架主实现。 |
| [`CallbackInfo`](callback/CallbackInfo.md) | 单个回调的元数据。 |
| [`CallbackChain`](callback/CallbackChain.md) | 链式回调和回滚执行。 |
| [`CallbackMetrics`](callback/CallbackMetrics.md) | 调用次数、耗时、错误率等统计。 |
| [`EventFilter`](callback/EventFilter.md) | 事件过滤器协议。 |
| [`FilterResult`](callback/FilterResult.md) | 过滤器判定结果。 |
| [`FilterAction`](callback/FilterAction.md) | `CONTINUE`、`STOP`、`SKIP`、`MODIFY`。 |
| [`HookType`](callback/HookType.md) | `BEFORE`、`AFTER`、`ERROR`、`CLEANUP`。 |
| [`AuthFilter`](callback/AuthFilter.md) | 鉴权过滤器。 |
| [`RateLimitFilter`](callback/RateLimitFilter.md) | 限流过滤器。 |
| [`CircuitBreakerFilter`](callback/CircuitBreakerFilter.md) | 断路器过滤器。 |
| [`ValidationFilter`](callback/ValidationFilter.md) | 参数校验过滤器。 |

## Notes

- 旧文档中的 `CallbackFramework` 类名是过期名称；当前源码没有 `CallbackFramework.java`。
- 常用入口是 `Runner.getCallbackFramework()`，返回 `AsyncCallbackFramework`。
- `trigger(...)` 是 `DecoratorFramework` 兼容入口，不返回结果；需要结果时使用 `triggerResults(...)`。
- `triggerParallel(...)` 当前使用固定线程池，不是虚拟线程池。
