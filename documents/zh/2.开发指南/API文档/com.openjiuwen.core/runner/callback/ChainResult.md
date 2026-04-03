# com.openjiuwen.core.runner.callback.ChainResult

## class ChainResult

```java
public class ChainResult
```

`ChainResult` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `action` | `ChainAction` | `-` | Final action taken by the chain. |
| `result` | `Object` | `-` | Final result value. |
| `context` | `ChainContext` | `-` | The chain execution context. |
| `error` | `Exception` | `-` | Exception if chain failed. |

## 相关测试

- `CallbackChainTest`
- `CallbackFrameworkTest`
- `CallbackModelsTest`
