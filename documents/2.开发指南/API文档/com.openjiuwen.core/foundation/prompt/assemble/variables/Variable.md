# com.openjiuwen.core.foundation.prompt.assemble.variables.Variable

## class Variable

```java
public abstract class Variable
```

Base class for prompt template variables.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | `String` | `-` | Name. |
| `inputKeys` | `List<String>` | `-` | Input keys. |
| `value` | `Object` | `""` | Value. |

## Constructors

| Signature | Description |
| --- | --- |
| `protected Variable(String name, List<String> inputKeys)` | Create a new `Variable` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getName()` | Return the name. |
| `public void setName(String name)` | Set the name. |
| `public List<String> getInputKeys()` | Return the input keys. |
| `public Object getValue()` | Return the value. |
| `public abstract void update(Map<String, Object> kwargs)` | Update the variable value based on the given arguments. |
| `public Object eval(Map<String, Object> kwargs)` | Validate input, update `value`, and return it. |
| `protected Map<String, Object> prepareInputs(Map<String, Object> kwargs)` | Filter kwargs to only include keys that are in `inputKeys`. |

## Related Tests

- `PromptAssembleTest`
