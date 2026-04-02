# com.openjiuwen.core.workflow.component.llm.ValidationUtils

## class ValidationUtils

```java
public final class ValidationUtils
```

Validation utilities for LLM component inputs and outputs.

## Methods

| Signature | Description |
| --- | --- |
| `public static void raiseInvalidParamsError(String errorMsg)` | Raise an invalid params error. |
| `public static void validateType(Object instance, String expectedType)` | Validate that the instance matches the expected type. |
| `public static void validateJsonSchema(Object instance, Map<String, Object> schema)` | Validate an instance against a JSON schema (simplified). |
| `public static void validateOutputsConfig(Object outputsConfig)` | Validate that output config is non-empty and is a Map. |
