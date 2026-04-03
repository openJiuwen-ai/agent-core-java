# com.openjiuwen.core.runner.callback.ValidationFilter

## class ValidationFilter

```java
public class ValidationFilter extends EventFilter
```

Filter for validating callback arguments.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `validator` | `Predicate<Map<String, Object>>` | `-` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public ValidationFilter(Predicate<Map<String, Object>> validator)` | - |
| `public ValidationFilter(Predicate<Map<String, Object>> validator, String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | - |

## 相关测试

- `CallbackFiltersTest`
- `CallbackFrameworkTest`
