# com.openjiuwen.core.runner.callback.ParamModifyFilter

## class ParamModifyFilter

```java
public class ParamModifyFilter extends EventFilter
```

Filter for modifying callback arguments.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `modifier` | `BiFunction<Object[], Map<String, Object>, Object[]>` | `-` | Modifier that takes (args, kwargs) and returns a two-element array: [newArgs, newKwargs]. Element [0] should be Object[] (new args), element [1] should be Map<String, Object> (new kwargs). |

## 构造方法

| Signature | Description |
| --- | --- |
| `public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier)` | - |
| `public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier, String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | - |

## 相关测试

- `CallbackFiltersTest`
