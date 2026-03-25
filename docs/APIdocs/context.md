# Context 模块 API 文档

> 包路径：`com.openjiuwen.core.context`

上下文窗口、上下文处理器、压缩器、卸载器与 token 计数能力。基于 `context` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `32` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.context` | 5 |
| `com.openjiuwen.core.context.context` | 5 |
| `com.openjiuwen.core.context.processor` | 3 |
| `com.openjiuwen.core.context.processor.compressor` | 6 |
| `com.openjiuwen.core.context.processor.offloader` | 4 |
| `com.openjiuwen.core.context.schema` | 7 |
| `com.openjiuwen.core.context.token` | 2 |

## `com.openjiuwen.core.context`

公开类型：`5`

### `ContextEngine`

- 类型：`class`
- 声明：`public class ContextEngine`
- 说明：Manages the lifecycle and processing of conversational context.
- 嵌套公开类型：`ContextEngine.ProcessorSpec`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ContextEngine()` | - |
| `public ContextEngine(ContextEngineConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ModelContext createContext(String contextId, Session session, List<ProcessorSpec> processors, List<BaseMessage> historyMessages, TokenCounter tokenCounter)` | `ModelContext` | Create or retrieve a ModelContext for the given session and context ID. |
| `public ModelContext createContext(String contextId, Session session)` | `ModelContext` | Create context with defaults. |
| `public ModelContext getContext(String contextId, String sessionId)` | `ModelContext` | Retrieve an existing ModelContext from the pool. |
| `public void clearContext(String contextId, String sessionId)` | `void` | Remove contexts from the internal pool. |
| `public void saveContexts(Session session, List<String> contextIds)` | `void` | Batch-persist multiple contexts and their runtime states. |
| `public static void registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass, Function<Object, ContextProcessor> factory)` | `void` | Register a processor class so the engine can instantiate it at runtime. |
| `public static void registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass)` | `void` | Register a processor class with a constructor-based factory. |
| `public static Class<? extends ContextProcessor> getProcessorClass(String processorType)` | `Class<? extends ContextProcessor>` | Get a registered processor class by type name. |

### `ContextEngine.ProcessorSpec`

- 类型：`record`
- 声明：`public record ProcessorSpec(String processorType, Object config)`
- 说明：Specifies a processor type and its associated configuration.
- 宿主类型：`ContextEngine`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `processorType` | `String` | `private final` | `-` | - |
| `config` | `Object` | `private final` | `-` | - |

### `ContextStats`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ContextStats`
- 说明：Token-usage snapshot for any context container (ModelContext or ContextWindow).
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `totalMessages` | `int` | `private` | `0` | - |
| `totalTokens` | `int` | `private` | `0` | - |
| `totalDialogues` | `int` | `private` | `0` | - |
| `systemMessages` | `int` | `private` | `0` | - |
| `userMessages` | `int` | `private` | `0` | - |
| `assistantMessages` | `int` | `private` | `0` | - |
| `toolMessages` | `int` | `private` | `0` | - |
| `tools` | `int` | `private` | `0` | - |
| `systemMessageTokens` | `int` | `private` | `0` | - |
| `userMessageTokens` | `int` | `private` | `0` | - |
| `assistantMessageTokens` | `int` | `private` | `0` | - |
| `toolMessageTokens` | `int` | `private` | `0` | - |
| `toolTokens` | `int` | `private` | `0` | - |

### `ContextWindow`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ContextWindow`
- 说明：A lightweight, serializable snapshot of the messages and tools that will actually be sent to the LLM endpoint.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `systemMessages` | `List<BaseMessage>` | `private` | `new ArrayList<>()` | System-level directives (e.g., instructions, personas) that should remain at the beginning of the final message list. |
| `contextMessages` | `List<BaseMessage>` | `private` | `new ArrayList<>()` | Conversation history or user inputs that may be truncated, compressed, or re-ordered by ContextEngine processors. |
| `tools` | `List<ToolInfo>` | `private` | `new ArrayList<>()` | Tool definitions (functions, plugins) that the model is allowed to invoke during the turn. |
| `statistic` | `ContextStats` | `private` | `new ContextStats()` | Aggregated statistics for this context window. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<BaseMessage> getMessages()` | `List<BaseMessage>` | Get all messages (system + context) for sending to the model. |
| `public List<ToolInfo> getToolList()` | `List<ToolInfo>` | Get the tool definitions. |

### `ModelContext`

- 类型：`class`
- 声明：`public abstract class ModelContext`
- 说明：Abstract base class for managing conversational context in a model-agnostic way.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract int size()` | `int` | Return the length of the context (number of messages). |
| `public abstract List<BaseMessage> getMessages(Integer size, boolean withHistory)` | `List<BaseMessage>` | Retrieve messages from the conversation context without removing them. |
| `public List<BaseMessage> getMessages()` | `List<BaseMessage>` | Get all messages (with history). |
| `public abstract void setMessages(List<BaseMessage> messages, boolean withHistory)` | `void` | Replace the current message list with the provided one. |
| `public void setMessages(List<BaseMessage> messages)` | `void` | Set messages replacing all (with history). |
| `public abstract List<BaseMessage> popMessages(int size, boolean withHistory)` | `List<BaseMessage>` | Remove and return the oldest messages from the context. |
| `public List<BaseMessage> popMessages()` | `List<BaseMessage>` | Pop one message (with history). |
| `public abstract void clearMessages(boolean withHistory)` | `void` | Remove all messages added in the current turn. |
| `public abstract List<BaseMessage> addMessages(List<BaseMessage> messages)` | `List<BaseMessage>` | Add one or more messages to the conversation context. |
| `public List<BaseMessage> addMessages(BaseMessage message)` | `List<BaseMessage>` | Add a single message. |
| `public abstract ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound)` | `ContextWindow` | Build and return a window of messages suitable for model inference. |
| `public ContextWindow getContextWindow()` | `ContextWindow` | Get context window with defaults. |
| `public abstract ContextStats statistic()` | `ContextStats` | Compute context-wide statistics. |
| `public abstract String sessionId()` | `String` | Return the session identifier. |
| `public abstract String contextId()` | `String` | Return the context identifier. |
| `public abstract TokenCounter tokenCounter()` | `TokenCounter` | Return the token counter used by this context. |
| `public abstract Tool reloaderTool()` | `Tool` | Return a tool for reloading offloaded messages back into context. |

## `com.openjiuwen.core.context.context`

公开类型：`5`

### `ContextMessageBuffer`

- 类型：`class`
- 声明：`public class ContextMessageBuffer`
- 说明：Manages the context message buffer, supporting history tracking and size limits.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ContextMessageBuffer(List<BaseMessage> historyMessages, Integer maxBufferSize)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int size()` | `int` | Return the effective size of the buffer. |
| `public void addBack(List<BaseMessage> messages)` | `void` | Append messages to the back of the buffer. |
| `public List<BaseMessage> getBack(Integer size, boolean withHistory)` | `List<BaseMessage>` | Get messages from the back of the buffer. |
| `public List<BaseMessage> getBack()` | `List<BaseMessage>` | Get all messages from the back. |
| `public List<BaseMessage> popBack(int size, boolean withHistory)` | `List<BaseMessage>` | Pop messages from the back of the buffer. |
| `public void setMessages(List<BaseMessage> messages, boolean withHistory)` | `void` | Replace messages in the buffer. |
| `public void rebuild(List<BaseMessage> historyMessages)` | `void` | Rebuild the buffer from a new list of history messages. |

### `ContextUtils`

- 类型：`class`
- 声明：`public final class ContextUtils`
- 说明：Utility helper functions for manipulating and parsing conversation contexts.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Optional<Integer> findLastAiMessageWithoutToolCall(List<BaseMessage> messages)` | `Optional<Integer>` | Find the index of the last assistant message without tool calls. |
| `public static List<BaseMessage> replaceMessages(List<BaseMessage> messages, List<BaseMessage> targetMessages, int startIndex, int endIndex)` | `List<BaseMessage>` | Replace a range of messages with target messages. |
| `public static String formatReloadedMessages(String offloadHandle, List<BaseMessage> messages)` | `String` | Format reloaded messages for display. |
| `public static List<int[]> findAllDialogueRound(List<BaseMessage> messages)` | `List<int[]>` | Find all dialogue rounds in the message list. |
| `public static int findLastNDialogueRound(List<BaseMessage> messages, int n)` | `int` | Find the start index for the last N dialogue rounds. |

### `KVCacheManager`

- 类型：`class`
- 声明：`public class KVCacheManager`
- 说明：Manages KV cache release for inference-affinity models.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public KVCacheManager(String sessionId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void release(ContextWindow contextWindow)` | `void` | Check and release stale KV cache if the context window has changed. |
| `public void release(ContextWindow contextWindow, Object model)` | `void` | Check and release stale KV cache if the context window has changed and a model with release capability is provided. |

### `OffloadMessageBuffer`

- 类型：`class`
- 声明：`public class OffloadMessageBuffer`
- 说明：Buffer for messages that have been offloaded from the context window.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OffloadMessageBuffer()` | - |
| `public OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages)` | `void` | Offload messages to the specified storage. |
| `public List<BaseMessage> reload(String offloadHandle, String offloadType)` | `List<BaseMessage>` | Reload offloaded messages from storage. |
| `public void clear(String offloadHandle, String offloadType)` | `void` | Clear a specific offloaded message set. |
| `public Map<String, List<BaseMessage>> getAll()` | `Map<String, List<BaseMessage>>` | Get all offloaded messages. |

### `SessionModelContext`

- 类型：`class`
- 声明：`public class SessionModelContext extends ModelContext`
- 说明：Core implementation of ModelContext backed by a message buffer and supporting processors, offloading, and KV cache management.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config, List<BaseMessage> historyMessages, List<ContextProcessor> processors, TokenCounter tokenCounter)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int size()` | `int` | - |
| `public String sessionId()` | `String` | - |
| `public String contextId()` | `String` | - |
| `public List<BaseMessage> addMessages(List<BaseMessage> messages)` | `List<BaseMessage>` | - |
| `public List<BaseMessage> popMessages(int size, boolean withHistory)` | `List<BaseMessage>` | - |
| `public List<BaseMessage> getMessages(Integer size, boolean withHistory)` | `List<BaseMessage>` | - |
| `public void setMessages(List<BaseMessage> messages, boolean withHistory)` | `void` | - |
| `public void clearMessages(boolean withHistory)` | `void` | - |
| `public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound)` | `ContextWindow` | - |
| `public ContextStats statistic()` | `ContextStats` | - |
| `public TokenCounter tokenCounter()` | `TokenCounter` | - |
| `public Tool reloaderTool()` | `Tool` | - |
| `public void offloadMessages(String offloadHandle, List<BaseMessage> messages)` | `void` | Offload messages to the in-memory buffer. |
| `public Map<String, Object> saveState()` | `Map<String, Object>` | Save context state for persistence. |
| `public void loadState(Map<String, Object> state)` | `void` | Load context state from persistence. |

## `com.openjiuwen.core.context.processor`

公开类型：`3`

### `ContextEvent`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ContextEvent`
- 说明：Event emitted by a ContextProcessor describing what was modified.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `eventType` | `String` | `private` | `-` | - |
| `messagesToModify` | `List<Integer>` | `private` | `new ArrayList<>()` | - |

### `ContextProcessor`

- 类型：`class`
- 声明：`public abstract class ContextProcessor`
- 说明：Abstract base class for all context-processing plug-ins.
- 嵌套公开类型：`ContextProcessor.ProcessResult`

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected ContextProcessor(Object config)` | Store the processor-specific configuration. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | Transform or filter the incoming message batch. |
| `public ProcessResult onGetContextWindow(ModelContext context, ContextWindow contextWindow)` | `ProcessResult` | Mutate the outgoing context window (e.g. |
| `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | Return `true` if this processor wants to intervene before the messages are appended to the context. |
| `public boolean triggerGetContextWindow(ModelContext context, ContextWindow contextWindow)` | `boolean` | Return `true` if this processor wants to intervene before the context window is returned to the caller. |
| `public abstract void loadState(Map<String, Object> state)` | `void` | Restore internal state from a dictionary produced by #saveState(). |
| `public abstract Map<String, Object> saveState()` | `Map<String, Object>` | Export internal state to a serialisable map. |
| `public String processorType()` | `String` | Return the registered processor type string (the simple class name). |
| `public <T>T getConfig()` | `T` | Read-only access to the validated configuration object. |
| `protected BaseMessage offloadMessages(String role, String content, List<BaseMessage> messages, ModelContext context, String offloadHandle, String offloadType)` | `BaseMessage` | Offload messages to in-memory storage and return a replacement marker message. |
| `protected BaseMessage offloadMessages(String role, String content, List<BaseMessage> messages, ModelContext context)` | `BaseMessage` | Overloaded convenience method with defaults. |

### `ContextProcessor.ProcessResult`

- 类型：`record`
- 声明：`public record ProcessResult(ContextEvent event, List<BaseMessage> messages, ContextWindow contextWindow)`
- 说明：Result from a processor hook.
- 宿主类型：`ContextProcessor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `event` | `ContextEvent` | `private final` | `-` | - |
| `messages` | `List<BaseMessage>` | `private final` | `-` | - |
| `contextWindow` | `ContextWindow` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static ProcessResult ofMessages(ContextEvent event, List<BaseMessage> messages)` | `ProcessResult` | - |
| `public static ProcessResult ofContextWindow(ContextEvent event, ContextWindow contextWindow)` | `ProcessResult` | - |

## `com.openjiuwen.core.context.processor.compressor`

公开类型：`6`

### `CurrentRoundCompressor`

- 类型：`class`
- 声明：`public class CurrentRoundCompressor extends ContextProcessor`
- 说明：Compresses messages within the current dialogue round to stay within token or message-count budgets.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CurrentRoundCompressor(CurrentRoundCompressorConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | - |
| `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | - |
| `public void loadState(Map<String, Object> state)` | `void` | - |
| `public Map<String, Object> saveState()` | `Map<String, Object>` | - |

### `CurrentRoundCompressorConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class CurrentRoundCompressorConfig`
- 说明：Configuration for the CurrentRoundCompressor ContextProcessor.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `messagesThreshold` | `Integer` | `private` | `-` | Maximum number of messages allowed before compression is triggered. |
| `tokensThreshold` | `int` | `private` | `10000` | Maximum accumulated token count before compression is triggered. |
| `messagesToKeep` | `Integer` | `private` | `-` | Number of most-recent messages to retain, regardless of thresholds. |
| `largeMessageThreshold` | `int` | `private` | `1000` | Token count above which a single message is considered 'large'. |
| `customizedCompressionPrompt` | `String` | `private` | `-` | User-supplied prompt for compression; falls back to built-in prompt if null. |
| `singleMultiCompression` | `boolean` | `private` | `false` | Switch between single-message and whole-block compression. |
| `model` | `ModelRequestConfig` | `private` | `-` | Model request configuration. |
| `modelClient` | `ModelClientConfig` | `private` | `-` | Optional client-level configuration for the model. |

### `DialogueCompressor`

- 类型：`class`
- 声明：`public class DialogueCompressor extends ContextProcessor`
- 说明：Compresses completed dialogue rounds (user question \u2192 tool calls \u2192 assistant answer) to keep context within budget.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DialogueCompressor(DialogueCompressorConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | - |
| `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | - |
| `public void loadState(Map<String, Object> state)` | `void` | - |
| `public Map<String, Object> saveState()` | `Map<String, Object>` | - |

### `DialogueCompressorConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DialogueCompressorConfig`
- 说明：Configuration for the DialogueCompressor ContextProcessor.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `messagesThreshold` | `Integer` | `private` | `-` | Maximum number of messages allowed before compression is triggered. |
| `tokensThreshold` | `int` | `private` | `10000` | Maximum accumulated token count before compression is triggered. |
| `messagesToKeep` | `Integer` | `private` | `-` | Number of most-recent messages to retain regardless of thresholds. |
| `keepLastRound` | `boolean` | `private` | `true` | If true, the most recent user-assistant round is always preserved. |
| `customizedCompressionPrompt` | `String` | `private` | `-` | User-supplied prompt for the compression step. |
| `compressionTokenLimit` | `int` | `private` | `2000` | Max tokens allowed in the compressed summary. |
| `model` | `ModelRequestConfig` | `private` | `-` | Model request configuration. |
| `modelClient` | `ModelClientConfig` | `private` | `-` | Optional client-level configuration for the model. |

### `RoundLevelCompressor`

- 类型：`class`
- 声明：`public class RoundLevelCompressor extends ContextProcessor`
- 说明：Compresses multiple consecutive dialogue rounds of the same compression level into a single summarized round.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RoundLevelCompressor(RoundLevelCompressorConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | - |
| `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | - |
| `public void loadState(Map<String, Object> state)` | `void` | - |
| `public Map<String, Object> saveState()` | `Map<String, Object>` | - |

### `RoundLevelCompressorConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class RoundLevelCompressorConfig`
- 说明：Configuration for the RoundLevelCompressor ContextProcessor.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `roundsThreshold` | `int` | `private` | `10` | Maximum number of consecutive dialogue rounds before compression is triggered. |
| `tokensThreshold` | `int` | `private` | `10000` | Maximum accumulated token count before compression is triggered. |
| `keepLastRound` | `boolean` | `private` | `true` | If true, the most recent user-assistant round is always preserved. |
| `customizedCompressionPrompt` | `String` | `private` | `-` | User-defined prompt template for round compression. |
| `model` | `ModelRequestConfig` | `private` | `-` | Model request configuration. |
| `modelClient` | `ModelClientConfig` | `private` | `-` | Optional client-level configuration for the model. |

## `com.openjiuwen.core.context.processor.offloader`

公开类型：`4`

### `MessageOffloader`

- 类型：`class`
- 声明：`public class MessageOffloader extends ContextProcessor`
- 说明：Offloads large messages by trimming their content and storing the originals in the offload buffer.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MessageOffloader(MessageOffloaderConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | - |
| `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | - |
| `public void loadState(Map<String, Object> state)` | `void` | - |
| `public Map<String, Object> saveState()` | `Map<String, Object>` | - |
| `protected BaseMessage offloadMessage(BaseMessage message, ModelContext context)` | `BaseMessage` | Offload a single message. |
| `protected void validateConfig()` | `void` | - |

### `MessageOffloaderConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MessageOffloaderConfig`
- 说明：Configuration for the MessageOffloader ContextProcessor.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `messagesThreshold` | `Integer` | `private` | `-` | Maximum number of messages allowed before offloading is triggered. |
| `tokensThreshold` | `int` | `private` | `20000` | Maximum accumulated token count before offloading is triggered. |
| `largeMessageThreshold` | `int` | `private` | `1000` | Messages whose token count exceeds this value are considered 'large'. |
| `offloadMessageType` | `List<String>` | `private` | `List.of("tool")` | Roles eligible for offloading (e.g., "user", "assistant", "tool"). |
| `trimSize` | `int` | `private` | `100` | Number of tokens to retain when a message is offloaded. |
| `messagesToKeep` | `Integer` | `private` | `-` | Number of most-recent messages to retain regardless of thresholds. |
| `keepLastRound` | `boolean` | `private` | `true` | If true, the most recent user-assistant round is always preserved. |

### `MessageSummaryOffloader`

- 类型：`class`
- 声明：`public class MessageSummaryOffloader extends MessageOffloader`
- 说明：Extends MessageOffloader to use an LLM for generating summarized replacement content instead of simple trimming.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MessageSummaryOffloader(MessageSummaryOffloaderConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected BaseMessage offloadMessage(BaseMessage message, ModelContext context)` | `BaseMessage` | - |
| `protected void validateConfig()` | `void` | - |

### `MessageSummaryOffloaderConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MessageSummaryOffloaderConfig`
- 说明：Configuration for the MessageSummaryOffloader ContextProcessor.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `messagesThreshold` | `Integer` | `private` | `-` | Hard ceiling on message count. |
| `tokensThreshold` | `int` | `private` | `20000` | Hard ceiling on accumulated tokens. |
| `largeMessageThreshold` | `int` | `private` | `1000` | Token length above which a single message is labelled large. |
| `offloadMessageType` | `List<String>` | `private` | `List.of("tool")` | White-list of roles that may be compressed or off-loaded. |
| `messagesToKeep` | `Integer` | `private` | `-` | Guarantee that the newest N messages are never off-loaded. |
| `keepLastRound` | `boolean` | `private` | `true` | If true, the latest user-assistant round is immune to off-loading. |
| `model` | `ModelRequestConfig` | `private` | `-` | Model request configuration. |
| `modelClient` | `ModelClientConfig` | `private` | `-` | Optional client-level configuration. |
| `customizedSummaryPrompt` | `String` | `private` | `-` | User-supplied prompt for the summary model. |

## `com.openjiuwen.core.context.schema`

公开类型：`7`

### `ContextEngineConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ContextEngineConfig`
- 说明：Configuration for the ContextEngine.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `maxContextMessageNum` | `Integer` | `private` | `-` | Maximum number of messages retained in the context message buffer. |
| `defaultWindowMessageNum` | `Integer` | `private` | `-` | Default window size (number of messages) when building a context window. |
| `defaultWindowRoundNum` | `Integer` | `private` | `-` | Default number of dialogue rounds to keep in the context window. |
| `enableKvCacheRelease` | `boolean` | `private` | `false` | Whether to enable KV cache release optimisation for inference-affinity models. |
| `enableReload` | `boolean` | `private` | `false` | Whether to enable the reload tool that can re-inject offloaded messages. |

### `OffloadMessages`

- 类型：`class`
- 声明：`public final class OffloadMessages`
- 说明：Mixin / marker interface for offloaded messages.
- 嵌套公开类型：`OffloadMessages.OffloadUserMessage`、`OffloadMessages.OffloadAssistantMessage`、`OffloadMessages.OffloadSystemMessage`、`OffloadMessages.OffloadToolMessage`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle, String offloadType)` | `BaseMessage` | Create an offloaded message of the appropriate type based on role. |

### `OffloadMessages.OffloadAssistantMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public static class OffloadAssistantMessage extends AssistantMessage implements OffloadMixin`
- 说明：Assistant message that has been offloaded.
- 宿主类型：`OffloadMessages`
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `offloadType` | `String` | `private` | `-` | - |
| `offloadHandle` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |

### `OffloadMessages.OffloadSystemMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public static class OffloadSystemMessage extends SystemMessage implements OffloadMixin`
- 说明：System message that has been offloaded.
- 宿主类型：`OffloadMessages`
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `offloadType` | `String` | `private` | `-` | - |
| `offloadHandle` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |

### `OffloadMessages.OffloadToolMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public static class OffloadToolMessage extends ToolMessage implements OffloadMixin`
- 说明：Tool message that has been offloaded.
- 宿主类型：`OffloadMessages`
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `offloadType` | `String` | `private` | `-` | - |
| `offloadHandle` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |

### `OffloadMessages.OffloadUserMessage`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @JsonInclude(JsonInclude.Include.NON_NULL) public static class OffloadUserMessage extends UserMessage implements OffloadMixin`
- 说明：User message that has been offloaded.
- 宿主类型：`OffloadMessages`
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@JsonInclude`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `offloadType` | `String` | `private` | `-` | - |
| `offloadHandle` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |

### `OffloadMixin`

- 类型：`interface`
- 声明：`public interface OffloadMixin`
- 说明：Marker interface for messages that have been offloaded from the context window.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `String getOffloadType()` | `String` | Storage type (e.g., "in_memory"). |
| `String getOffloadHandle()` | `String` | Unique handle to retrieve offloaded content. |
| `Map<String, Object> getMetadata()` | `Map<String, Object>` | Arbitrary metadata attached to the offloaded message. |

## `com.openjiuwen.core.context.token`

公开类型：`2`

### `SimpleTokenCounter`

- 类型：`class`
- 声明：`public class SimpleTokenCounter extends TokenCounter`
- 说明：A simple token counter that estimates token count based on character length.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SimpleTokenCounter()` | - |
| `public SimpleTokenCounter(String model)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int count(String text, String model)` | `int` | - |
| `public int countMessages(List<BaseMessage> messages, String model)` | `int` | - |
| `public int countTools(List<ToolInfo> tools, String model)` | `int` | - |

### `TokenCounter`

- 类型：`class`
- 声明：`public abstract class TokenCounter`
- 说明：Abstract base class for token counting.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract int count(String text, String model)` | `int` | Count the number of tokens in a plain text string. |
| `public int count(String text)` | `int` | Count tokens with default model. |
| `public abstract int countMessages(List<BaseMessage> messages, String model)` | `int` | Count the total tokens across a list of messages. |
| `public int countMessages(List<BaseMessage> messages)` | `int` | Count messages tokens with default model. |
| `public abstract int countTools(List<ToolInfo> tools, String model)` | `int` | Count the total tokens across a list of tool definitions. |
| `public int countTools(List<ToolInfo> tools)` | `int` | Count tool tokens with default model. |

