# com.openjiuwen.core.runner.base.Ok

## class Ok

```java
public final class Ok<T> implements Result<T>
```

Represents a successful operation result following the Result pattern.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `value` | `T` | `-` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public Ok(T value)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public boolean isOk()` | - |
| `public boolean isError()` | - |
| `public T getValue()` | - |
| `public Exception getError()` | - |
| `public String toString()` | - |

## 相关测试

- `ResourceMgrTest`
