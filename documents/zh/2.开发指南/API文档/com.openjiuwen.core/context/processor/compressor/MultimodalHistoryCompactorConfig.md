# com.openjiuwen.core.context_engine.processor.compressor.MultimodalHistoryCompactorConfig

## class MultimodalHistoryCompactorConfig

```java
public class MultimodalHistoryCompactorConfig
```

`MultimodalHistoryCompactorConfig` 配置历史多模态媒体淘汰策略。

## 属性

### `public int getKeepRecentTurns()`

获取保留为全分辨率的最近媒体轮数。

### `public void setKeepRecentTurns(int keepRecentTurns)`

设置保留轮数，必须大于等于 0。

### `public int getTriggerThreshold()`

获取触发阈值。

### `public void setTriggerThreshold(int triggerThreshold)`

设置触发阈值，必须大于 0。

### `public String getSummaryMarker()`

获取替换旧图片或视频块的文本。

### `public void setSummaryMarker(String summaryMarker)`

设置替换文本，不能为 `null`。
