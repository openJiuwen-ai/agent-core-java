# com.openjiuwen.core.workflow.component.llm.OutputParamConfig

## class OutputParamConfig

```java
public class OutputParamConfig
```

Configuration model for a single LLM output parameter. `type`, `description`, `required`.

## Fields

| Signature | Description |
| --- | --- |
| `private String paramType =` | . |
| `private String paramDescription =` | . |
| `private boolean paramRequired = false` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public OutputParamConfig()` | Create a new `OutputParamConfig` instance. |
| `public OutputParamConfig(String paramType, String paramDescription, boolean paramRequired)` | Create a new `OutputParamConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getParamType()` | Return the param type. |
| `public void setParamType(String paramType)` | Update the param type. |
| `public String getParamDescription()` | Return the param description. |
| `public void setParamDescription(String paramDescription)` | Update the param description. |
| `public boolean isParamRequired()` | Report whether param required. |
| `public void setParamRequired(boolean paramRequired)` | Update the param required. |
| `public static OutputParamConfig fromMap(Map<String, Object> map)` | Validate and create from a map (uses aliased keys: "type", "description", "required"). |
