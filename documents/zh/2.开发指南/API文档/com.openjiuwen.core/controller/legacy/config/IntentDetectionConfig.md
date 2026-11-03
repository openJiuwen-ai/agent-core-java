# com.openjiuwen.core.single_agent.legacy.config.IntentDetectionConfig

## class IntentDetectionConfig

```java
public class IntentDetectionConfig
```

`IntentDetectionConfig` 定义旧版意图检测模块的提示词、分类列表和聊天历史开关。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `categoryInfo` | `String` | `""` | 分类说明文本。 |
| `categoryList` | `List<String>` | 空列表 | 可识别分类列表。 |
| `intentDetectionTemplate` | `List<Map<String, Object>>` | 空列表 | 意图检测模板。 |
| `userPrompt` | `String` | `""` | 用户提示词模板。 |
| `chatHistoryMaxTurn` | `int` | `100` | 最大聊天轮数。 |
| `defaultClass` | `String` | `分类0` | 默认分类标签。 |
| `enableHistory` | `boolean` | `false` | 是否启用历史消息。 |
| `enableInput` | `boolean` | `true` | 是否把当前输入加入提示。 |
| `exampleContent` | `List<String>` | 空列表 | 示例内容。 |

## 说明

- 该类通过 Lombok `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor` 提供常规访问器和 builder。
- `DefaultIntentDetector.prepareDetectionInput()` 会读取这里的大部分字段并拼出 LLM 输入。
