# com.openjiuwen.core.operator.memory_call.MemoryOperation

## interface MemoryOperation

```java
public interface MemoryOperation
```

`MemoryOperation` 定义了 `MemoryCallOperator` 依赖的最小 memory 契约，既支持普通调用，也支持可选的流式调用。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `invoke(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Object` | 执行一次记忆调用，必须由实现类提供。 |
| `supportsStream()` | `boolean` | 默认返回 `false`，表示未声明流式能力。 |
| `stream(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Iterator<Object>` | 默认直接抛出 `UnsupportedOperationException("memory stream not implemented")`。 |

## 说明

- `MemoryCallOperator` 不会先检查 `supportsStream()`，而是直接调用 `stream()`；因此实现流式能力时需要同时覆写 `stream()`。
