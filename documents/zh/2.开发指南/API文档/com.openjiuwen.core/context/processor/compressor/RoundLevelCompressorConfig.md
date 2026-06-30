# com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig

## class RoundLevelCompressorConfig

```java
public class RoundLevelCompressorConfig
```

`RoundLevelCompressorConfig` 定义 round-level fallback 压缩器的 context-window token 预算、分阶段压缩目标和截断参数。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `triggerTotalTokens` | `int` | `230000` | context window 估算 token 超过该值时触发压缩。 |
| `targetTotalTokens` | `int` | `160000` | 压缩后的 context window 目标 token 上限。 |
| `keepRecentMessages` | `int` | `0` | `onAddMessages` 压缩时保护的最新原始消息数量。 |
| `compressionCallMaxTokens` | `int` | `250000` | 单次内部压缩模型调用的最大 token 预算。 |
| `firstPassTargetTokens` | `int` | `30000` | 第一阶段 round-level summary 目标 token 数。 |
| `secondPassTargetTokens` | `int` | `20000` | aggressive keep-recent 阶段 summary 目标 token 数。 |
| `thirdPassTargetTokens` | `int` | `10000` | aggressive full-context 阶段 summary 目标 token 数。 |
| `truncateHeadRatio` | `double` | `0.2` | hard truncate 时保留文本中 head 部分的比例。 |
| `truncatedMarker` | `String` | `"...[TRUNCATED]..."` | hard truncate 插入的省略标记。 |
| `compressionMarker` | `String` | `"[ROUND_LEVEL_MEMORY_BLOCK]"` | round-level fallback memory block 标记。 |
| `model` | `ModelRequestConfig` | `null` | 模型请求配置。 |
| `modelClient` | `ModelClientConfig` | `null` | 模型客户端配置。 |

## 显式方法

### `public void validate()`

校验 token 预算、阶段目标 token 均大于 `0`，`keepRecentMessages >= 0`，且 `truncateHeadRatio` 在 `(0, 1)` 区间内。

## 说明

- 压缩器构造时会立即调用 `validate()`，因此非法配置会在实例化阶段暴露。
- Java 字段名按 Python `RoundLevelCompressorConfig` 的语义映射为驼峰命名。
