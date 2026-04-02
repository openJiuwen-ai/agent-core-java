# com.openjiuwen.core.workflow.condition.ArrayCondition

## class ArrayCondition

```java
public class ArrayCondition extends Condition
```

Loop condition over array items, resolving arrays from session state via input schema.

## Fields

| Signature | Description |
| --- | --- |
| `private static final int DEFAULT_MAX_LOOP_NUMBER = 1000` | . |
| `private final Map<String, Object> arrays` | Arrays. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ArrayCondition(Map<String, Object> arrays)` | Create a new `ArrayCondition` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object doInvoke(Object inputs, BaseSession session)` | Execute `doInvoke`. |
