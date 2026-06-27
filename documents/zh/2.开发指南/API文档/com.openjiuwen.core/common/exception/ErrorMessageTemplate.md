# com.openjiuwen.core.common.exception.ErrorMessageTemplate

## 记录 ErrorMessageTemplate

```java
public record ErrorMessageTemplate(String template, Set<String> params)
```

`ErrorMessageTemplate` 根据结构化输入生成英文错误消息模板，并记录模板里实际使用到的占位符名称。

## 组件

| 组件 | 类型 | 说明 |
| --- | --- | --- |
| `template` | `String` | 生成后的消息模板字符串。 |
| `params` | `Set<String>` | 模板中使用到的占位符名称集合。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static ErrorMessageTemplate generate(String scope, String subject, String failureType, boolean withReason)` | 根据作用域、主题和失败类型生成模板，并按 `withReason` 决定是否附加 `reason: {error_msg}`。 |
| `public static ErrorMessageTemplate generate(String scope, String subject, String failureType)` | 便捷重载，等价于 `withReason = true`。 |

## 说明

- `scope` 与 `subject` 会先转成小写，再拼入最终模板。
- `TIMEOUT` 会额外引入 `timeout` 占位符；`withReason = true` 会额外引入 `error_msg`。
- 不支持的失败类型会抛出 `IllegalArgumentException`，`StatusCodeTest` 对此有覆盖。
