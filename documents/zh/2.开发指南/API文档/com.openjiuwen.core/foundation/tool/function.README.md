# function

`com.openjiuwen.core.foundation.tool.function` 提供本地函数工具包装器，以及从 `@ToolDefinition` 注解方法批量生成工具的工厂。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AnnotatedToolFactory`](function/AnnotatedToolFactory.md) | 扫描目标对象或类上的注解方法，并生成 `LocalFunction` 列表。 |
| [`LocalFunction`](function/LocalFunction.md) | 把 `Function<Map<String, Object>, Object>` 包装成 `Tool`。 |

## 关键行为

- `AnnotatedToolFactory.scan(...)` 只扫描声明在当前类上的方法，即 `getDeclaredMethods()` 返回的方法。
- `LocalFunction.stream(...)` 仅接受返回值为 `Iterator` 或 `Iterable` 的函数；否则抛出错误而不是自动包装单值结果。

## 相关测试

- `LocalFunctionTest`
