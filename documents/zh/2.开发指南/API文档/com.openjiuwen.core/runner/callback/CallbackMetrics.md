# com.openjiuwen.core.runner.callback.CallbackMetrics

## class CallbackMetrics

```java
public class CallbackMetrics
```

`CallbackMetrics` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `callCount` | `int` | `0` | - |
| `totalTime` | `double` | `0.0` | - |
| `minTime` | `double` | `Double.MAX_VALUE` | - |
| `maxTime` | `double` | `0.0` | - |
| `errorCount` | `int` | `0` | - |
| `lastCallTime` | `Double` | `-` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public synchronized void update(double executionTime, boolean isError)` | Update metrics with new execution data. |
| `public double getAvgTime()` | Calculate average execution time. |
| `public Map<String, Object> toMap()` | Convert metrics to dictionary format. |

## 相关测试

- `CallbackModelsTest`
