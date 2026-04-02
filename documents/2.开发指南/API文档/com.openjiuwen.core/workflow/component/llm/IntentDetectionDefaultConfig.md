# com.openjiuwen.core.workflow.component.llm.IntentDetectionDefaultConfig

意图识别组件的默认模板与分类配置。

## class IntentDetectionDefaultConfig

```java
public class IntentDetectionDefaultConfig
```

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `categoryList` | `List<String>` | `new ArrayList<>()` | 默认分类键列表。 |
| `intentDetectionTemplate` | `PromptTemplate` | - | 当前使用的意图识别提示词模板。 |
| `defaultClass` | `String` | - | 兜底分类键。 |
| `enableInput` | `boolean` | `true` | 是否把当前输入注入模板。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public IntentDetectionDefaultConfig(String acceptLanguage)` | 根据语言初始化默认模板，并把 `defaultClass` 设为 `Category0` 或 `分类0`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static PromptTemplate getDefaultTemplate(String acceptLanguage)` | 根据语言生成默认提示词模板。 |

## Notes

- 模板内容内置中英文两套系统提示词和用户提示词，构造时仅选择其一。
