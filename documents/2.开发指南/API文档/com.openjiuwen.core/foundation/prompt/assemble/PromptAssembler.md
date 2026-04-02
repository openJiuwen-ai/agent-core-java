# com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler

## class PromptAssembler

```java
public class PromptAssembler
```

Assembler that substitutes placeholders in a prompt template.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `templateContent` | `Object` | `-` | Template content. |
| `placeholderPrefix` | `final String` | `-` | Placeholder prefix. |
| `placeholderSuffix` | `final String` | `-` | Placeholder suffix. |
| `templateFormatters` | `final List<Variable>` | `-` | Template formatters. |
| `variables` | `final Map<String, Variable>` | `-` | Variables. |

## Constructors

| Signature | Description |
| --- | --- |
| `public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix, Map<String, Variable> initialVariables)` | Construct a PromptAssembler. |
| `public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix)` | Create a new `PromptAssembler` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> getInputKeys()` | Get all input keys needed for the template. |
| `public Object promptAssemble(Map<String, Object> kwargs)` | Assemble the prompt by substituting placeholders with the given keyword arguments. |
| `private List<Variable> buildFormatterList()` | Build the configured result. |
| `private Map<String, Variable> buildVariablesWithVerify(Map<String, Variable> inputVariables)` | Build the configured result. |
| `private void doUpdate(Map<String, Object> kwargs)` | Execute `doUpdate`. |
| `private Object doFormat()` | Execute `doFormat`. |

## Related Tests

- `PromptAssembleTest`
