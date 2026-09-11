# com.openjiuwen.core.application.schema.AgentMemoryConfig

## class AgentMemoryConfig

```java
public class AgentMemoryConfig
```

`AgentMemoryConfig` 是 Core 应用层持有的 Agent 记忆策略配置。该类型留在 Core 中，供 Agent 配置和可选的 Memory 实现共同使用。

## 构造方法

### `public AgentMemoryConfig()`

创建一个默认配置实例。

## 主要字段

| 字段 | 说明 |
| --- | --- |
| `memVariables` | Agent 需要维护的记忆变量。 |
| `enableLongTermMem` | 是否启用长期记忆。 |
| `enableFragmentMemory` | 是否启用片段记忆。 |
| `enableUserProfile` | 是否启用用户画像记忆。 |
| `enableSemanticMemory` | 是否启用语义记忆。 |
| `enableEpisodicMemory` | 是否启用情景记忆。 |
| `enableSummaryMemory` | 是否启用摘要记忆。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean isMemoryTypeEnabled(String memoryType)` | 返回指定片段记忆类型是否启用。 |

`LlmAgentConfig` 将该类型作为 `agentMemoryConfig` 字段暴露给应用层 Agent。启用 Memory 时，
Core 通过可选运行时接口把配置传给 `agent-core-memory-java`。
