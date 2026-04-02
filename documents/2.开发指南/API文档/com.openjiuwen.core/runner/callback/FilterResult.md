# com.openjiuwen.core.runner.callback.FilterResult

## class FilterResult

```java
public class FilterResult
```

`FilterResult` 提供 `com.openjiuwen.core.runner.callback` 范围内的运行时能力。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `action` | `FilterAction` | `-` | The action to take (CONTINUE, STOP, SKIP, MODIFY). |
| `modifiedArgs` | `Object[]` | `-` | New positional arguments if action is MODIFY. |
| `modifiedKwargs` | `Map<String, Object>` | `-` | New keyword arguments if action is MODIFY. |
| `reason` | `String` | `-` | Optional reason for the action taken. |

## 方法

| Signature | Description |
| --- | --- |
| `public static FilterResult continueResult()` | Create a CONTINUE result. |
| `public static FilterResult continueResult(Object[] args, Map<String, Object> kwargs)` | Create a CONTINUE result with modified arguments. |
| `public static FilterResult skipResult(String reason)` | Create a SKIP result with reason. |
| `public static FilterResult stopResult(String reason)` | Create a STOP result with reason. |
| `public static FilterResult modifyResult(Object[] modifiedArgs, Map<String, Object> modifiedKwargs)` | Create a MODIFY result with new arguments. |

## 相关测试

- `CallbackFiltersTest`
- `CallbackModelsTest`
