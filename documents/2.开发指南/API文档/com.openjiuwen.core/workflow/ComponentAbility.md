# com.openjiuwen.core.workflow.ComponentAbility

## enum ComponentAbility

```java
public enum ComponentAbility
```

Re-export of `com.openjiuwen.core.workflow.component.ComponentAbility`. Provides the enum values at the top-level workflow package for test compatibility.

## Enum Constants

| Value | Description |
| --- | --- |
| `INVOKE` | I n v o k e. |
| `STREAM` | S t r e a m. |
| `COLLECT` | C o l l e c t. |
| `TRANSFORM` | T r a n s f o r m. |

## Methods

| Signature | Description |
| --- | --- |
| `public com.openjiuwen.core.workflow.component.ComponentAbility toInternal()` | Convert to the internal `com.openjiuwen.core.workflow.component.ComponentAbility`. |
| `public static ComponentAbility fromInternal(com.openjiuwen.core.workflow.component.ComponentAbility internal)` | Convert from the internal enum. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
