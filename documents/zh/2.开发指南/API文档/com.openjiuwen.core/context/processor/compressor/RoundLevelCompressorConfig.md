# com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig

## class RoundLevelCompressorConfig

```java
public class RoundLevelCompressorConfig
```

`RoundLevelCompressorConfig` 定义多轮压缩器需要的轮次数阈值、token 阈值和模型调用配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `roundsThreshold` | `int` | `10` | 至少连续多少轮对话才允许触发合并压缩。 |
| `tokensThreshold` | `int` | `10000` | 累计 token 超过该值时才允许触发。 |
| `keepLastRound` | `boolean` | `true` | 是否保留最近一轮不参与压缩。 |
| `customizedCompressionPrompt` | `String` | `null` | 自定义轮次压缩提示词。 |
| `model` | `ModelRequestConfig` | `null` | 模型请求配置。 |
| `modelClient` | `ModelClientConfig` | `null` | 模型客户端配置。 |

## 显式方法

### `public void validate()`

校验 `roundsThreshold > 1` 且 `tokensThreshold > 0`。

## 说明

- 压缩器构造时会立即调用 `validate()`，因此非法配置会在实例化阶段暴露。
