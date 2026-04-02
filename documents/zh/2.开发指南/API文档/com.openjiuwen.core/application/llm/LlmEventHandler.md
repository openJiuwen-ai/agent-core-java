# com.openjiuwen.core.application.llm.LlmEventHandler

## class LlmEventHandler

```java
public class LlmEventHandler extends EventHandler
```

`LlmEventHandler` 是 `LlmAgent` 的核心执行器。它负责把用户输入转成对话上下文、调用模型生成计划、执行插件或工作流任务、处理中断恢复，并把最终结果写回流式会话。

## 构造方法

### `public LlmEventHandler(LlmAgentConfig agentConfig, ContextEngine contextEngine)`

基于应用层配置与上下文引擎创建事件处理器。

**说明**

- 会根据 `agentMemoryConfig` 与 `memoryScopeId` 计算是否启用长期记忆逻辑。
- 内部维护 `LongTermMemory` 单例引用，用于在满足条件时回写消息。

## 公共方法

| 方法 | 说明 |
|---|---|
| `handleInput(EventHandlerInput inputs)` | 统一入口。正常情况下执行 ReAct 流程；异常会被包装为 `AGENT_CONTROLLER_RUNTIME_ERROR`。 |
| `handleTaskInteraction(EventHandlerInput inputs)` | 目前仅记录日志并返回 `null`。 |
| `handleTaskCompletion(EventHandlerInput inputs)` | 目前仅记录日志并返回 `null`。 |
| `handleTaskFailed(EventHandlerInput inputs)` | 目前仅记录日志并返回 `null`。 |
| `setPromptTemplate(List<Map<String, String>> promptTemplate)` | 直接替换 `agentConfig` 上的提示模板。 |

## 行为摘要

- 支持从结构化 `query` 中提取 `InteractiveInput`，用于恢复被打断的工作流节点。
- 正常路径会先调用模型生成计划，再按任务类型执行 `workflow` 或 `plugin`，直到没有新任务或达到 `constrain.maxIteration`。
- 当任务进入 `INPUT_REQUIRED` 状态时，会序列化剩余任务、当前轮次和交互输出，封装成 `TaskInterruptionState`。
- `ApplicationTranslationRegressionTest` 验证了工具调用参数会被解析为结构化 `Map` 写入任务元数据，而不是保留原始 JSON 字符串。
