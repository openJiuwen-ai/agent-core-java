# com.openjiuwen.core.runner.callback.RateLimitFilter

## class RateLimitFilter

```java
public class RateLimitFilter extends EventFilter
```

Filter to limit callback execution rate.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `maxCalls` | `int` | `-` | - |
| `timeWindow` | `double` | `-` | - |
| `callTimes` | `Map<String, Deque<Double>>` | `new ConcurrentHashMap<>()` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public RateLimitFilter(int maxCalls, double timeWindow)` | - |
| `public RateLimitFilter(int maxCalls, double timeWindow, String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public synchronized FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | - |

## 相关测试

- `CallbackFiltersTest`
- `CallbackFrameworkTest`
