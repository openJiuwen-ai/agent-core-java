# com.openjiuwen.core.workflow.component.ComponentAbility

## enum ComponentAbility

```java
public enum ComponentAbility
```

Defines the execution abilities of a workflow component.

## Enum Constants

| Value | Description |
| --- | --- |
| `INVOKE` | I n v o k e. |
| `STREAM` | S t r e a m. |
| `COLLECT` | C o l l e c t. |
| `TRANSFORM` | T r a n s f o r m. |

## Fields

| Signature | Description |
| --- | --- |
| `private final String name` | Transform: consumes a stream of chunks, yields transformed chunks. |
| `private final String desc` | Desc. |

## Constructors

| Signature | Description |
| --- | --- |
| `ComponentAbility(String name, String desc)` | Create a new `ComponentAbility` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getAbilityName()` | Return the ability name. |
| `public String getDesc()` | Return the desc. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
