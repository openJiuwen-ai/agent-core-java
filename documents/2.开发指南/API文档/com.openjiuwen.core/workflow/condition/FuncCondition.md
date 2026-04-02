# com.openjiuwen.core.workflow.condition.FuncCondition

## class FuncCondition

```java
public class FuncCondition extends Condition
```

Condition that wraps a callable predicate.

## Fields

| Signature | Description |
| --- | --- |
| `private final BooleanSupplier func` | Func. |

## Constructors

| Signature | Description |
| --- | --- |
| `public FuncCondition(BooleanSupplier func)` | Create a new `FuncCondition` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object doInvoke(Object inputs, BaseSession session)` | Execute `doInvoke`. |
| `public Object traceInfo(BaseSession session)` | Execute `traceInfo`. |
