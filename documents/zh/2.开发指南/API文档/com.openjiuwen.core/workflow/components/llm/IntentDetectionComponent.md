# com.openjiuwen.core.workflow.component.IntentDetectionComponent

## class IntentDetectionComponent

```java
public class IntentDetectionComponent extends com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl
```

`workflow.components.llm` 包下的意图识别组件兼容包装类。

它直接复用主包 `IntentDetectionComponentImpl` 的执行能力，只补充同时接受主包配置和兼容配置的构造方法。

## Constructors

| Signature | Description |
| --- | --- |
| `public IntentDetectionComponent( com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig config)` | Create a new `IntentDetectionComponent` instance. |
| `public IntentDetectionComponent(IntentDetectionCompConfig config)` | Create a new `IntentDetectionComponent` instance. |
