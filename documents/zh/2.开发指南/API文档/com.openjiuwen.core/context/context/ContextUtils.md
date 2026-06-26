# com.openjiuwen.core.context_engine.context.ContextUtils

## class ContextUtils

```java
public final class ContextUtils
```

`ContextUtils` 提供上下文消息列表分析和格式化的静态工具方法，用于识别对话轮次、替换消息片段和格式化重载结果。

## 静态方法

### `public static Optional<Integer> findLastAiMessageWithoutToolCall(List<BaseMessage> messages)`

从尾部向前查找最近一条不带 `toolCalls` 的 `assistant` 消息索引。

### `public static List<BaseMessage> replaceMessages(List<BaseMessage> messages, List<BaseMessage> targetMessages, int startIndex, int endIndex)`

用 `targetMessages` 替换 `[startIndex, endIndex]` 区间内的消息。

**说明**

- 索引非法时抛出 `IndexOutOfBoundsException`。

### `public static String formatReloadedMessages(String offloadHandle, List<BaseMessage> messages)`

把已重载消息格式化为多行文本，优先按 JSON 序列化单条消息，序列化失败时回退到 `toString()`。

### `public static List<int[]> findAllDialogueRound(List<BaseMessage> messages)`

识别消息列表中的全部对话轮次，并按 `[userIndex, assistantIndex]` 形式返回索引对。

**说明**

- 一轮对话从 `user` 消息开始，到下一条“不带工具调用的 `assistant` 消息”结束。
- 未完成轮次会把 `assistantIndex` 记为 `-1`。

### `public static int findLastNDialogueRound(List<BaseMessage> messages, int n)`

返回最近 `n` 轮对话的起始 `user` 消息索引；找不到任何轮次时返回 `-1`。

## 说明

- `SessionModelContext.getContextWindow()` 会用 `findLastNDialogueRound()` 来实现按对话轮次截断。
- `ContextUtilsTest` 覆盖了无工具调用助手消息定位、区间替换、轮次识别和重载文本格式化。
