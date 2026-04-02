# com.openjiuwen.core.workflow.component.llm.ValidationUtils

## class ValidationUtils

```java
public final class ValidationUtils
```

LLM 组件输入输出校验工具。

该工具类封装了类型检查、简化版 JSON Schema 校验以及输出配置合法性校验，发现问题时会统一抛出组件配置错误。

## Methods

| Signature | Description |
| --- | --- |
| `public static void raiseInvalidParamsError(String errorMsg)` | Raise an invalid params error. |
| `public static void validateType(Object instance, String expectedType)` | Validate that the instance matches the expected type. |
| `public static void validateJsonSchema(Object instance, Map<String, Object> schema)` | Validate an instance against a JSON schema (simplified). |
| `public static void validateOutputsConfig(Object outputsConfig)` | Validate that output config is non-empty and is a Map. |
