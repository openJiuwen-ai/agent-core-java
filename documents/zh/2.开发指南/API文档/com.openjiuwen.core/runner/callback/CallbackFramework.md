# com.openjiuwen.core.runner.callback.CallbackFramework

## 类 CallbackFramework

```java
public class CallbackFramework
```

`CallbackFramework` 提供面向事件的回调框架，支持优先级执行、过滤器、链式回滚、指标收集、生命周期 hook、断路器、延迟触发以及事件历史记录。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `callbacks` | `Map<String, List<CallbackInfo>>` | `new ConcurrentHashMap<>()` | 按事件名保存回调列表。 |
| `chains` | `Map<String, CallbackChain>` | `new ConcurrentHashMap<>()` | 按事件名保存链式执行配置。 |
| `filters` | `Map<String, List<EventFilter>>` | `new ConcurrentHashMap<>()` | 按事件名保存事件级过滤器。 |
| `globalFilters` | `List<EventFilter>` | `Collections.synchronizedList(new ArrayList<>())` | 对全部事件生效的全局过滤器列表。 |
| `callbackFilters` | `Map<Function<Map<String, Object>, Object>, List<EventFilter>>` | `new ConcurrentHashMap<>()` | 绑定到具体回调函数的过滤器列表。 |
| `hooks` | `Map<String, Map<HookType, List<Consumer<Map<String, Object>>>>>` | `new ConcurrentHashMap<>()` | 按事件名与 `HookType` 保存生命周期 hook。 |
| `enableMetrics` | `boolean` | `-` | 是否采集回调执行指标。 |
| `metrics` | `Map<String, CallbackMetrics>` | `new ConcurrentHashMap<>()` | 回调执行指标缓存。 |
| `enableLogging` | `boolean` | `-` | 是否输出框架日志。 |
| `circuitBreakers` | `Map<String, CircuitBreakerFilter>` | `new ConcurrentHashMap<>()` | 按 `event:callback` 键保存断路器实例。 |
| `eventHistory` | `LinkedList<Map<String, Object>>` | `new LinkedList<>()` | 事件历史记录队列。 |
| `MAX_HISTORY_SIZE` | `int` | `1000` | 事件历史记录的最大保留条数。 |
| `enableHistory` | `boolean` | `false` | 是否记录事件历史。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public CallbackFramework()` | 使用默认配置创建框架，默认开启指标与日志。 |
| `public CallbackFramework(boolean enableMetrics, boolean enableLogging)` | 按显式配置决定是否开启指标与日志。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, List<CallbackInfo>> getCallbacks()` | 返回按事件名组织的回调列表映射。 |
| `public Map<String, CallbackChain> getChains()` | 返回按事件名组织的 `CallbackChain` 映射。 |
| `public Map<String, CircuitBreakerFilter> getCircuitBreakers()` | 返回当前已注册的断路器映射。 |
| `public Map<Function<Map<String, Object>, Object>, List<EventFilter>> getCallbackFilters()` | 返回绑定到具体回调函数的过滤器映射。 |
| `public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, int priority, boolean once, String namespace, Set<String> tags, List<EventFilter> eventFilters, Consumer<ChainContext> rollbackHandler, Function<CallbackChain.ExceptionContext, Object> errorHandler, int maxRetries, double retryDelay, Double timeout, String callbackName)` | 为事件注册回调，支持优先级、单次执行、命名空间、标签、过滤器、回滚处理、错误处理、重试、超时与回调名。 |
| `public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, int priority, String callbackName)` | 以简化参数注册回调，并显式指定优先级与回调名。 |
| `public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, String callbackName)` | 使用默认优先级 `0` 注册回调。 |
| `public CallbackInfo registerSync(String event, Function<Map<String, Object>, Object> callback, int priority, boolean once, String namespace, Set<String> tags, List<EventFilter> eventFilters, Consumer<ChainContext> rollbackHandler, Function<CallbackChain.ExceptionContext, Object> errorHandler, int maxRetries, double retryDelay, Double timeout, String callbackName)` | 以同步入口调用完整注册逻辑，参数语义与 `register(...)` 一致。 |
| `public void unregister(String event, Function<Map<String, Object>, Object> callback)` | 从指定事件移除某个回调，并同步清理其过滤器与链式配置。 |
| `public void unregisterNamespace(String namespace)` | 移除指定命名空间下的全部回调。 |
| `public void unregisterByTags(Set<String> tags)` | 移除与给定标签集合存在交集的回调。 |
| `public void unregisterEvent(String event)` | 移除某个事件的全部回调、链式配置、hook 与过滤器。 |
| `public List<Object> trigger(String event, Object[] args, Map<String, Object> kwargs)` | 触发事件并顺序执行可用回调，返回各回调的结果列表。 |
| `public List<Object> trigger(String event, Map<String, Object> kwargs)` | 使用空 `args` 触发事件，并传入给定 `kwargs`。 |
| `public List<Object> trigger(String event)` | 使用空 `args` 与空 `kwargs` 触发事件。 |
| `public ChainResult triggerChain(String event, Object[] args, Map<String, Object> kwargs)` | 以 `CallbackChain` 方式执行事件回调，并返回链式执行结果。 |
| `public List<Object> triggerParallel(String event, Object[] args, Map<String, Object> kwargs)` | 并发执行事件回调，并返回成功完成的结果列表。 |
| `public Object triggerUntil(String event, Predicate<Object> condition, Object[] args, Map<String, Object> kwargs)` | 依次执行回调，返回首个满足 `condition` 的结果；若没有命中则返回 `null`。 |
| `public List<Object> triggerWithTimeout(String event, double timeoutSeconds, Object[] args, Map<String, Object> kwargs)` | 在总超时时间内触发事件；超时或执行异常时返回空列表。 |
| `public Iterator<Object> triggerStream(String event, Iterator<?> inputStream, Object[] args, Map<String, Object> kwargs)` | 对输入迭代器的每一项触发一次事件，并返回聚合后的结果迭代器。 |
| `public ScheduledFuture<List<Object>> triggerDelayed(String event, double delaySeconds, Object[] args, Map<String, Object> kwargs)` | 延迟指定秒数后触发事件，并返回可取消的 `ScheduledFuture`。 |
| `public Iterator<Object> triggerGenerator(String event, Object[] args, Map<String, Object> kwargs)` | 触发事件后把各回调结果展平成迭代器；若单个回调返回 `Iterable` 或 `Iterator`，则逐项产出其中元素。 |
| `public void addFilter(String event, EventFilter filter)` | 为指定事件添加过滤器。 |
| `public void addGlobalFilter(EventFilter filter)` | 为全部事件添加全局过滤器。 |
| `public void addCircuitBreaker(String event, CallbackInfo callback, int failureThreshold, double timeout)` | 为指定回调添加 `CircuitBreakerFilter` 保护，并同步注册为事件过滤器。 |
| `public void addHook(String event, HookType hookType, Consumer<Map<String, Object>> hook)` | 为事件添加生命周期 hook；hook 接收包含 `_args` 的参数映射。 |
| `public Map<String, Map<String, Object>> getMetrics(String event, String callback)` | 按事件名与回调名筛选执行指标映射。 |
| `public Map<String, Map<String, Object>> getMetrics()` | 返回全部执行指标。 |
| `public void resetMetrics()` | 清空全部执行指标。 |
| `public List<Map<String, Object>> getSlowCallbacks(double threshold)` | 返回平均执行时间高于阈值的回调信息列表。 |
| `public void enableEventHistory(boolean enabled)` | 开启或关闭事件历史记录。 |
| `public List<Map<String, Object>> getEventHistory(String event, Long since)` | 按事件名与起始时间筛选历史事件记录。 |
| `public void replayEvents(Long since)` | 重放指定时间之后记录的历史事件。 |
| `public void saveState(String filepath)` | 将回调元数据、指标与历史记录写入 JSON 文件；不保存回调函数本体。 |
| `public List<String> listEvents(String namespace)` | 列出已注册事件；传入命名空间时只返回该命名空间下可见的事件。 |
| `public List<Map<String, Object>> listCallbacks(String event)` | 列出某个事件下回调的名称、优先级、标签、超时等元数据。 |
| `public Map<String, Object> getStatistics()` | 返回事件数、回调数、命名空间、过滤器数、链式配置数、历史记录数与指标条目数等统计信息。 |
| `public CallbackInfo on(String event, Function<Map<String, Object>, Object> callback, String callbackName)` | 以 DSL 风格注册回调，并返回对应的 `CallbackInfo`。 |
| `public CallbackInfo on(String event, Function<Map<String, Object>, Object> callback, int priority, boolean once, String namespace, Set<String> tags, List<EventFilter> eventFilters, Consumer<ChainContext> rollbackHandler, Function<CallbackChain.ExceptionContext, Object> errorHandler, int maxRetries, double retryDelay, Double timeout, String callbackName)` | 以完整参数的 DSL 风格注册回调。 |
| `public Function<Map<String, Object>, Object> triggerOnCall( String event, Function<Map<String, Object>, Object> wrapped, boolean passResult, boolean passArgs)` | 包装函数，在调用前触发事件；`passResult` 为 `true` 时会在原函数返回后再次携带结果触发同一事件。 |
| `public Function<Map<String, Object>, Object> emits( String event, Function<Map<String, Object>, Object> wrapped, String resultKey, boolean includeArgs)` | 包装函数，在原函数返回后以结果触发事件。 |
| `public Function<Map<String, Object>, Object> emitsStream( String event, Function<Map<String, Object>, Object> wrapped, String itemKey)` | 包装返回 `Iterator` 或 `Iterable` 的函数，并为每个产出项触发一次事件。 |
| `public Function<Map<String, Object>, Object> emitAround( String beforeEvent, String afterEvent, Function<Map<String, Object>, Object> wrapped, boolean passArgs, boolean passResult, String onErrorEvent)` | 包装函数，在执行前、执行后以及可选的异常路径上分别触发事件。 |
| `public Function<Map<String, Object>, Object> transformIo( Function<Map<String, Object>, Object> wrapped, Function<Map<String, Object>, Map<String, Object>> inputTransform, Function<Object, Object> outputTransform)` | 使用输入转换函数与输出转换函数包装原函数。 |
| `public Function<Map<String, Object>, Object> transformIoByEvents( Function<Map<String, Object>, Object> wrapped, String inputEvent, String outputEvent, String resultKey)` | 通过输入事件与输出事件包装原函数；输入事件最后一个 `Map` 结果会作为新的 `kwargs`，输出事件最后一个结果会作为最终返回值。 |

## 说明

- 相关测试：`CallbackFrameworkTest`、`RunnerTest`。
