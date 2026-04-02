# com.openjiuwen.core.foundation.tool.annotation.ToolDefinition

## @interface ToolDefinition

```java
public @interface ToolDefinition
```

工具定义注解。`AnnotatedToolFactory` 会扫描带有该注解的方法，并把它们转换成 `LocalFunction`。

## 注解元素

| 元素 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | `""` | 工具名称；为空时回退到方法名。 |
| `description` | `String` | `""` | 工具说明；为空时由 `CallableSchemaExtractor.extractFunctionDescription(...)` 推导。 |
| `autoExtract` | `boolean` | `true` | 是否自动从方法签名提取输入参数 Schema。 |

## 使用说明

- 该注解只能标注在方法上。
- 运行时扫描时，如果 `autoExtract=false`，生成的输入 Schema 固定为一个空 `object`。 |
