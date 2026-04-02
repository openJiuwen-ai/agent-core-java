# com.openjiuwen.core.workflow.condition.NumberConditionInSession

## class NumberConditionInSession

```java
public class NumberConditionInSession extends Condition
```

Loop condition based on iteration count with limit stored directly (not from schema).

## Fields

| Signature | Description |
| --- | --- |
| `private final int limit` | Limit. |

## Constructors

| Signature | Description |
| --- | --- |
| `public NumberConditionInSession(int limit)` | Create a new `NumberConditionInSession` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object doInvoke(Object inputs, BaseSession session)` | Execute `doInvoke`. |
