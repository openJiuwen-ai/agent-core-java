# com.openjiuwen.core.foundation.prompt.assemble.variables.DictableVariable

## class DictableVariable

```java
public class DictableVariable extends Variable
```

Variable class for processing dict or list type placeholders recursively.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `LOG` | `static final Logger` | `LoggerFactory.getLogger(DictableVariable.class)` | Log. |
| `data` | `final Object` | `-` | Data. |
| `prefix` | `final String` | `-` | Prefix. |
| `suffix` | `final String` | `-` | Suffix. |
| `pattern` | `final Pattern` | `-` | Pattern. |
| `placeholders` | `final List<String>` | `-` | Placeholders. |

## Constructors

| Signature | Description |
| --- | --- |
| `public DictableVariable(Object data, String name, String prefix, String suffix)` | Construct a DictableVariable. |

## Methods

| Signature | Description |
| --- | --- |
| `private void scanPlaceholders(Object obj, LinkedHashSet<String> result)` | Execute `scanPlaceholders`. |
| `public void update(Map<String, Object> kwargs)` | Update the requested state. |
| `private Object recursiveFormat(Object obj, Map<String, Object> kwargs)` | Execute `recursiveFormat`. |
| `private Object deepCopy(Object obj)` | Execute `deepCopy`. |

## Related Tests

- `PromptAssembleTest`
