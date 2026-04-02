# com.openjiuwen.core.foundation.prompt.PromptTemplate

## class PromptTemplate

```java
public class PromptTemplate
```

提示词模板对象。它保存模板名称、模板内容与占位符分隔符，并提供格式化与消息列表转换能力。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | `""` | 模板名称。 |
| `content` | `Object` | `""` | 模板内容，源码明确支持 `String` 或 `List<BaseMessage>`。 |
| `placeholderPrefix` | `String` | `"{{"` | 占位符前缀。 |
| `placeholderSuffix` | `String` | `"}}"` | 占位符后缀。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PromptTemplate(String name, Object content, String placeholderPrefix, String placeholderSuffix)` | 直接指定模板名称、内容与占位符分隔符。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public List<BaseMessage> toMessages()` | 把模板内容转换为消息列表：空内容返回空列表，字符串内容转换为单个 `UserMessage`，消息列表内容会复制后返回。 |
| `public PromptTemplate format(Map<String, Object> keywords)` | 根据当前模板实际需要的键执行替换，并返回新的 `PromptTemplate`。 |

## 使用说明

- `format` 会忽略模板未使用的多余键。
- 当 `keywords` 缺少某些占位符时，这些占位符会保留原文，不会默认抛错。
- `toMessages()` 遇到既不是 `String` 也不是 `List<BaseMessage>` 的内容时会抛出 `PROMPT_TEMPLATE_INVALID`。

## 相关测试

- `PromptAssembleTest`
