# callback

`com.openjiuwen.core.runner.callback` 提供事件驱动回调框架、过滤器、链式执行和指标统计能力。

## 类型

| Type | Description |
| --- | --- |
| [`AuthFilter`](callback/AuthFilter.md) | Authorization filter for role-based access control. |
| [`CallbackChain`](callback/CallbackChain.md) | Manages sequential execution of callbacks with rollback support. |
| [`CallbackFramework`](callback/CallbackFramework.md) | `CallbackFramework` 提供生产级事件回调框架，支持优先级执行、过滤器、回滚链、metrics、hooks、circuit breaker 和 history。 |
| [`CallbackInfo`](callback/CallbackInfo.md) | `CallbackInfo` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。 |
| [`CallbackMetrics`](callback/CallbackMetrics.md) | `CallbackMetrics` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。 |
| [`ChainAction`](callback/ChainAction.md) | Actions that callbacks can return to control chain execution. |
| [`ChainContext`](callback/ChainContext.md) | `ChainContext` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。 |
| [`ChainResult`](callback/ChainResult.md) | `ChainResult` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。 |
| [`CircuitBreakerFilter`](callback/CircuitBreakerFilter.md) | Circuit breaker pattern implementation. |
| [`ConditionalFilter`](callback/ConditionalFilter.md) | Conditional filter based on custom predicate. |
| [`EventFilter`](callback/EventFilter.md) | Base class for event filters. |
| [`FilterAction`](callback/FilterAction.md) | Actions that filters can return to control callback execution. |
| [`FilterResult`](callback/FilterResult.md) | `FilterResult` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。 |
| [`HookType`](callback/HookType.md) | Types of hooks that can be registered for lifecycle events. |
| [`LoggingFilter`](callback/LoggingFilter.md) | Filter for logging callback execution. |
| [`ParamModifyFilter`](callback/ParamModifyFilter.md) | Filter for modifying callback arguments. |
| [`RateLimitFilter`](callback/RateLimitFilter.md) | Filter to limit callback execution rate. |
| [`ValidationFilter`](callback/ValidationFilter.md) | Filter for validating callback arguments. |
