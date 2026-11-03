# com.openjiuwen.core.context.context.KVCacheManager

## class KVCacheManager

```java
public class KVCacheManager
```

`KVCacheManager` 用于对比前后两次发送给推理端的 `ContextWindow`，计算 KV Cache 需要失效的半开区间（消息列表与工具列表的首个差异下标）。当前窗口差异失效统一由 ascend-affinity 的 `agent_hint` 管理协议（`KVCacheModelCallHook.handleContextWindowChange`）消费。

## 主要方法

### `public static Optional<ReleaseDecision> firstChangedDecision(ContextWindow previousWindow, ContextWindow nextWindow)`

对比前一个 LLM 绑定窗口与下一个窗口，返回变更决策；无前序窗口时返回 empty。

**说明**

- 追加式变化视为无变化；追加工具会使旧消息后缀（紧跟 system 提示之后）一并失效。
- 返回的 `ReleaseDecision(shouldRelease, messagesReleasedIndex, toolsReleasedIndex)` 中的下标为半开区间起点，由 `KVCacheModelCallHook` 转换为 `agent_hint` 管理动作。
- 该方法为纯函数，不修改任何状态。

## 说明

- `ReleaseDecision` 为记录类型，携带 `shouldRelease` 与消息/工具两个差异起点。
