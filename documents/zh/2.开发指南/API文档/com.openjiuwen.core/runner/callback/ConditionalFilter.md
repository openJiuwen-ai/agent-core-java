# com.openjiuwen.core.runner.callback.ConditionalFilter

## class ConditionalFilter

```java
public class ConditionalFilter extends EventFilter
```

Conditional filter based on custom predicate.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `condition` | `ConditionPredicate` | `-` | - |
| `actionOnFalse` | `FilterAction` | `-` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public ConditionalFilter(ConditionPredicate condition)` | - |
| `public ConditionalFilter(ConditionPredicate condition, FilterAction actionOnFalse, String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | - |

## 嵌套类型

- `ConditionPredicate`: Predicate function: (event, callback, args, kwargs) -> boolean

## 相关测试

- `CallbackFiltersTest`
