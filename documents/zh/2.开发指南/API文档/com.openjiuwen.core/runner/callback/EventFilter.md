# com.openjiuwen.core.runner.callback.EventFilter

## class EventFilter

```java
public class EventFilter
```

Base class for event filters.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | `String` | `-` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public EventFilter()` | - |
| `public EventFilter(String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public String getName()` | - |
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | Filter logic to execute before callback. Override this method to implement custom filtering logic. |

## 相关测试

- `CallbackFiltersTest`
