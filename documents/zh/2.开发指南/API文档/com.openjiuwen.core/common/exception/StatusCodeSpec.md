# com.openjiuwen.core.common.exception.StatusCodeSpec

## 记录 StatusCodeSpec

```java
public record StatusCodeSpec(String name, int code, String message)
```

`StatusCodeSpec` 表示一个已经补齐具体整数编码的状态码条目，通常由 `StatusCodeTemplate` 与实际编码值组合得到。

## 组件

| 组件 | 类型 | 说明 |
| --- | --- | --- |
| `name` | `String` | 枚举成员名。 |
| `code` | `int` | 具体整数状态码。 |
| `message` | `String` | 最终消息模板。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static StatusCodeSpec fromTemplate(StatusCodeTemplate template, int code)` | 用模板中的名称和消息模板，再配合具体整数编码生成完整条目。 |
| `public String renderEnumMember()` | 把当前条目渲染成 Java `enum` 成员片段。 |

## 说明

- 该类型偏向代码生成场景，而不是运行时异常处理。
- `StatusCodeTest` 覆盖了 `fromTemplate(...)` 和 `renderEnumMember()` 的核心行为。
