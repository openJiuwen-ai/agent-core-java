# com.openjiuwen.core.workflow.components.llm.IntentDetectionCompConfig

## class IntentDetectionCompConfig

```java
public class IntentDetectionCompConfig extends com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig
```

Alias/extension of `com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig` with positional constructor for test compatibility.

## Constructors

| Signature | Description |
| --- | --- |
| `public IntentDetectionCompConfig( ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig, String userPrompt, List<String> categoryNameList)` | IntentDetectionCompConfig(modelCfg, modelClientCfg, userPrompt, categoryNameList) |
| `public IntentDetectionCompConfig()` | Create a new `IntentDetectionCompConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void setModel_config(ModelRequestConfig modelConfig) { setModelConfig(modelConfig); }` | Update the model config. |
| `public void setModel_client_config(ModelClientConfig modelClientConfig) { setModelClientConfig(modelClientConfig); }` | Update the model client config. |
| `public void setCategory_name_list(List<String> categoryNameList) { setCategoryNameList(categoryNameList); }` | Update the category name list. |
