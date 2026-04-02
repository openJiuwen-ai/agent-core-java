# com.openjiuwen.core.runner.callback.ChainContext

## class ChainContext

```java
public class ChainContext
```

`ChainContext` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `event` | `String` | `-` | Name of the event being processed. |
| `initialArgs` | `Object[]` | `-` | Original positional arguments. |
| `initialKwargs` | `Map<String, Object>` | `-` | Original keyword arguments. |
| `results` | `List<Object>` | `new ArrayList<>()` | List of results from executed callbacks. |
| `metadata` | `Map<String, Object>` | `new HashMap<>()` | Arbitrary metadata for sharing data. |
| `currentIndex` | `int` | `0` | Index of currently executing callback. |
| `completed` | `boolean` | `false` | Whether chain completed successfully. |
| `rolledBack` | `boolean` | `false` | Whether chain was rolled back. |
| `startTime` | `long` | `-` | Timestamp when chain execution started (epoch millis). |

## 构造方法

| Signature | Description |
| --- | --- |
| `public ChainContext(String event, Object[] initialArgs, Map<String, Object> initialKwargs)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public Object getLastResult()` | Get the result from the previous callback. |
| `public List<Object> getAllResults()` | Get all results from executed callbacks. |
| `public void setMetadata(String key, Object value)` | Store metadata in the context. |
| `public Object getMetadata(String key, Object defaultValue)` | Retrieve metadata from the context. |
| `public double getElapsedTime()` | Calculate elapsed time since chain start. |

## 相关测试

- `CallbackChainTest`
- `CallbackModelsTest`
