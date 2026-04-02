# com.openjiuwen.core.foundation.tool.annotation.ToolDefinition

## annotation ToolDefinition

```java
public @interface ToolDefinition
```

Annotation used to publish a Java method as a tool and optionally override its displayed name, description, and schema extraction behavior.

## Annotation Members

| Signature | Description |
| --- | --- |
| `String name() default ""` | - |
| `String description() default ""` | - |
| `boolean autoExtract() default true` | - |
