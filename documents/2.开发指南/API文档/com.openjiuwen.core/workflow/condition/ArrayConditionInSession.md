# com.openjiuwen.core.workflow.condition.ArrayConditionInSession

## class ArrayConditionInSession

```java
public class ArrayConditionInSession extends Condition
```

Loop condition over array items already stored in session (not from schema).

## Fields

| Signature | Description |
| --- | --- |
| `private static final int DEFAULT_MAX_LOOP_NUMBER = 1000` | . |
| `private final Map<String, Object> arrays` | Arrays. |
| `private final int minLength` | Min length. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ArrayConditionInSession(Map<String, Object> arrays)` | Create a new `ArrayConditionInSession` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object doInvoke(Object inputs, BaseSession session)` | Execute `doInvoke`. |
| `private static int checkArrays(Map<String, Object> arrays)` | Execute `checkArrays`. |
