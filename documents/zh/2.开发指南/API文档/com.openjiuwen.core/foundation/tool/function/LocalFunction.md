# com.openjiuwen.core.foundation.tool.function.LocalFunction

## class LocalFunction

```java
public class LocalFunction extends Tool
```

本地函数工具。它把一个接收 `Map<String, Object>` 的 Java `Function` 包装为标准 `Tool`。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `func` | `Function<Map<String, Object>, Object>` | `-` | 被包装的本地函数。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LocalFunction(ToolCard card, Function<Map<String, Object>, Object> func)` | 创建本地函数工具；`card` 或 `func` 不合法时抛出错误。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 按卡片中的 `inputParams` 校验并格式化输入后执行函数。 |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 执行函数一次；返回 `Iterator` 或 `Iterable` 时逐元素分片输出，其他返回值（含 `null`）包装为唯一分片。 |
| `public Function<Map<String, Object>, Object> getFunc()` | 返回底层函数对象。 |

## 使用说明

- `kwargs.skip_none_value` 与 `kwargs.skip_inputs_validate` 会透传给 `SchemaUtils.formatWithSchema(...)`。
- `LocalFunctionTest` 覆盖了构造失败、加减法调用、复杂嵌套输入，以及流式返回、非流式单分片、`null` 返回值等场景。

## 相关测试

- `LocalFunctionTest`
