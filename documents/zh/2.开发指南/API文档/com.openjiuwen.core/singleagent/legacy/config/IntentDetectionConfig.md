# com.openjiuwen.core.singleagent.legacy.config.IntentDetectionConfig

## 类 IntentDetectionConfig

```java
public class IntentDetectionConfig
```

意图识别阶段的模板与分类配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `intentDetectionTemplate` | `List<Map<String, String>>` | `new ArrayList<>()` | 意图识别阶段使用的提示词模板。 |
| `defaultClass` | `String` | `"分类1"` | 无法明确分类时返回的默认类别。 |
| `enableInput` | `boolean` | `true` | 是否把当前输入纳入识别上下文。 |
| `enableHistory` | `boolean` | `false` | 是否把聊天历史纳入识别上下文。 |
| `chatHistoryMaxTurn` | `int` | `5` | 参与意图识别的历史最大轮次。 |
| `categoryList` | `List<String>` | `new ArrayList<>()` | 可选分类标签列表。 |
| `userPrompt` | `String` | `""` | 额外的用户侧提示语。 |
| `exampleContent` | `List<String>` | `new ArrayList<>()` | 用于分类参考的示例内容。 |

## 说明

- 源码使用 Lombok `@Data`、`@Builder`、`@NoArgsConstructor` 和 `@AllArgsConstructor` 生成常规构造与访问器。
