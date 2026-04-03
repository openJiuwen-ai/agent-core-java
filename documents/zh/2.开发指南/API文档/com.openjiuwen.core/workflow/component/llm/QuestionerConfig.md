# com.openjiuwen.core.workflow.component.llm.QuestionerConfig

Questioner 组件配置对象。

## class QuestionerConfig

```java
public class QuestionerConfig extends ComponentConfig
```

## Lombok

- 该类型使用 `@Data` 和 `@EqualsAndHashCode(callSuper = true)` 生成访问器、`equals` / `hashCode` 等样板代码。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `modelId` | `String` | - | 已注册模型 ID。 |
| `modelClientConfig` | `ModelClientConfig` | - | 模型客户端配置。 |
| `modelConfig` | `ModelRequestConfig` | - | 模型请求配置。 |
| `responseType` | `String` | `ResponseType.REPLY_DIRECTLY.getValue()` | 响应类型。 |
| `questionContent` | `String` | `""` | 固定追问模板。 |
| `extractFieldsFromResponse` | `boolean` | `true` | 是否从回复中抽取字段。 |
| `fieldNames` | `List<FieldInfo>` | `new ArrayList<>()` | 待抽取字段列表。 |
| `maxResponse` | `int` | `3` | 最大追问次数。 |
| `withChatHistory` | `boolean` | `false` | 是否启用对话历史。 |
| `chatHistoryMaxRounds` | `int` | `5` | 对话历史最大轮数。 |
| `extraPromptForFieldsExtraction` | `String` | `""` | 额外抽取约束提示。 |
| `exampleContent` | `String` | `""` | 示例内容。 |
| `acceptLanguage` | `String` | `"zh"` | 语言设置。 |

## Notes

- 该配置主要供 [`QuestionerExecutable`](./QuestionerExecutable.md) 与 [`QuestionerDirectReplyHandler`](./QuestionerDirectReplyHandler.md) 使用。
