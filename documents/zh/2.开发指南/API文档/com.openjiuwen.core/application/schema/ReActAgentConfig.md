# com.openjiuwen.core.application.schema.ReActAgentConfig

## class ReActAgentConfig

```java
public class ReActAgentConfig extends LlmAgentConfig
```

`ReActAgentConfig` 是基于 `LlmAgentConfig` 的派生配置类型，适合在需要显式表达 ReAct Agent 配置语义时使用。

## 说明

- 该类型没有新增字段或方法，只继承 `LlmAgentConfig` 的全部配置项。
- 类型定义使用了 `@SuperBuilder`、`@NoArgsConstructor` 与 `@EqualsAndHashCode(callSuper = true)`。
- 通过 `ReActAgentConfig.builder()` 创建实例时，默认控制器类型仍为 `ControllerType.REACT_CONTROLLER`，并继承 `getContextWindowLimit()` 的行为。
