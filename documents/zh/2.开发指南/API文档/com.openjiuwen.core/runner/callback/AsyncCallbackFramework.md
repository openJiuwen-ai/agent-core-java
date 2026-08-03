# com.openjiuwen.core.runner.callback.AsyncCallbackFramework

## class AsyncCallbackFramework

```java
public class AsyncCallbackFramework implements DecoratorFramework
```

`AsyncCallbackFramework` 是 Java 0.1.14 Runner 级事件回调框架主实现，对应 Python 文档中的 `AsyncCallbackFramework`。它支持优先级、一次性回调、命名空间、标签、过滤器、链式回调、回滚、错误处理、重试、超时、metrics、hooks、延迟触发、并行触发和事件历史。

## Constructors

| 构造函数 | 说明 |
| --- | --- |
| `AsyncCallbackFramework()` | 默认开启 metrics 和 logging。 |
| `AsyncCallbackFramework(boolean enableMetrics, boolean enableLogging)` | 显式指定是否开启指标和日志。 |

## 注册入口

| 方法 | 说明 |
| --- | --- |
| `on(...)` | 返回装饰器风格的注册函数。 |
| `onChain(...)` | 注册链式回调，可配置 rollback / error handler。 |
| `registerSync(...)` | `DecoratorFramework` 同步注册入口。 |
| `register(...)` | 直接注册回调并返回 `CallbackInfo`。 |
| `unregister(...)` | 移除指定事件下的回调。 |
| `unregisterNamespace(...)` | 按命名空间移除回调。 |
| `unregisterByTags(...)` | 按标签移除回调。 |
| `unregisterEvent(...)` | 移除某个事件下全部回调。 |

## 触发入口

| 方法 | 说明 |
| --- | --- |
| `trigger(String event, Object[] args, Map<String, Object> kwargs)` | `DecoratorFramework` 兼容入口，内部调用 `triggerResults(...)`，不返回结果。 |
| `triggerResults(...)` | 顺序触发事件并返回回调结果列表。 |
| `triggerAsync(...)` | 异步触发并返回 `CompletableFuture`。 |
| `triggerDelayed(...)` | 延迟触发并返回 `ScheduledFuture<List<Object>>`。 |
| `triggerParallel(...)` | 用固定线程池并发执行当前事件回调。 |
| `triggerUntil(...)` | 按顺序执行直到 predicate 命中。 |
| `triggerWithTimeout(...)` | 给触发过程设置总超时。 |
| `triggerStream(...)` / `triggerGenerator(...)` | 将结果整理成 `Iterator`。 |
| `triggerTransform(...)` | 执行 transform 类型回调。 |

## 过滤、hook 和状态

- `addFilter(...)`、`addGlobalFilter(...)`、`removeFilter(...)` 管理事件过滤器。
- `addHook(...)` / `removeHook(...)` 管理生命周期 hook。
- `enableHistory(...)`、`getHistory()`、`replay(...)` 管理事件历史。
- `saveState(...)`、`loadState(...)` 持久化回调框架状态。

## 注意

- `triggerParallel(...)` 当前使用 `Executors.newFixedThreadPool(...)`，不是虚拟线程池。
- `trigger(...)` 不返回结果；文档或示例需要结果时应使用 `triggerResults(...)`。
- Runner 全局入口是 `Runner.getCallbackFramework()`，也可读静态字段 `Runner.callbackFramework`。
