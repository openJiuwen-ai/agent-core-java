# com.openjiuwen.core.memory.manage.update.CheckResult

## enum CheckResult

```java
public enum CheckResult
```

Result of memory check operation.

## Enum Values

| Value | Description |
| --- | --- |
| `REDUNDANT` | redundant. |
| `CONFLICTING` | conflicting. |
| `NONE` | none. |

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `value` | `String` | value. |

## Constructors

| Signature | Description |
| --- | --- |
| `CheckResult(String value)` | Create a new `CheckResult` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Execute `getValue`. |
| `public static CheckResult fromValue(String value)` | Execute `fromValue`. |
