# com.openjiuwen.core.workflow.components.llm.LLMComponent

## class LLMComponent

```java
public class LLMComponent extends com.openjiuwen.core.workflow.component.llm.LLMComponent
```

`workflow.components.llm` 包下的 LLM 组件兼容包装类。

该类型继承主包 `LLMComponent`，提供接受主包配置、接受兼容配置以及无参空配置三种构造方式，核心执行行为仍由父类实现。

## Constructors

| Signature | Description |
| --- | --- |
| `public LLMComponent(com.openjiuwen.core.workflow.component.llm.LLMCompConfig config)` | 使用主包配置对象创建兼容组件实例。 |
| `public LLMComponent(LLMCompConfig config)` | 使用本包兼容配置对象创建组件实例。 |
| `public LLMComponent()` | 创建带空白配置的兼容组件实例。 |
