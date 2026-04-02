# com.openjiuwen.core.workflow.component.llm.OutputParamConfig

## class OutputParamConfig

```java
public class OutputParamConfig
```

单个 LLM 输出字段的配置模型。

该类型把 `type`、`description`、`required` 三个别名键映射为 Java 字段 `paramType`、`paramDescription` 与 `paramRequired`，供输出配置解析链路使用。

## Fields

| Signature | Description |
| --- | --- |
| `private String paramType =` | 输出字段类型。 |
| `private String paramDescription =` | 输出字段说明。 |
| `private boolean paramRequired = false` | 是否为必填字段。 |

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
