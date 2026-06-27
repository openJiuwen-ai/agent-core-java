# com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressorConfig

## class DialogueCompressorConfig

```java
public class DialogueCompressorConfig
```

`DialogueCompressorConfig` 定义完整对话轮压缩器的触发阈值、是否保留最近一轮，以及摘要模型的请求参数。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `messagesThreshold` | `Integer` | `null` | 总消息数超过该值时触发压缩。 |
| `tokensThreshold` | `int` | `10000` | 累计 token 超过该值时触发压缩。 |
| `messagesToKeep` | `Integer` | `null` | 保留最近 N 条消息不参与压缩。 |
| `keepLastRound` | `boolean` | `true` | 是否保留最近一轮完整对话不参与压缩。 |
| `customizedCompressionPrompt` | `String` | `null` | 自定义压缩提示词。 |
| `compressionTokenLimit` | `int` | `2000` | 期望压缩摘要的 token 上限。 |
| `model` | `ModelRequestConfig` | `null` | 模型请求配置。 |
| `modelClient` | `ModelClientConfig` | `null` | 模型客户端配置。 |

## 显式方法

### `public void validate()`

校验 `messagesThreshold`、`tokensThreshold`、`messagesToKeep` 和 `compressionTokenLimit` 是否大于 `0`。

## 说明

- `DialogueCompressorTest` 覆盖了 builder 配置值、处理器名称和触发条件。
