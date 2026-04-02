# com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig

## class CurrentRoundCompressorConfig

```java
public class CurrentRoundCompressorConfig
```

`CurrentRoundCompressorConfig` 定义当前轮压缩器的触发阈值、保留消息策略和模型调用参数。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `messagesThreshold` | `Integer` | `null` | 总消息数超过该值时触发压缩。 |
| `tokensThreshold` | `int` | `10000` | 累计 token 超过该值时触发压缩。 |
| `messagesToKeep` | `Integer` | `null` | 保留最近 N 条消息不参与压缩。 |
| `largeMessageThreshold` | `int` | `1000` | 在单条消息模式下，大于该值的消息视为“大消息”。 |
| `customizedCompressionPrompt` | `String` | `null` | 自定义压缩提示词；为空时使用内置提示。 |
| `singleMultiCompression` | `boolean` | `false` | `false` 表示逐条压缩大消息，`true` 表示整体压缩连续消息段。 |
| `model` | `ModelRequestConfig` | `null` | 模型请求配置。 |
| `modelClient` | `ModelClientConfig` | `null` | 模型客户端配置。 |

## 显式方法

### `public void validate()`

校验 `messagesThreshold`、`tokensThreshold`、`messagesToKeep`、`largeMessageThreshold` 是否大于 `0`。

## 说明

- 该类使用 Lombok builder；`CurrentRoundCompressor` 构造时直接读取这些字段初始化内部阈值。
- `CurrentRoundCompressorTest` 验证了 builder 赋值和 `processorType()` 返回值。
