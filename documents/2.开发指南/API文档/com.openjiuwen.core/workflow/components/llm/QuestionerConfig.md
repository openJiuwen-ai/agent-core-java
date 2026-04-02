# com.openjiuwen.core.workflow.components.llm.QuestionerConfig

## class QuestionerConfig

```java
public class QuestionerConfig extends com.openjiuwen.core.workflow.component.llm.QuestionerConfig
```

`workflow.components.llm` 包下的 Questioner 配置兼容类。

该类型继承主包 `QuestionerConfig`，增加位置参数构造器及 snake_case 兼容 setter，以匹配旧测试调用方式。

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerConfig( ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig, String questionContent, boolean extractFieldsFromResponse, List<?> fieldNames, boolean withChatHistory)` | QuestionerConfig(modelCfg, modelClientCfg, questionContent, extractFieldsFromResponse, fieldNames, withChatHistory) fieldNames can be List of our FieldInfo subclass or base FieldInfo. |
| `public QuestionerConfig()` | Create a new `QuestionerConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void setModel_config(ModelRequestConfig modelConfig) { setModelConfig(modelConfig); }` | Update the model config. |
| `public void setModel_client_config(ModelClientConfig modelClientConfig) { setModelClientConfig(modelClientConfig); }` | Update the model client config. |
| `public void setWith_chat_history(boolean withChatHistory) { setWithChatHistory(withChatHistory); }` | Update the with chat history. |
| `public void setField_names(List<?> fieldNames)` | Update the field names. |
