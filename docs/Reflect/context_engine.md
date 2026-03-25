# context_engine Python/Java API 映射

## 1. 对照范围

- Python 版目录: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\context_engine`
- Java 版目录: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\context`

本对照文档按“模块/类/方法/字段”四个层次整理，重点说明：

1. Python `snake_case` 与 Java `camelCase` 的命名映射。
2. Python `async` 接口在 Java 中对应同步方法。
3. Python `Optional[...]` / `None` 在 Java 中通常对应可空 `Integer`、`List`、`Map` 等。
4. Python 的 `**kwargs`、装饰器和元类，在 Java 中多由重载、显式工厂、`record`、内部类替代。

## 2. 模块总览映射

| Python 模块/类型 | Java 类型 | 说明 |
| --- | --- | --- |
| `schema.config.ContextEngineConfig` | `schema.ContextEngineConfig` | 引擎配置对象 |
| `base.ModelContext` | `ModelContext` | 上下文抽象基类 |
| `base.ContextStats` | `ContextStats` | 统计信息模型 |
| `base.ContextWindow` | `ContextWindow` | 推理窗口模型 |
| `context_engine.ContextEngine` | `ContextEngine` | 上下文引擎入口 |
| `context.context.SessionModelContext` | `context.SessionModelContext` | 默认会话上下文实现 |
| `context.message_buffer.ContextMessageBuffer` | `context.ContextMessageBuffer` | 消息缓冲区 |
| `context.message_buffer.OffloadMessageBuffer` | `context.OffloadMessageBuffer` | offload 缓冲区 |
| `context.context_utils.ContextUtils` | `context.ContextUtils` | 上下文工具类 |
| `context.kv_cache_manager.KVCacheManager` | `context.KVCacheManager` | KV cache 释放管理器 |
| `processor.base.ContextEvent` | `processor.ContextEvent` | 处理器事件对象 |
| `processor.base.ContextProcessor` | `processor.ContextProcessor` | 处理器抽象基类 |
| `processor.base.MetaContextProcessor` | 无直接类 | Java 用 `processorType()` + 静态注册替代元类行为 |
| `processor.compressor.CurrentRoundCompressorConfig` | `processor.compressor.CurrentRoundCompressorConfig` | 当前轮压缩器配置 |
| `processor.compressor.CurrentRoundCompressor` | `processor.compressor.CurrentRoundCompressor` | 当前轮压缩器 |
| `processor.compressor.DialogueCompressorConfig` | `processor.compressor.DialogueCompressorConfig` | 对话压缩器配置 |
| `processor.compressor.DialogueCompressor` | `processor.compressor.DialogueCompressor` | 对话压缩器 |
| `processor.compressor.DialogueRound` | `RoundLevelCompressor.DialogueRound` | Java 里改成内部 `record` |
| `processor.compressor.RoundLevelCompressorConfig` | `processor.compressor.RoundLevelCompressorConfig` | 轮级压缩器配置 |
| `processor.compressor.RoundLevelCompressor` | `processor.compressor.RoundLevelCompressor` | 轮级压缩器 |
| `processor.offloader.MessageOffloaderConfig` | `processor.offloader.MessageOffloaderConfig` | 消息 offloader 配置 |
| `processor.offloader.MessageOffloader` | `processor.offloader.MessageOffloader` | 消息 offloader |
| `processor.offloader.MessageSummaryOffloaderConfig` | `processor.offloader.MessageSummaryOffloaderConfig` | 摘要 offloader 配置 |
| `processor.offloader.MessageSummaryOffloader` | `processor.offloader.MessageSummaryOffloader` | 摘要 offloader |
| `schema.messages.OffloadMixin` | `schema.OffloadMixin` | offload 占位消息混入接口 |
| `schema.messages.OffloadUserMessage` 等四类 | `schema.OffloadMessages.OffloadUserMessage` 等四个内部类 | Java 收敛到 `OffloadMessages` 外壳类中 |
| `schema.messages.create_offload_message` | `schema.OffloadMessages.createOffloadMessage` | offload 消息工厂 |
| `token.base.TokenCounter` | `token.TokenCounter` | token 计数器抽象基类 |
| `token.tiktoken_counter.TiktokenCounter` | `token.SimpleTokenCounter` | Java 只有近似实现，没有精确 `tiktoken` 对应物 |

## 3. 核心模型层映射

### 3.1 `ContextEngineConfig`

| Python 字段 | Java 字段 | 说明 |
| --- | --- | --- |
| `max_context_message_num` | `maxContextMessageNum` | 上下文缓冲区最大消息数 |
| `default_window_message_num` | `defaultWindowMessageNum` | 默认窗口消息数 |
| `default_window_round_num` | `defaultWindowRoundNum` | 默认窗口轮数 |
| `enable_kv_cache_release` | `enableKvCacheRelease` | 是否启用 KV cache 释放 |
| `enable_reload` | `enableReload` | 是否启用 reload 工具 |

### 3.2 `ModelContext`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__len__()` | `size()` | 返回上下文消息数 |
| `get_messages(size=None, with_history=True)` | `getMessages(Integer size, boolean withHistory)` | 获取消息 |
| 无 | `getMessages()` | Java 增加了无参便利方法 |
| `set_messages(messages, with_history=True)` | `setMessages(List<BaseMessage> messages, boolean withHistory)` | 设置消息 |
| 无 | `setMessages(List<BaseMessage> messages)` | Java 增加了无参便利方法 |
| `pop_messages(size=1, with_history=True)` | `popMessages(int size, boolean withHistory)` | 弹出消息 |
| 无 | `popMessages()` | Java 增加了弹出 1 条的便利方法 |
| `clear_messages(with_history=True)` | `clearMessages(boolean withHistory)` | 清空消息 |
| `add_messages(message_or_list)` | `addMessages(List<BaseMessage> messages)` | Python 为异步，Java 为同步 |
| 无 | `addMessages(BaseMessage message)` | Java 增加单条便利重载 |
| `get_context_window(system_messages=None, tools=None, window_size=None, dialogue_round=None, **kwargs)` | `getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound)` | Java 少了 `**kwargs` 扩展参数 |
| 无 | `getContextWindow()` | Java 增加无参便利方法 |
| `statistic()` | `statistic()` | 统计信息 |
| `session_id()` | `sessionId()` | 会话 ID |
| `context_id()` | `contextId()` | 上下文 ID |
| `token_counter()` | `tokenCounter()` | token 计数器 |
| `reloader_tool()` | `reloaderTool()` | reload 工具 |

### 3.3 `ContextStats`

| Python 字段 | Java 字段 |
| --- | --- |
| `total_messages` | `totalMessages` |
| `total_tokens` | `totalTokens` |
| `total_dialogues` | `totalDialogues` |
| `system_messages` | `systemMessages` |
| `user_messages` | `userMessages` |
| `assistant_messages` | `assistantMessages` |
| `tool_messages` | `toolMessages` |
| `tools` | `tools` |
| `system_message_tokens` | `systemMessageTokens` |
| `user_message_tokens` | `userMessageTokens` |
| `assistant_message_tokens` | `assistantMessageTokens` |
| `tool_message_tokens` | `toolMessageTokens` |
| `tool_tokens` | `toolTokens` |

### 3.4 `ContextWindow`

| Python API/字段 | Java API/字段 | 说明 |
| --- | --- | --- |
| `system_messages` | `systemMessages` | 系统消息列表 |
| `context_messages` | `contextMessages` | 上下文消息列表 |
| `tools` | `tools` | 工具列表 |
| `statistic` | `statistic` | 统计信息 |
| `get_messages()` | `getMessages()` | 返回 `system + context` |
| `get_tools()` | `getToolList()` | Java 方法名不同 |

## 4. 引擎层映射

### 4.1 `ContextEngine`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(config=None)` | `ContextEngine()` / `ContextEngine(ContextEngineConfig config)` | 构造器映射 |
| `create_context(context_id="default_context_id", session=None, *, processors=None, history_messages=None, token_counter=None)` | `createContext(String contextId, Session session, List<ProcessorSpec> processors, List<BaseMessage> historyMessages, TokenCounter tokenCounter)` | Python 异步，Java 同步 |
| 无 | `createContext(String contextId, Session session)` | Java 增加便利重载 |
| `get_context(context_id="default_context_id", session_id="default_session_id")` | `getContext(String contextId, String sessionId)` | 获取上下文 |
| `clear_context(context_id=None, session_id=None)` | `clearContext(String contextId, String sessionId)` | Java 通过 `null` 表达删除全部/按 session 删除 |
| `save_contexts(session, context_ids=None)` | `saveContexts(Session session, List<String> contextIds)` | 批量保存状态 |
| `register_processor()` 装饰器 | `registerProcessor(...)` 静态方法 | Java 用静态注册表替代装饰器 |
| `_create_processor(processor_type, config)` | `createProcessor(String processorType, Object processorConfig)` | 私有构造辅助 |
| `_load_state_from_session(context, session, history_messages=None)` | `loadStateFromSession(ModelContext context, Session session, List<BaseMessage> historyMessages)` | Java 目前只支持 `Session` 直连接口 |
| `_save_state_to_session(session, states)` | `saveStateToSession(Session session, Map<String, Object> states)` | 状态回写 |
| `_process_context_id(context_id)` | `processContextId(String contextId)` | `.` 替换为 `_` |
| `ContextEngine._PROCESSOR_MAP` | `PROCESSOR_FACTORY_MAP` + `PROCESSOR_CLASS_MAP` | Java 把“类型 -> 类/工厂”拆成两个注册表 |
| 无 | `ProcessorSpec(String processorType, Object config)` | Java 用 `record` 表示处理器规格 |

## 5. Context 子模块映射

### 5.1 `SessionModelContext`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(context_id, session_id, config, *, history_messages=None, processors=None, token_counter=None)` | `SessionModelContext(String contextId, String sessionId, ContextEngineConfig config, List<BaseMessage> historyMessages, List<ContextProcessor> processors, TokenCounter tokenCounter)` | 构造参数一一对应 |
| `__len__()` | `size()` | 长度 |
| `session_id()` | `sessionId()` | 会话 ID |
| `context_id()` | `contextId()` | 上下文 ID |
| `add_messages(messages, **kwargs)` | `addMessages(List<BaseMessage> messages)` | Java 未暴露 `kwargs` |
| `pop_messages(size=1, with_history=True)` | `popMessages(int size, boolean withHistory)` | 弹出消息 |
| `get_messages(size=None, with_history=True)` | `getMessages(Integer size, boolean withHistory)` | 获取消息 |
| `set_messages(messages, with_history=True)` | `setMessages(List<BaseMessage> messages, boolean withHistory)` | 设置消息 |
| `clear_messages(with_history=True)` | `clearMessages(boolean withHistory)` | 清空消息并重置 offload buffer |
| `get_context_window(..., **kwargs)` | `getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound)` | Java 少了 `kwargs` 传递链 |
| `_get_window_messages(...)` | `getWindowMessages(...)` | 私有窗口裁剪辅助 |
| `statistic()` | `statistic()` | 统计上下文 |
| `_stat_context_window(...)` | `statContextWindow(...)` | 统计窗口 |
| `_stat_tools(...)` | `statTools(...)` | 工具统计 |
| `_stat_messages(...)` | `statMessages(...)` | 消息统计 |
| `_validate_and_init_messages(...)` | `validateMessages(...)` | 输入校验 |
| `_validate_and_fix_context_window(...)` | `validateAndFixContextWindow(...)` | 清除前置 `ToolMessage` |
| `token_counter()` | `tokenCounter()` | token counter |
| `reloader_tool()` | `reloaderTool()` | reload 工具 |
| `offload_messages(offload_handle, messages)` | `offloadMessages(String offloadHandle, List<BaseMessage> messages)` | offload 到内存 |
| `save_state()` | `saveState()` | 保存状态 |
| `load_state(state)` | `loadState(Map<String, Object> state)` | 加载状态 |
| 无 | `ReloaderTool.invoke(...)` / `ReloaderTool.stream(...)` | Java 用内部 `Tool` 子类代替 Python `@tool` 装饰器函数 |

### 5.2 `ContextMessageBuffer`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(history_messages, max_buffer_size=None)` | `ContextMessageBuffer(List<BaseMessage> historyMessages, Integer maxBufferSize)` | 构造器 |
| `size()` | `size()` | 长度 |
| `add_back(message_or_messages)` | `addBack(List<BaseMessage> messages)` | Java 只保留列表版本 |
| `get_back(size=None, with_history=True)` | `getBack(Integer size, boolean withHistory)` | 获取尾部消息 |
| 无 | `getBack()` | Java 增加便利重载 |
| `pop_back(size=None, with_history=True)` | `popBack(int size, boolean withHistory)` | Java 这里没有 `size=None` 的“全部弹出”语义 |
| `set_messages(messages, with_history=True)` | `setMessages(List<BaseMessage> messages, boolean withHistory)` | 设置缓冲区 |
| `rebulid(history_messages)` | `rebuild(List<BaseMessage> historyMessages)` | Java 修正了 Python 中的拼写错误 |
| `_if_need_resize()` | `ifNeedResize()` | 自动裁剪 |

### 5.3 `OffloadMessageBuffer`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(init_messages=None)` | `OffloadMessageBuffer()` / `OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages)` | 构造器 |
| `offload(offload_handle, offload_type, messages)` | `offload(String offloadHandle, String offloadType, List<BaseMessage> messages)` | offload |
| `reload(offload_handle, offload_type)` | `reload(String offloadHandle, String offloadType)` | reload |
| `clear(offload_handle, offload_type)` | `clear(String offloadHandle, String offloadType)` | 清理 |
| `get_all()` | `getAll()` | 获取全部 offload 数据 |

### 5.4 `ContextUtils`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `find_last_ai_message_without_tool_call(messages)` | `findLastAiMessageWithoutToolCall(List<BaseMessage> messages)` | Java 返回 `Optional<Integer>` |
| `replace_messages(messages, target_messages, start_index, end_index)` | `replaceMessages(List<BaseMessage> messages, List<BaseMessage> targetMessages, int startIndex, int endIndex)` | 切片替换 |
| `format_reloaded_messages(offload_handle, messages)` | `formatReloadedMessages(String offloadHandle, List<BaseMessage> messages)` | reload 展示格式化 |
| `find_all_dialogue_round(messages)` | `findAllDialogueRound(List<BaseMessage> messages)` | Java 使用 `List<int[]>` 承载 `[userIdx, assistantIdx]` |
| `find_last_n_dialogue_round(messages, n)` | `findLastNDialogueRound(List<BaseMessage> messages, int n)` | 查找最近第 N 轮起点 |
| 无 | `hasToolCalls(BaseMessage msg)` | Java 把工具调用判断抽成私有辅助 |

### 5.5 `KVCacheManager`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(session_id)` | `KVCacheManager(String sessionId)` | 构造器 |
| `release(context_window, **kwargs)` | `release(ContextWindow contextWindow)` / `release(ContextWindow contextWindow, Object model)` | Java 方法存在，但公开调用链没有把 `model` 传下来 |
| `_check_release_needed(context_window)` | `checkReleaseNeeded(ContextWindow contextWindow)` | Java 用内部 `ReleaseCheckResult record` 封装返回值 |

## 6. Processor 基础层映射

### 6.1 `ContextEvent`

| Python 字段 | Java 字段 |
| --- | --- |
| `event_type` | `eventType` |
| `messages_to_modify` | `messagesToModify` |

### 6.2 `ContextProcessor`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(config)` | `ContextProcessor(Object config)` | 保存配置 |
| `on_add_messages(context, messages_to_add, **kwargs)` | `onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | Java 为同步 |
| `on_get_context_window(context, context_window, **kwargs)` | `onGetContextWindow(ModelContext context, ContextWindow contextWindow)` | Java 为同步 |
| `trigger_add_messages(context, messages_to_add, **kwargs)` | `triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)` | 触发判断 |
| `trigger_get_context_window(context, context_window, **kwargs)` | `triggerGetContextWindow(ModelContext context, ContextWindow contextWindow)` | 触发判断 |
| `load_state(state)` | `loadState(Map<String, Object> state)` | 加载状态 |
| `save_state()` | `saveState()` | 保存状态 |
| `processor_type()` | `processorType()` | Java 用类名替代 Python 元类注入属性 |
| `config` 属性 | `getConfig()` | Java 返回泛型配置对象 |
| `offload_messages(role, content, messages, *, context=None, offload_handle=None, offload_type="in_memory", **kwargs)` | `offloadMessages(String role, String content, List<BaseMessage> messages, ModelContext context, String offloadHandle, String offloadType)` | Java 没有 `**kwargs` 透传 |
| `_offload_messages_to_memory(...)` | `offloadMessagesToMemory(...)` | Java 仅支持 `SessionModelContext` |
| 无 | `ProcessResult` | Java 用 `record` 显式承载 hook 返回值 |

### 6.3 `MetaContextProcessor`

| Python API | Java 对应 | 说明 |
| --- | --- | --- |
| `MetaContextProcessor.__new__` | 无直接类 | Java 不需要元类，`processorType()` 直接返回简单类名 |

## 7. Compressor 映射

### 7.1 `CurrentRoundCompressorConfig`

| Python 字段 | Java 字段 |
| --- | --- |
| `messages_threshold` | `messagesThreshold` |
| `tokens_threshold` | `tokensThreshold` |
| `messages_to_keep` | `messagesToKeep` |
| `large_message_threshold` | `largeMessageThreshold` |
| `customized_compression_prompt` | `customizedCompressionPrompt` |
| `single_multi_compression` | `singleMultiCompression` |
| `model` | `model` |
| `model_client` | `modelClient` |

### 7.2 `CurrentRoundCompressor`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(config)` | `CurrentRoundCompressor(CurrentRoundCompressorConfig config)` | 构造器 |
| `trigger_add_messages(...)` | `triggerAddMessages(...)` | 触发压缩 |
| `on_add_messages(...)` | `onAddMessages(...)` | 执行压缩 |
| `load_state(...)` | `loadState(...)` | 无状态实现 |
| `save_state()` | `saveState()` | 无状态实现 |
| `get_compress_idx(messages)` | `getCompressIdx(List<BaseMessage> messages)` | 私有辅助 |
| `multi_compress(...)` | `multiCompress(...)` | 整段压缩 |
| `single_compress(...)` | `singleCompress(...)` | 单消息压缩 |
| `compress(messages_to_compress, context)` | `compress(List<BaseMessage> messagesToCompress, ModelContext context)` | 调模型生成摘要 |

### 7.3 `DialogueCompressorConfig`

| Python 字段 | Java 字段 |
| --- | --- |
| `messages_threshold` | `messagesThreshold` |
| `tokens_threshold` | `tokensThreshold` |
| `messages_to_keep` | `messagesToKeep` |
| `keep_last_round` | `keepLastRound` |
| `customized_compression_prompt` | `customizedCompressionPrompt` |
| `compression_token_limit` | `compressionTokenLimit` |
| `model` | `model` |
| `model_client` | `modelClient` |

### 7.4 `DialogueCompressor`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(config)` | `DialogueCompressor(DialogueCompressorConfig config)` | 构造器 |
| `trigger_add_messages(...)` | `triggerAddMessages(...)` | 触发压缩 |
| `on_add_messages(...)` | `onAddMessages(...)` | 执行压缩 |
| `get_compress_idx(messages)` | `getCompressIdx(List<BaseMessage> messages)` | 私有辅助 |
| `get_compress_pairs(messages)` | `getCompressPairs(List<BaseMessage> messages)` | Java 为包可见静态方法 |
| `_compress(messages_to_compress, context)` | `compress(List<BaseMessage> messagesToCompress, ModelContext context)` | 摘要生成 |
| `load_state(...)` | `loadState(...)` | 无状态 |
| `save_state()` | `saveState()` | 无状态 |

### 7.5 `RoundLevelCompressorConfig`

| Python 字段 | Java 字段 |
| --- | --- |
| `rounds_threshold` | `roundsThreshold` |
| `tokens_threshold` | `tokensThreshold` |
| `keep_last_round` | `keepLastRound` |
| `customized_compression_prompt` | `customizedCompressionPrompt` |
| `model` | `model` |
| `model_client` | `modelClient` |

### 7.6 `RoundLevelCompressor` 及辅助结构

| Python API | Java API | 说明 |
| --- | --- | --- |
| `filter_out_latest_round(rounds, preserve)` | `filterOutLatestRound(List<DialogueRound> rounds, boolean preserve)` | Python 顶层函数，Java 私有静态方法 |
| `DialogueRound` | `RoundLevelCompressor.DialogueRound` | Python dataclass，Java `record` |
| `__init__(config)` | `RoundLevelCompressor(RoundLevelCompressorConfig config)` | 构造器 |
| `trigger_add_messages(...)` | `triggerAddMessages(...)` | 触发压缩 |
| `on_add_messages(...)` | `onAddMessages(...)` | 执行压缩 |
| `_iter_rounds(messages)` | `iterRounds(List<BaseMessage> messages)` | 迭代轮次 |
| `_is_valid_dialogue_round(u, a)` | `isValidDialogueRound(BaseMessage u, BaseMessage a)` | 判定有效轮 |
| `_find_best_round_window(rounds)` | `findBestRoundWindow(List<DialogueRound> rounds)` | 查找可压缩窗口 |
| `_compress_round_pairs(rounds, context)` | `compressRoundPairs(List<DialogueRound> rounds, ModelContext context)` | 压缩多轮为一轮 |
| `_compress_rounds(messages, rounds, context)` | `compressRounds(List<BaseMessage> messages, List<List<DialogueRound>> targetWindows, ModelContext context)` | 批量替换 |
| `_compress_messages(messages, role, context)` | 无直接对应 | Java 未移植该私有辅助方法 |
| `_get_compress_level(message)` | `getCompressLevel(BaseMessage message)` | 从 metadata 读取压缩层级 |
| `load_state(...)` | `loadState(...)` | 无状态 |
| `save_state()` | `saveState()` | 无状态 |

## 8. Offloader 映射

### 8.1 `MessageOffloaderConfig`

| Python 字段 | Java 字段 |
| --- | --- |
| `messages_threshold` | `messagesThreshold` |
| `tokens_threshold` | `tokensThreshold` |
| `large_message_threshold` | `largeMessageThreshold` |
| `offload_message_type` | `offloadMessageType` |
| `trim_size` | `trimSize` |
| `messages_to_keep` | `messagesToKeep` |
| `keep_last_round` | `keepLastRound` |

### 8.2 `MessageOffloader`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(config)` | `MessageOffloader(MessageOffloaderConfig config)` | 构造器 |
| `trigger_add_messages(...)` | `triggerAddMessages(...)` | 触发 offload |
| `on_add_messages(...)` | `onAddMessages(...)` | 执行 offload |
| `_offload_large_messages(messages, context)` | `offloadLargeMessages(List<BaseMessage> messages, ModelContext context)` | 私有辅助 |
| `_offload_message(message, context)` | `offloadMessage(BaseMessage message, ModelContext context)` | Java 为 `protected`，便于子类覆写 |
| `_validate_config()` | `validateConfig()` | 校验阈值关系 |
| `load_state(...)` | `loadState(...)` | 无状态 |
| `save_state()` | `saveState()` | 无状态 |

### 8.3 `MessageSummaryOffloaderConfig`

| Python 字段 | Java 字段 |
| --- | --- |
| `messages_threshold` | `messagesThreshold` |
| `tokens_threshold` | `tokensThreshold` |
| `large_message_threshold` | `largeMessageThreshold` |
| `offload_message_type` | `offloadMessageType` |
| `messages_to_keep` | `messagesToKeep` |
| `keep_last_round` | `keepLastRound` |
| `model` | `model` |
| `model_client` | `modelClient` |
| `customized_summary_prompt` | `customizedSummaryPrompt` |

### 8.4 `MessageSummaryOffloader`

| Python API | Java API | 说明 |
| --- | --- | --- |
| `__init__(config)` | `MessageSummaryOffloader(MessageSummaryOffloaderConfig config)` | 构造器 |
| `_offload_message(message, context)` | `offloadMessage(BaseMessage message, ModelContext context)` | 用模型摘要替代裁剪 |
| `_validate_config()` | `validateConfig()` | 校验配置 |
| 无 | `toOffloaderConfig(MessageSummaryOffloaderConfig config)` | Java 为了复用父类新增转换辅助 |

## 9. Schema / Token 层映射

### 9.1 Offload 消息模型

| Python API/类型 | Java API/类型 | 说明 |
| --- | --- | --- |
| `OffloadMixin` | `OffloadMixin` | Python 是 `BaseModel` mixin，Java 是接口 |
| `OffloadUserMessage` | `OffloadMessages.OffloadUserMessage` | Java 改成 `OffloadMessages` 内部类 |
| `OffloadAssistantMessage` | `OffloadMessages.OffloadAssistantMessage` | 同上 |
| `OffloadSystemMessage` | `OffloadMessages.OffloadSystemMessage` | 同上 |
| `OffloadToolMessage` | `OffloadMessages.OffloadToolMessage` | 同上 |
| `create_offload_message(role, content, offload_handle, offload_type, **kwargs)` | `OffloadMessages.createOffloadMessage(String role, String content, String offloadHandle, String offloadType)` | Java 工厂目前没有 `**kwargs` 扩展字段入口 |

### 9.2 Token Counter

| Python API/类型 | Java API/类型 | 说明 |
| --- | --- | --- |
| `TokenCounter.count(text, *, model="", **kwargs)` | `count(String text, String model)` | Java 不支持 `**kwargs` |
| `TokenCounter.count_messages(messages, *, model="", **kwargs)` | `countMessages(List<BaseMessage> messages, String model)` | 消息 token 统计 |
| `TokenCounter.count_tools(tools, *, model="", **kwargs)` | `countTools(List<ToolInfo> tools, String model)` | 工具 token 统计 |
| `TiktokenCounter(model="gpt-4")` | `SimpleTokenCounter()` / `SimpleTokenCounter(String model)` | Java 为近似计数，不是精确 `tiktoken` |

## 10. 关键语义差异总结

### 10.1 命名与调用约定

- Python 普遍使用 `snake_case`，Java 普遍使用 `camelCase`。
- Python 的处理器与上下文接口大量使用 `async def`，Java 统一落成同步方法。
- Python 更依赖鸭子类型与 `hasattr()`；Java 更多依赖显式接口、`instanceof` 和构造器签名。

### 10.2 结构性改写

- Python `MetaContextProcessor` 元类在 Java 中没有直接对应物，等价语义由 `processorType()` 与静态注册表替代。
- Python `schema.messages.py` 把 offload 消息类平铺定义；Java 将其收束到 `OffloadMessages` 外层类下。
- Python `DialogueRound` 为模块级 dataclass；Java 将其改为 `RoundLevelCompressor` 的内部 `record`。
- Python 顶层函数 `filter_out_latest_round()` 在 Java 中改为私有静态方法 `filterOutLatestRound()`。

### 10.3 已对上但需要注意的行为差异

- Java `ContextWindow.getToolList()` 对应 Python `ContextWindow.get_tools()`，只是命名差异。
- Java `ContextMessageBuffer.rebuild()` 对应 Python `rebulid()`，Java 这里顺手修正了拼写。
- Java `ContextProcessor.ProcessResult` 是显式返回容器；Python 则直接返回 `(event, messages/window)` 元组。
- Java `SimpleTokenCounter` 只提供估算结果，因此阈值触发、压缩判定、统计信息与 Python `TiktokenCounter` 不一定完全一致。

## 11. 建议阅读顺序

如果后续要继续做 Python/Java 对齐，建议按下面顺序阅读源码与对照：

1. `ContextEngineConfig` -> `ContextEngine` -> `ModelContext`
2. `SessionModelContext` + `ContextMessageBuffer` + `ContextUtils`
3. `ContextProcessor` 基类与 `OffloadMessages`
4. `MessageOffloader` / `MessageSummaryOffloader`
5. `CurrentRoundCompressor` / `DialogueCompressor` / `RoundLevelCompressor`
6. `TokenCounter` / `KVCacheManager`

缺漏项与优先级已经单独整理到 `docs/FIXED/context_engine_fixed.md`。
