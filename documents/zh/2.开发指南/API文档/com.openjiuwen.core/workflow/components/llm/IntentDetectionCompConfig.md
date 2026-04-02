# com.openjiuwen.core.workflow.components.llm.IntentDetectionCompConfig

## class IntentDetectionCompConfig

```java
public class IntentDetectionCompConfig extends com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig
```

`workflow.components.llm` 包下的意图识别配置兼容类。

它继承主包 `IntentDetectionCompConfig`，补充位置参数构造器和若干 snake_case 兼容 setter，方便旧测试继续复用。

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
