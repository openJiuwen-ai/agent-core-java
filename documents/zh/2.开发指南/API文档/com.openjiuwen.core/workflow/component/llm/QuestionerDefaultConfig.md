# com.openjiuwen.core.workflow.component.llm.QuestionerDefaultConfig

## class QuestionerDefaultConfig

```java
public class QuestionerDefaultConfig
```

Questioner 组件的默认提示词配置。

源码内置中英文 system/user 模板，以及继续追问时使用的固定提示语；`fromLanguage(...)` 会按 `acceptLanguage` 选择默认消息模板，供 `QuestionerExecutable` 初始化 `PromptTemplate`。

## Fields

| Signature | Description |
| --- | --- |
| `private final List<BaseMessage> promptTemplate` | 当前实例持有的默认提示词消息列表。 |

## 常量说明

- `QUESTIONER_SYSTEM_TEMPLATE_ZH` / `QUESTIONER_USER_TEMPLATE_ZH`：中文字段抽取提示词模板。
- `QUESTIONER_SYSTEM_TEMPLATE_EN` / `QUESTIONER_USER_TEMPLATE_EN`：英文字段抽取提示词模板。
- `CONTINUE_ASK_STATEMENT_ZH` / `CONTINUE_ASK_STATEMENT_EN`：缺少必填字段时的继续追问文案模板。

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerDefaultConfig(List<BaseMessage> promptTemplate)` | 使用指定提示词消息列表创建默认配置对象。 |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerDefaultConfig fromLanguage(String acceptLanguage)` | 按语言选择默认模板并创建配置对象。 |
| `public List<BaseMessage> getPromptTemplate()` | 返回当前实例持有的提示词消息列表。 |
| `public static List<BaseMessage> getDefaultTemplate(String acceptLanguage)` | 返回指定语言下的默认 system/user 消息模板。 |
