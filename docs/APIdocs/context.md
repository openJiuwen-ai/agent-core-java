# Context 模块 API 文档

> 包路径：`com.openjiuwen.core.context`

Context 模块提供对话上下文的完整生命周期管理，包括上下文引擎、消息处理器、上下文窗口构建、Token 计数以及消息压缩/卸载等能力。

---

## 目录

- [1. 核心类](#1-核心类)
- [2. 上下文实现（context）](#2-上下文实现context)
- [3. 处理器（processor）](#3-处理器processor)
- [4. 压缩器（compressor）](#4-压缩器compressor)
- [5. 卸载器（offloader）](#5-卸载器offloader)
- [6. 配置与模式（schema）](#6-配置与模式schema)
- [7. Token 计数器（token）](#7-token-计数器token)

---

## 1. 核心类

### 1.1 ContextEngine

对话上下文生命周期管理的核心入口，负责处理器注册、上下文创建和检索。

**包路径**：`com.openjiuwen.core.context`

**静态字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `PROCESSOR_FACTORY_MAP` | `Map<String, Function<Object, ContextProcessor>>` | 处理器工厂全局注册表 |
| `PROCESSOR_CLASS_MAP` | `Map<String, Class<? extends ContextProcessor>>` | 处理器类全局注册表 |

**构造方法**：
```java
ContextEngine()                           // 使用默认配置
ContextEngine(ContextEngineConfig config)  // 使用指定配置
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createContext(String contextId, Session session, List<ProcessorSpec> processors, List<BaseMessage> historyMessages, TokenCounter tokenCounter)` | `ModelContext` | 创建或获取 ModelContext |
| `createContext(String contextId, Session session)` | `ModelContext` | 使用默认参数创建上下文 |
| `getContext(String contextId, String sessionId)` | `ModelContext` | 检索已有的 ModelContext |
| `clearContext(String contextId, String sessionId)` | `void` | 从上下文池中移除上下文 |
| `saveContexts(Session session, List<String> contextIds)` | `void` | 批量持久化多个上下文 |

**静态方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass, Function<Object, ContextProcessor> factory)` | `void` | 注册处理器（含工厂） |
| `registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass)` | `void` | 注册处理器（不含工厂） |
| `getProcessorClass(String processorType)` | `Class<? extends ContextProcessor>` | 根据类型获取已注册处理器类 |

**内部记录**：
```java
record ProcessorSpec(String processorType, Object config)
```

### 1.2 ContextStats

Token 使用量统计快照（Lombok `@Data`、`@Builder`）。

**包路径**：`com.openjiuwen.core.context`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `totalMessages` | `int` | 0 | 消息总数 |
| `totalTokens` | `int` | 0 | Token 总数 |
| `totalDialogues` | `int` | 0 | 对话轮次总数 |
| `systemMessages` | `int` | 0 | 系统消息数 |
| `userMessages` | `int` | 0 | 用户消息数 |
| `assistantMessages` | `int` | 0 | 助手消息数 |
| `toolMessages` | `int` | 0 | 工具消息数 |
| `tools` | `int` | 0 | 工具数 |
| `systemMessageTokens` | `int` | 0 | 系统消息 Token 数 |
| `userMessageTokens` | `int` | 0 | 用户消息 Token 数 |
| `assistantMessageTokens` | `int` | 0 | 助手消息 Token 数 |
| `toolMessageTokens` | `int` | 0 | 工具消息 Token 数 |
| `toolTokens` | `int` | 0 | 工具定义 Token 数 |

### 1.3 ContextWindow

轻量级可序列化的上下文窗口快照，用于发送给 LLM 端点。

**包路径**：`com.openjiuwen.core.context`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `systemMessages` | `List<BaseMessage>` | 空列表 | 系统消息列表 |
| `contextMessages` | `List<BaseMessage>` | 空列表 | 上下文消息列表 |
| `tools` | `List<ToolInfo>` | 空列表 | 工具定义列表 |
| `statistic` | `ContextStats` | 新实例 | 统计信息 |

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getMessages()` | `List<BaseMessage>` | 获取所有消息（系统 + 上下文） |
| `getToolList()` | `List<ToolInfo>` | 获取工具定义列表 |

### 1.4 ModelContext

管理对话上下文的抽象基类，提供模型无关的消息管理接口。

**包路径**：`com.openjiuwen.core.context`

**抽象方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `size()` | `int` | 返回上下文长度 |
| `getMessages(Integer size, boolean withHistory)` | `List<BaseMessage>` | 获取消息（不移除） |
| `setMessages(List<BaseMessage> messages, boolean withHistory)` | `void` | 替换消息列表 |
| `popMessages(int size, boolean withHistory)` | `List<BaseMessage>` | 移除并返回最早的消息 |
| `clearMessages(boolean withHistory)` | `void` | 清空所有消息 |
| `addMessages(List<BaseMessage> messages)` | `List<BaseMessage>` | 添加消息到上下文 |
| `getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound)` | `ContextWindow` | 构建推理用上下文窗口 |
| `statistic()` | `ContextStats` | 计算上下文统计信息 |
| `sessionId()` | `String` | 返回会话标识 |
| `contextId()` | `String` | 返回上下文标识 |
| `tokenCounter()` | `TokenCounter` | 返回 Token 计数器实例 |
| `reloaderTool()` | `Tool` | 返回重新加载已卸载消息的工具 |

**便捷方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getMessages()` | `List<BaseMessage>` | 获取所有消息（含历史） |
| `setMessages(List<BaseMessage> messages)` | `void` | 设置消息（含历史） |
| `popMessages()` | `List<BaseMessage>` | 弹出一条消息 |
| `addMessages(BaseMessage message)` | `List<BaseMessage>` | 添加单条消息 |
| `getContextWindow()` | `ContextWindow` | 使用默认参数获取上下文窗口 |

---

## 2. 上下文实现（context）

### 2.1 SessionModelContext

`ModelContext` 的核心实现，基于消息缓冲区，支持处理器、卸载和 KV 缓存管理。

**包路径**：`com.openjiuwen.core.context.context`  
**继承**：`ModelContext`

**构造方法**：
```java
SessionModelContext(String contextId, String sessionId, ContextEngineConfig config,
                    List<BaseMessage> historyMessages, List<ContextProcessor> processors,
                    TokenCounter tokenCounter)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `size()` | `int` | 返回消息缓冲区大小 |
| `sessionId()` | `String` | 返回会话 ID |
| `contextId()` | `String` | 返回上下文 ID |
| `addMessages(List<BaseMessage> messages)` | `List<BaseMessage>` | 添加消息并触发处理器 |
| `popMessages(int size, boolean withHistory)` | `List<BaseMessage>` | 从缓冲区弹出消息 |
| `getMessages(Integer size, boolean withHistory)` | `List<BaseMessage>` | 从缓冲区获取消息 |
| `setMessages(List<BaseMessage> messages, boolean withHistory)` | `void` | 设置缓冲区消息 |
| `clearMessages(boolean withHistory)` | `void` | 清空所有消息和卸载缓冲区 |
| `getContextWindow(...)` | `ContextWindow` | 构建上下文窗口并触发处理器 |
| `statistic()` | `ContextStats` | 获取上下文统计信息 |
| `tokenCounter()` | `TokenCounter` | 返回 Token 计数器 |
| `reloaderTool()` | `Tool` | 返回重新加载工具 |
| `offloadMessages(String offloadHandle, List<BaseMessage> messages)` | `void` | 将消息卸载到内存缓冲区 |
| `saveState()` | `Map<String, Object>` | 保存上下文状态用于持久化 |
| `loadState(Map<String, Object> state)` | `void` | 从持久化数据加载上下文状态 |

### 2.2 ContextMessageBuffer

上下文消息缓冲区，支持历史消息追踪和大小限制。

**包路径**：`com.openjiuwen.core.context.context`

**构造方法**：
```java
ContextMessageBuffer(List<BaseMessage> historyMessages, Integer maxBufferSize)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `size()` | `int` | 返回有效缓冲区大小 |
| `addBack(List<BaseMessage> messages)` | `void` | 在尾部追加消息 |
| `getBack(Integer size, boolean withHistory)` | `List<BaseMessage>` | 从尾部获取消息 |
| `getBack()` | `List<BaseMessage>` | 获取所有消息 |
| `popBack(int size, boolean withHistory)` | `List<BaseMessage>` | 从尾部弹出消息 |
| `setMessages(List<BaseMessage> messages, boolean withHistory)` | `void` | 替换消息 |
| `rebuild(List<BaseMessage> historyMessages)` | `void` | 从历史消息重建 |

### 2.3 ContextUtils

上下文操作静态工具类。

**包路径**：`com.openjiuwen.core.context.context`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `findLastAiMessageWithoutToolCall(List<BaseMessage> messages)` | `Optional<Integer>` | 查找最后一条无工具调用的助手消息索引 |
| `replaceMessages(List<BaseMessage> messages, List<BaseMessage> targetMessages, int startIndex, int endIndex)` | `List<BaseMessage>` | 替换消息范围 |
| `formatReloadedMessages(String offloadHandle, List<BaseMessage> messages)` | `String` | 格式化重新加载的消息 |
| `findAllDialogueRound(List<BaseMessage> messages)` | `List<int[]>` | 查找所有对话轮次 |
| `findLastNDialogueRound(List<BaseMessage> messages, int n)` | `int` | 查找最后 N 轮对话的起始索引 |

### 2.4 KVCacheManager

KV 缓存释放管理器，通过追踪上下文窗口变化来管理推理亲和模型的 KV 缓存。

**包路径**：`com.openjiuwen.core.context.context`

**构造方法**：
```java
KVCacheManager(String sessionId)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `release(ContextWindow contextWindow)` | `void` | 检查并释放过期的 KV 缓存 |

### 2.5 OffloadMessageBuffer

已卸载消息的缓冲区，支持内存存储。

**包路径**：`com.openjiuwen.core.context.context`

**构造方法**：
```java
OffloadMessageBuffer()
OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `offload(String offloadHandle, String offloadType, List<BaseMessage> messages)` | `void` | 将消息卸载到存储 |
| `reload(String offloadHandle, String offloadType)` | `List<BaseMessage>` | 从存储重新加载消息 |
| `clear(String offloadHandle, String offloadType)` | `void` | 清除特定卸载消息集 |
| `getAll()` | `Map<String, List<BaseMessage>>` | 获取所有已卸载消息 |

---

## 3. 处理器（processor）

### 3.1 ContextProcessor

所有上下文处理插件的抽象基类。处理器在两个生命周期点介入：消息添加时和上下文窗口构建时。

**包路径**：`com.openjiuwen.core.context.processor`

**构造方法**：
```java
ContextProcessor(Object config)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | 转换传入的消息批次（默认无操作） |
| `onGetContextWindow(ModelContext context, ContextWindow contextWindow)` | `ProcessResult` | 变更输出的上下文窗口（默认无操作） |
| `triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | 是否在追加前介入 |
| `triggerGetContextWindow(ModelContext context, ContextWindow contextWindow)` | `boolean` | 是否在返回上下文窗口前介入 |
| `loadState(Map<String, Object> state)` | `void` | 恢复内部状态（抽象） |
| `saveState()` | `Map<String, Object>` | 导出内部状态（抽象） |
| `processorType()` | `String` | 返回已注册的处理器类型字符串 |
| `getConfig()` | `<T> T` | 获取处理器配置（只读） |
| `offloadMessages(String role, String content, List<BaseMessage> messages, ModelContext context, String offloadHandle, String offloadType)` | `BaseMessage` | 卸载消息并返回替换标记 |

**内部记录**：
```java
record ProcessResult(ContextEvent event, List<BaseMessage> messages, ContextWindow contextWindow)
```

### 3.2 ContextEvent

处理器发出的事件，描述已修改的内容。

**包路径**：`com.openjiuwen.core.context.processor`

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventType` | `String` | 事件类型 |
| `messagesToModify` | `List<Integer>` | 被修改消息的索引列表 |

---

## 4. 压缩器（compressor）

### 4.1 CurrentRoundCompressor

当前对话轮次压缩器，在 Token 或消息数超出预算时压缩当前轮次的消息。

**包路径**：`com.openjiuwen.core.context.processor.compressor`  
**继承**：`ContextProcessor`

**构造方法**：
```java
CurrentRoundCompressor(CurrentRoundCompressorConfig config)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | 超出阈值时触发 |
| `onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | 执行消息压缩 |

**配置类 CurrentRoundCompressorConfig**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `messagesThreshold` | `Integer` | null | 触发压缩的最大消息数 |
| `tokensThreshold` | `int` | 10000 | 触发压缩的最大 Token 数 |
| `messagesToKeep` | `Integer` | null | 无论如何保留的消息数 |
| `largeMessageThreshold` | `int` | 1000 | "大消息"的 Token 阈值 |
| `customizedCompressionPrompt` | `String` | null | 自定义压缩提示词 |
| `singleMultiCompression` | `boolean` | false | 单条 vs 批量压缩 |
| `model` | `ModelRequestConfig` | null | 模型请求配置 |
| `modelClient` | `ModelClientConfig` | null | 模型客户端配置 |

### 4.2 DialogueCompressor

对话轮次压缩器，压缩已完成的对话轮次（用户提问→工具调用→助手回答）。

**包路径**：`com.openjiuwen.core.context.processor.compressor`  
**继承**：`ContextProcessor`

**构造方法**：
```java
DialogueCompressor(DialogueCompressorConfig config)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | 超出阈值时触发 |
| `onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | 压缩对话轮次 |
| `getCompressPairs(List<BaseMessage> messages)` | `List<int[]>` | 查找用户→助手消息对（静态） |

**配置类 DialogueCompressorConfig**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `messagesThreshold` | `Integer` | null | 触发压缩的最大消息数 |
| `tokensThreshold` | `int` | 10000 | 触发压缩的最大 Token 数 |
| `messagesToKeep` | `Integer` | null | 保留的消息数 |
| `keepLastRound` | `boolean` | true | 保留最近一轮 |
| `customizedCompressionPrompt` | `String` | null | 自定义压缩提示词 |
| `compressionTokenLimit` | `int` | 2000 | 摘要最大 Token 数 |
| `model` | `ModelRequestConfig` | null | 模型请求配置 |
| `modelClient` | `ModelClientConfig` | null | 模型客户端配置 |

### 4.3 RoundLevelCompressor

多轮次级别压缩器，将相同压缩级别的多个连续对话轮次压缩为单一摘要轮次。

**包路径**：`com.openjiuwen.core.context.processor.compressor`  
**继承**：`ContextProcessor`

**构造方法**：
```java
RoundLevelCompressor(RoundLevelCompressorConfig config)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | 轮次数和 Token 超出阈值时触发 |
| `onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | 压缩多个轮次 |

**内部记录**：
```java
record DialogueRound(BaseMessage user, BaseMessage ai, Integer level, int startIdx, int endIdx)
record CompressResult(List<BaseMessage> messages, List<Integer> allStarts, List<Integer> allEnds)
```

**配置类 RoundLevelCompressorConfig**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `roundsThreshold` | `int` | 10 | 触发压缩的最大连续轮次数 |
| `tokensThreshold` | `int` | 10000 | 触发压缩的最大 Token 数 |
| `keepLastRound` | `boolean` | true | 保留最近一轮 |
| `customizedCompressionPrompt` | `String` | null | 自定义压缩提示词 |
| `model` | `ModelRequestConfig` | null | 模型请求配置 |
| `modelClient` | `ModelClientConfig` | null | 模型客户端配置 |

---

## 5. 卸载器（offloader）

### 5.1 MessageOffloader

消息卸载器，将大消息内容裁剪并将原始内容存储到卸载缓冲区。

**包路径**：`com.openjiuwen.core.context.processor.offloader`  
**继承**：`ContextProcessor`

**构造方法**：
```java
MessageOffloader(MessageOffloaderConfig config)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `boolean` | 消息数或 Token 超出阈值时触发 |
| `onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | `ProcessResult` | 卸载大消息 |

**配置类 MessageOffloaderConfig**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `messagesThreshold` | `Integer` | null | 触发卸载的最大消息数 |
| `tokensThreshold` | `int` | 20000 | 触发卸载的最大 Token 数 |
| `largeMessageThreshold` | `int` | 1000 | "大消息"的 Token 阈值 |
| `offloadMessageType` | `List<String>` | `["tool"]` | 可卸载的消息角色 |
| `trimSize` | `int` | 100 | 卸载时保留的 Token 数 |
| `messagesToKeep` | `Integer` | null | 无论如何保留的消息数 |
| `keepLastRound` | `boolean` | true | 保留最近一轮 |

### 5.2 MessageSummaryOffloader

基于 LLM 摘要的消息卸载器，使用大模型生成摘要替代简单裁剪。

**包路径**：`com.openjiuwen.core.context.processor.offloader`  
**继承**：`MessageOffloader`

**构造方法**：
```java
MessageSummaryOffloader(MessageSummaryOffloaderConfig config)
```

**配置类 MessageSummaryOffloaderConfig**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `messagesThreshold` | `Integer` | null | 触发卸载的最大消息数 |
| `tokensThreshold` | `int` | 20000 | 触发卸载的最大 Token 数 |
| `largeMessageThreshold` | `int` | 1000 | "大消息"的 Token 阈值 |
| `offloadMessageType` | `List<String>` | `["tool"]` | 可卸载的消息角色 |
| `messagesToKeep` | `Integer` | null | 保留的消息数 |
| `keepLastRound` | `boolean` | true | 保留最近一轮 |
| `model` | `ModelRequestConfig` | null | 模型请求配置 |
| `modelClient` | `ModelClientConfig` | null | 模型客户端配置 |
| `customizedSummaryPrompt` | `String` | null | 自定义摘要提示词 |

---

## 6. 配置与模式（schema）

### 6.1 ContextEngineConfig

ContextEngine 的配置类。

**包路径**：`com.openjiuwen.core.context.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxContextMessageNum` | `Integer` | null | 上下文缓冲区最大消息数（null = 无限） |
| `defaultWindowMessageNum` | `Integer` | null | 默认窗口消息大小（null = 无限） |
| `defaultWindowRoundNum` | `Integer` | null | 默认保留对话轮次数（null = 无限） |
| `enableKvCacheRelease` | `boolean` | false | 启用 KV 缓存优化 |
| `enableReload` | `boolean` | false | 启用已卸载消息的重新加载工具 |

### 6.2 OffloadMixin

已卸载消息的标记接口。

**包路径**：`com.openjiuwen.core.context.schema`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getOffloadType()` | `String` | 获取存储类型（如 "in_memory"） |
| `getOffloadHandle()` | `String` | 获取用于检索内容的唯一句柄 |
| `getMetadata()` | `Map<String, Object>` | 获取任意元数据 |

### 6.3 OffloadMessages

已卸载消息的工厂和消息类型定义。

**包路径**：`com.openjiuwen.core.context.schema`

**内部类**（均实现 `OffloadMixin`）：
- `OffloadUserMessage` — 继承 `UserMessage`
- `OffloadAssistantMessage` — 继承 `AssistantMessage`
- `OffloadSystemMessage` — 继承 `SystemMessage`
- `OffloadToolMessage` — 继承 `ToolMessage`

**静态工厂方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createOffloadMessage(String role, String content, String offloadHandle, String offloadType)` | `BaseMessage` | 创建适当类型的卸载消息 |

---

## 7. Token 计数器（token）

### 7.1 TokenCounter

Token 计数的抽象基类，提供文本、消息和工具定义的统一计数接口。

**包路径**：`com.openjiuwen.core.context.token`

**抽象方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `count(String text, String model)` | `int` | 计算纯文本的 Token 数 |
| `countMessages(List<BaseMessage> messages, String model)` | `int` | 计算消息列表的 Token 数 |
| `countTools(List<ToolInfo> tools, String model)` | `int` | 计算工具定义的 Token 数 |

**便捷方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `count(String text)` | `int` | 使用默认模型计算 Token 数 |
| `countMessages(List<BaseMessage> messages)` | `int` | 使用默认模型计算消息 Token 数 |
| `countTools(List<ToolInfo> tools)` | `int` | 使用默认模型计算工具 Token 数 |

### 7.2 SimpleTokenCounter

简单 Token 计数器，使用字符长度启发式算法（约 4 字符/Token）作为回退实现。

**包路径**：`com.openjiuwen.core.context.token`  
**继承**：`TokenCounter`

**常量**：

| 常量 | 值 | 说明 |
|------|----|------|
| `CHARS_PER_TOKEN` | 4 | 每 Token 字符数估算 |
| `MESSAGE_OVERHEAD` | 4 | 每条消息额外 Token 数 |
| `REPLY_OVERHEAD` | 3 | 消息列表末端额外 Token 数 |

**构造方法**：
```java
SimpleTokenCounter()           // 默认模型 "gpt-4"
SimpleTokenCounter(String model)  // 指定模型
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `count(String text, String model)` | `int` | 计算文本 Token 数 |
| `countMessages(List<BaseMessage> messages, String model)` | `int` | 计算消息 Token 数（含消息开销） |
| `countTools(List<ToolInfo> tools, String model)` | `int` | 计算工具定义 Token 数 |
