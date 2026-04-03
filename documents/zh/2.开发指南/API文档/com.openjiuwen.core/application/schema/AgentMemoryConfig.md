# com.openjiuwen.core.application.schema.AgentMemoryConfig

## class AgentMemoryConfig

```java
public class AgentMemoryConfig extends com.openjiuwen.core.memory.config.AgentMemoryConfig
```

`AgentMemoryConfig` 是应用层暴露的 memory 配置类型，直接继承共享 memory 模块中的 `AgentMemoryConfig` 实现，便于应用层配置对象统一引用。

## 构造方法

### `public AgentMemoryConfig()`

创建一个默认配置实例，并直接调用父类无参构造方法。

## 说明

- 该类型没有新增字段或方法，所有实际配置项与访问器均继承自 `com.openjiuwen.core.memory.config.AgentMemoryConfig`。
- `LlmAgentConfig` 会将它作为 `agentMemoryConfig` 字段暴露给应用层 Agent。
