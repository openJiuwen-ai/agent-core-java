# com.openjiuwen.core.workflow.component.llm.SchemaGenerator

## class SchemaGenerator

```java
public final class SchemaGenerator
```

根据输出配置生成 JSON Schema 的工具类。

它会遍历字段配置并生成 `type`、`description`、`properties`、`items` 和 `required` 等结构，供 JSON 输出校验与提示词注入使用。

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, Object> generateJsonSchema(Map<String, Object> outputsConfig)` | Generate a JSON schema from output configuration. |
