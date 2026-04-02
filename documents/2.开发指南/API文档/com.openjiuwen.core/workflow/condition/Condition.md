# com.openjiuwen.core.workflow.condition.Condition

## class Condition

```java
public abstract class Condition extends AtomicNode
```

Abstract condition for workflow branching and loop control.

## Fields

| Signature | Description |
| --- | --- |
| `protected Object inputSchema` | Input schema. |

## Constructors

| Signature | Description |
| --- | --- |
| `public Condition()` | Create a new `Condition` instance. |
| `public Condition(Object inputSchema)` | Create a new `Condition` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean evaluate(BaseSession session)` | Evaluate the condition against the given session. |
| `protected Object doAtomicInvoke(Map<String, Object> kwargs)` | Execute `doAtomicInvoke`. |
| `public abstract Object doInvoke(Object inputs, BaseSession session)` | Perform the condition check. |
| `public Object traceInfo(BaseSession session)` | Get trace info for this condition. |
