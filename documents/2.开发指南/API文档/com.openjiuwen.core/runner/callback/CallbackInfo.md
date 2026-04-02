# com.openjiuwen.core.runner.callback.CallbackInfo

## class CallbackInfo

```java
public class CallbackInfo
```

`CallbackInfo` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `callback` | `Function<Map<String, Object>, Object>` | `-` | The callback function. Accepts keyword args map, returns result. |
| `priority` | `int` | `0` | Execution priority (higher executes first). |
| `once` | `boolean` | `false` | Whether callback should execute only once. |
| `enabled` | `boolean` | `true` | Whether callback is currently enabled. |
| `namespace` | `String` | `"default"` | Namespace for grouping callbacks. |
| `tags` | `Set<String>` | `new HashSet<>()` | Set of tags for filtering. |
| `maxRetries` | `int` | `0` | Maximum retry attempts on failure. |
| `retryDelay` | `double` | `0.0` | Delay between retries in seconds. |
| `timeout` | `Double` | `-` | Execution timeout in seconds. |
| `createdAt` | `double` | `System.currentTimeMillis() / 1000.0` | Timestamp when callback was registered (epoch seconds). |
| `callbackName` | `String` | `-` | Name of the callback for logging purposes. |

## 方法

| Signature | Description |
| --- | --- |
| `public String getCallbackDisplayName()` | Get the callback name for logging/metrics purposes. |

## 相关测试

- `CallbackChainTest`
- `CallbackFiltersTest`
- `CallbackModelsTest`
