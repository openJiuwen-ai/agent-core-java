# com.openjiuwen.core.workflow.components.llm.QuestionerConfig

## class QuestionerConfig

```java
public class QuestionerConfig extends com.openjiuwen.core.workflow.component.llm.QuestionerConfig
```

Alias/extension of `com.openjiuwen.core.workflow.component.llm.QuestionerConfig` with positional constructor for test compatibility.

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
