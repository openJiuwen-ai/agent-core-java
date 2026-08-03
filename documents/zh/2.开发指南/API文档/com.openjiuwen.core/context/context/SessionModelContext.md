# com.openjiuwen.core.context_engine.context.SessionModelContext

## class SessionModelContext

```java
public class SessionModelContext extends ModelContext implements StatefulContext, OffloadCapableContext
```

`SessionModelContext` 是当前上下文子系统的核心实现。它把消息缓冲、处理器链、窗口裁剪、统计计算、状态持久化、卸载缓存和可选的 KV Cache 管理组合到同一个会话级上下文对象中。

## 构造方法

### `public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config, List<BaseMessage> historyMessages, List<ContextProcessor> processors, TokenCounter tokenCounter)`

根据上下文配置、初始历史、处理器链和 token 计数器创建会话级上下文。

**参数**

- `contextId`: 当前上下文 ID。
- `sessionId`: 所属会话 ID。
- `config`: 上下文引擎配置；其中 `maxContextMessageNum`、`defaultWindowMessageNum`、`defaultWindowRoundNum`、`enableReload`、`enableKvCacheRelease` 会被拆分到内部字段。
- `historyMessages`: 初始历史消息。
- `processors`: `ContextProcessor` 列表；为 `null` 时退回空列表。
- `tokenCounter`: token 计数器，可为 `null`。

## 主要方法

### `public List<BaseMessage> addMessages(List<BaseMessage> messages)`

校验消息类型后按顺序执行处理器链上的 `triggerAddMessages()` / `onAddMessages()`，再把最终消息写入 `ContextMessageBuffer`。

**说明**

- 处理器异常不会中断主流程，只会记录 warning 日志。

### `public List<BaseMessage> popMessages(int size, boolean withHistory)`

从尾部弹出消息；`size < 0` 时抛出 `CONTEXT_EXECUTION_ERROR`。

### `public List<BaseMessage> getMessages(Integer size, boolean withHistory)`

从 `ContextMessageBuffer` 读取尾部消息；`size < 0` 时抛出 `CONTEXT_EXECUTION_ERROR`。

### `public void setMessages(List<BaseMessage> messages, boolean withHistory)`

校验消息类型后替换缓冲区内容。

### `public void clearMessages(boolean withHistory)`

清空消息缓冲，并重置 `OffloadMessageBuffer`。

### `public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize, Integer dialogueRound, Map<String, Object> kwargs)`

构造推理窗口，并在需要时追加重载提示、执行窗口处理器链、剔除前导 `ToolMessage`、统计 token，并通知 `KVCacheManager`。

**说明**

- `windowSize <= 0` 或 `dialogueRound <= 0` 时抛出 `CONTEXT_EXECUTION_ERROR`。
- `enableReload == true` 时会自动在系统消息尾部追加重载提示词。
- 当显式 `dialogueRound` 非空时，会先按最近 N 轮对话截断，再应用 `windowSize`。
- 传入 `kwargs.get("model")` 且启用了 `enableKvCacheRelease` 时，会尝试触发 KV Cache 释放。

### `public ContextStats statistic()`

统计当前消息缓冲区中的消息数量、轮次数以及 token 用量。

### `public TokenCounter tokenCounter()`

返回构造时绑定的 token 计数器。

### `public Tool reloaderTool()`

返回 `reload_original_context_messages` 工具，工具卡片 ID 形如 `reload_<sessionId>_<contextId>`。

### `public void offloadMessages(String offloadHandle, List<BaseMessage> messages)`

把消息按 `in_memory` 类型写入卸载缓冲。

### `public Map<String, Object> saveState()`

返回包含 `messages` 与 `offload_messages` 两个键的状态映射。

### `public void loadState(Map<String, Object> state)`

从外部状态映射中恢复当前 `contextId` 对应的消息与卸载缓存；目标上下文缺失时会清空当前缓冲。

## 说明

- 统计逻辑会分别累计 `assistant`、`user`、`system`、`tool` 四类消息，并通过 `ContextUtils.findAllDialogueRound()` 计算 `totalDialogues`。
- `validateAndFixContextWindow()` 会丢弃窗口开头连续出现的 `ToolMessage`；如果窗口只剩工具消息，则直接清空 `contextMessages`。
- `SessionModelContextTest` 与 `ModelContextTest` 覆盖了窗口截断、轮次优先级、重载提示、KV Cache 选项、状态恢复和重载工具行为。
