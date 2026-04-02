# com.openjiuwen.core.runner.callback.CircuitBreakerFilter

## class CircuitBreakerFilter

```java
public class CircuitBreakerFilter extends EventFilter
```

Circuit breaker pattern implementation.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `failureThreshold` | `int` | `-` | - |
| `timeout` | `double` | `-` | - |
| `failures` | `Map<String, Integer>` | `new ConcurrentHashMap<>()` | - |
| `lastFailureTime` | `Map<String, Double>` | `new ConcurrentHashMap<>()` | - |
| `isOpen` | `Map<String, Boolean>` | `new ConcurrentHashMap<>()` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public CircuitBreakerFilter()` | - |
| `public CircuitBreakerFilter(int failureThreshold, double timeout)` | - |
| `public CircuitBreakerFilter(int failureThreshold, double timeout, String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public Map<String, Integer> getFailures()` | - |
| `public synchronized FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | - |
| `public synchronized void recordSuccess(String event, CallbackInfo callback)` | Record successful execution. |
| `public synchronized void recordFailure(String event, CallbackInfo callback)` | Record failed execution and potentially open circuit. |

## 相关测试

- `CallbackFiltersTest`
