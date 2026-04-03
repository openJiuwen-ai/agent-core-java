# com.openjiuwen.core.runner.base.Error

## class Error

```java
public final class Error<T> implements Result<T>
```

Represents a failed operation result following the Result pattern.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `error` | `Exception` | `-` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public Error(Exception error)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public boolean isOk()` | - |
| `public boolean isError()` | - |
| `public T getValue()` | - |
| `public Exception getError()` | - |
| `public String toString()` | - |

## 相关测试

- `CallbackChainTest`
- `CallbackFrameworkTest`
- `MessageQueueInMemoryTest`
