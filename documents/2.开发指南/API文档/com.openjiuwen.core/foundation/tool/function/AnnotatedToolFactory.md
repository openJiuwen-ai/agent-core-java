# com.openjiuwen.core.foundation.tool.function.AnnotatedToolFactory

## class AnnotatedToolFactory

```java
public final class AnnotatedToolFactory
```

注解工具工厂。它把带有 `@ToolDefinition` 的方法转换为 `LocalFunction`，并自动构造对应的 `ToolCard`。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static List<LocalFunction> scan(Object target)` | 扫描目标实例或类对象，收集所有带 `@ToolDefinition` 的声明方法并逐个转换。 |
| `public static LocalFunction fromMethod(Object target, Method method)` | 按单个方法创建 `LocalFunction`；非静态方法要求 `target` 非空。 |

## 使用说明

- 本类通过私有构造器禁止实例化，因此不提供公开构造方法。
- 当注解中的 `name` 或 `description` 为空时，分别回退到方法名和 `CallableSchemaExtractor` 推导结果。
- 参数值通过 Jackson `ObjectMapper.convertValue(...)` 按方法签名进行类型转换。
- 对 `Optional<T>` 参数，缺失值会转换为 `Optional.empty()`。
- 工厂会调用 `method.setAccessible(true)`，因此可处理非 public 声明方法。
