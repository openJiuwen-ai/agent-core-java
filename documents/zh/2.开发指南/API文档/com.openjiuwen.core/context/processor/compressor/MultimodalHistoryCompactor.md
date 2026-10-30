# com.openjiuwen.core.context_engine.processor.compressor.MultimodalHistoryCompactor

## class MultimodalHistoryCompactor

```java
public class MultimodalHistoryCompactor extends ContextProcessor
```

`MultimodalHistoryCompactor` 用于淘汰多模态工具结果中的历史 base64 图片和视频，避免 ReActAgent 长任务里每轮请求重复携带早期媒体导致 token 二次方增长。

## 构造方法

### `public MultimodalHistoryCompactor(MultimodalHistoryCompactorConfig config)`

根据配置初始化保留轮数、触发阈值和替换文本。`config` 为 `null` 时使用默认配置。

## 主要方法

### `public boolean triggerAddMessages(SessionModelContext context, List<BaseMessage> messages)`

当未压缩媒体轮数量超出保留轮数的差值达到触发阈值时返回 `true`。

### `public ProcessResult onAddMessages(SessionModelContext context, List<BaseMessage> messages, boolean isForced, Map<String, Object> kwargs)`

把保留窗口之外的用户媒体块替换为文本标记，并保留原消息的其余内容。

**说明**

- 处理 `type=image_url` 且 `url` 以 `data:image/` 开头的图片块，以及 `type=video_url` 且 `url` 以 `data:video/` 开头的视频块。
- 已替换的媒体块不再计入后续触发轮数，避免纯文本追加反复触发空扫描。
- 被处理过的消息会写入内部 metadata 标记，便于观测该消息已经压缩。
- `isForced` 或 kwargs 中的 `force=true` 会忽略触发阈值，但仍然保留最近 `keepRecentTurns` 轮。
- 处理器无内部状态，标记和替换文本直接随消息保存。

### `public void loadState(Map<String, Object> state)`

无状态实现，方法体为空。

### `public Map<String, Object> saveState()`

返回空映射。

## 配置

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `keep_recent_turns` | `2` | 保留最近多少个带媒体用户消息为全分辨率。 |
| `trigger_threshold` | `1` | 未压缩媒体轮超出保留窗口的差值达到该值时触发。 |
| `summary_marker` | `[Old image or video content cleared]` | 替换旧媒体块的文本。 |

## 注册

`ContextEngine` 默认注册处理器类型 `MultimodalHistoryCompactor`，可通过 `ContextEngine.ProcessorSpec` 和 `MultimodalHistoryCompactorConfig` 配置启用。
