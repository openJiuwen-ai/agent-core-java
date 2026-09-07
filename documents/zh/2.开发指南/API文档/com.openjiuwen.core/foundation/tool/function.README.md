# function

`com.openjiuwen.core.foundation.tool.function` 提供本地函数工具包装器，以及从 `@ToolDefinition` 注解方法批量生成工具的工厂。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AnnotatedToolFactory`](function/AnnotatedToolFactory.md) | 扫描目标对象或类上的注解方法，并生成 `LocalFunction` 列表。 |
| [`LocalFunction`](function/LocalFunction.md) | 把 `Function<Map<String, Object>, Object>` 包装成 `Tool`。 |

## 关键行为

- `AnnotatedToolFactory.scan(...)` 只扫描声明在当前类上的方法，即 `getDeclaredMethods()` 返回的方法。
- `LocalFunction.stream(...)` 按函数返回值决定分片方式：返回 `Iterator` 或 `Iterable` 时逐元素分片，其他返回值（含 `null`）包装为唯一分片。无论哪种情况，底层函数都只执行一次。

## 相关测试

- `LocalFunctionTest`
