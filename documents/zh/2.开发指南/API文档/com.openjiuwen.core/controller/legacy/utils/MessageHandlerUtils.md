# com.openjiuwen.core.controller.legacy.utils.MessageHandlerUtils

## final class MessageHandlerUtils

```java
public final class MessageHandlerUtils
```

`MessageHandlerUtils` 提供旧版控制器和 reasoner 复用的静态工具方法，覆盖 LLM 输入拼装、tool call 转任务、上下文写入和用户输入过滤。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `formatLlmInputs(...)` | `List<BaseMessage>` | 组合 system prompt 与聊天历史。 |
| `concatSystemPromptWithChatHistory(...)` | `List<BaseMessage>` | 合并系统提示和聊天历史；必要时避免重复 system 消息。 |
| `parseLlmOutput(AssistantMessage response, Object agentConfig)` | `List<Task>` | 从 tool call 解析任务。 |
| `createTasksFromToolCalls(List<ToolCall> toolCalls, Object agentConfig)` | `List<Task>` | 为每个 tool call 生成一个 legacy `Task`。 |
| `isInteractionResult(Object execResult)` | `boolean` | 判断执行结果是否属于交互型返回。 |
| `createInterruptResult(Exception e, String toolName)` | `Map<String, Object>` | 创建统一的中断错误结构。 |
| `validateExecutionInputs(...)` | `boolean` | 校验执行输入是否有效。 |
| `shouldAddUserMessage(String query, ContextEngine contextEngine, Session session)` | `boolean` | 决定是否把用户消息写入上下文，避免重复或工具后消息。 |
| `addUserMessage(...)` / `addAiMessage(...)` / `addToolResult(...)` | `void` | 把用户消息、AI 消息或工具结果写入 `ModelContext`。 |
| `getChatHistory(...)` | `List<BaseMessage>` | 获取最近若干轮聊天历史。 |
| `filterInputs(Map<String, Object> schema, Map<String, Object> userData)` | `Map<String, Object>` | 按 schema 提取并校验用户输入。 |

## 说明

- `createTasksFromToolCalls()` 会把 tool call 的 `id` 直接作为任务 ID，把 `name` 写入 `TaskInput.targetName`，把参数 JSON 文本写入 `TaskInput.arguments`。
- 如果 schema 标记某字段 `required = true` 而用户没有提供，`filterInputs()` 会抛出 `IllegalArgumentException`。
- 这是纯静态工具类，私有构造函数禁止实例化。
