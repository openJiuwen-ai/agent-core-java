# com.openjiuwen.core.retrieval.common.TqdmCallback

## 类 TqdmCallback

```java
public class TqdmCallback extends BaseCallback
```

轻量级进度回调，用于批量 embedding 或索引流程中的进度统计。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public TqdmCallback(Collection<?> sequence)` | 使用默认描述 `"Indexing"` 创建回调。 |
| `public TqdmCallback(Collection<?> sequence, String desc)` | 使用指定描述创建回调。 |
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | 处理一个批次。 |
| `public int length()` | 返回总量。 |
| `public String getDesc()` | 返回描述文本。 |

## 说明

- `APIEmbeddingTest` 确认批量 embedding 时会按批次触发该回调。
