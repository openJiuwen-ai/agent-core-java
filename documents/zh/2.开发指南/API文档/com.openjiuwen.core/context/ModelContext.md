# com.openjiuwen.core.context.ModelContext

## abstract class ModelContext

```java
public abstract class ModelContext
```

`ModelContext` 定义模型无关的上下文抽象 API，用于统一描述消息增删改查、窗口构造、统计与重载能力。该抽象类提供同步方法签名；如需并发调用，调用方可自行放到虚拟线程中执行。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `size()` | `int` | 返回当前上下文中的消息数量。 |
| `getMessages(Integer size, boolean withHistory)` | `List<BaseMessage>` | 按尾部读取消息；`withHistory` 决定是否包含历史消息。 |
| `getMessages()` | `List<BaseMessage>` | 读取全部消息的便捷重载。 |
| `setMessages(List<BaseMessage> messages, boolean withHistory)` | `void` | 替换消息内容；可选择是否替换历史段。 |
| `setMessages(List<BaseMessage> messages)` | `void` | 替换全部消息的便捷重载。 |
| `popMessages(int size, boolean withHistory)` | `List<BaseMessage>` | 从尾部弹出消息；可选择是否连带历史。 |
| `popMessages()` | `List<BaseMessage>` | 默认弹出 1 条消息。 |
| `clearMessages(boolean withHistory)` | `void` | 清理当前轮消息，并可选择同时清理历史。 |
| `addMessages(List<BaseMessage> messages)` | `List<BaseMessage>` | 追加多条消息并返回最终写入的消息序列。 |
| `addMessages(BaseMessage message)` | `List<BaseMessage>` | 追加单条消息的便捷重载。 |
| `getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound, Map<String, Object> kwargs)` | `ContextWindow` | 构造模型推理窗口。 |
| `getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound)` | `ContextWindow` | 无 `kwargs` 的便捷重载。 |
| `getContextWindow()` | `ContextWindow` | 使用默认参数构造窗口。 |
| `statistic()` | `ContextStats` | 统计当前上下文的消息和 token 信息。 |
| `sessionId()` | `String` | 返回所属会话 ID。 |
| `contextId()` | `String` | 返回上下文 ID。 |
| `tokenCounter()` | `TokenCounter` | 返回当前上下文使用的 token 计数器。 |
| `reloaderTool()` | `Tool` | 返回用于重载已卸载消息的工具。 |

## 说明

- `ModelContextTest` 通过 `SessionModelContext` 覆盖了 `withHistory` 标志、窗口大小、对话轮次、token 统计和重载工具行为。
- 负数 `size`、`windowSize` 或 `dialogueRound` 在具体实现中会转换成 `CONTEXT_EXECUTION_ERROR`。
