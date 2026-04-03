# com.openjiuwen.core.memory.process.extract.Generator

## 类 Generator

```java
public class Generator
```

`Generator` 协调消息分析、摘要生成与长期片段提取，输出按记忆类型分组的 `BaseMemoryUnit` 列表。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆处理流程使用的日志记录器。 |
| `dataIdGenerator` | `DataIdManager` | 为摘要与片段记忆生成新的 `memId`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Generator(DataIdManager dataIdGenerator)` | 使用给定的数据 ID 管理器创建生成器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, List<BaseMemoryUnit>> genAllMemory(Map<String, Object> kwargs)` | 从 `kwargs` 提取消息、配置、模型与上下文参数，生成变量、摘要和片段记忆结果。 |

## 行为说明

- 方法要求 `messages`、`config`、`user_id`、`scope_id`、`base_chat_model` 同时存在；缺失时会记录错误并返回空映射。
- 会先调用 `MemoryAnalyzer.analyze(...)` 获取变量、摘要与关键信息标记，再根据 `AgentMemoryConfig` 的开关决定是否生成摘要记忆和片段记忆。
- 变量结果会被转换为 `VariableUnit`，摘要结果会生成 `SummaryUnit`，长期片段会生成 `FragmentMemoryUnit`。
