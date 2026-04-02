# com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable

## class TextableVariable

```java
public class TextableVariable extends Variable
```

Variable class for processing string-type placeholders.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `LOG` | `static final Logger` | `LoggerFactory.getLogger(TextableVariable.class)` | Log. |
| `text` | `final String` | `-` | Text. |
| `prefix` | `final String` | `-` | Prefix. |
| `suffix` | `final String` | `-` | Suffix. |
| `placeholders` | `final List<String>` | `-` | Placeholders. |

## Constructors

| Signature | Description |
| --- | --- |
| `public TextableVariable(String text, String name, String prefix, String suffix)` | Construct a new TextableVariable. |

## Methods

| Signature | Description |
| --- | --- |
| `public void update(Map<String, Object> kwargs)` | Update the requested state. |

## Related Tests

- `PromptAssembleTest`
