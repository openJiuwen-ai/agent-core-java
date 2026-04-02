# com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig

意图识别组件的配置对象。

## class IntentDetectionCompConfig

```java
public class IntentDetectionCompConfig extends ComponentConfig
```

## Lombok

- 该类型使用 `@Data` 和 `@EqualsAndHashCode(callSuper = true)` 生成访问器、`equals` / `hashCode` 等样板代码。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `modelId` | `String` | - | 已注册模型 ID。 |
| `modelClientConfig` | `ModelClientConfig` | - | 模型客户端配置。 |
| `modelConfig` | `ModelRequestConfig` | - | 模型请求配置。 |
| `categoryNameList` | `List<String>` | `new ArrayList<>()` | 分类名称列表。 |
| `userPrompt` | `String` | `""` | 用户侧提示词内容。 |
| `exampleContent` | `List<String>` | `new ArrayList<>()` | few-shot 示例内容。 |
| `enableHistory` | `boolean` | `false` | 是否拼接历史消息。 |
| `chatHistoryMaxTurn` | `int` | `3` | 历史消息最大轮数。 |
| `acceptLanguage` | `String` | `"zh"` | 语言设置。 |

## Notes

- 该配置用于驱动 [`IntentDetectionExecutable`](./IntentDetectionExecutable.md) 的模型调用、分类解析与默认模板选择。
