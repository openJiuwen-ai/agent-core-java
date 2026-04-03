# com.openjiuwen.core.workflow.component.llm.LLMComponent

## class LLMComponent

```java
public class LLMComponent implements ComponentComposable
```

工作流中的 LLM 组件封装。

该类型持有 `LLMCompConfig` 并负责构建 `LLMExecutable`；`getExecutable()` 使用延迟初始化缓存执行体，`toExecutable()` 则可按当前配置重新创建可执行对象。

## Constructors

| Signature | Description |
| --- | --- |
| `public LLMComponent(LLMCompConfig componentConfig)` | Create a new `LLMComponent` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public LLMExecutable getExecutable()` | Return the executable. |
| `public Executable<?, ?> toExecutable()` | Execute `toExecutable`. |
