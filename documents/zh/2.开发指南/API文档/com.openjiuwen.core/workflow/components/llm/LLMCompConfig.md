# com.openjiuwen.core.workflow.component.llm.LLMCompConfig

## class LLMCompConfig

```java
public class LLMCompConfig extends com.openjiuwen.core.workflow.component.llm.LLMCompConfig
```

`workflow.components.llm` 包下的 LLM 配置兼容类。

它继承主包 `LLMCompConfig`，补充位置参数构造器与 `builder()`，主要用于兼容旧测试中的位置参数和链式构造方式。

## Constructors

| Signature | Description |
| --- | --- |
| `public LLMCompConfig( ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig, List<Map<String, Object>> templateContent, Map<String, Object> responseFormat, Map<String, Object> outputConfig)` | 使用位置参数一次性创建兼容配置对象。 |
| `public LLMCompConfig()` | 创建空白兼容配置对象。 |

## Methods

| Signature | Description |
| --- | --- |
| `public static LLMCompConfigBuilder builder()` | 返回链式构造器。 |

## Nested Types

| Type | Kind | Description |
| --- | --- | --- |
| `LLMCompConfigBuilder` | `class` | `LLMCompConfig` 的链式构造器。 |
