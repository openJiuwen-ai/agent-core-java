# com.openjiuwen.core.workflow.condition.NumberCondition

## class NumberCondition

```java
public class NumberCondition extends Condition
```

Loop condition based on iteration count, resolving limit from input schema.

## Fields

| Signature | Description |
| --- | --- |
| `private final Object limit` | Limit. |

## Constructors

| Signature | Description |
| --- | --- |
| `public NumberCondition(Object limit)` | Create a new `NumberCondition` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object doInvoke(Object inputs, BaseSession session)` | Execute `doInvoke`. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
