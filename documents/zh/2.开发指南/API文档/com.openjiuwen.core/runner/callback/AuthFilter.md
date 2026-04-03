# com.openjiuwen.core.runner.callback.AuthFilter

## class AuthFilter

```java
public class AuthFilter extends EventFilter
```

Authorization filter for role-based access control.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `requiredRole` | `String` | `-` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public AuthFilter(String requiredRole)` | - |
| `public AuthFilter(String requiredRole, String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | - |

## 相关测试

- `CallbackFiltersTest`
