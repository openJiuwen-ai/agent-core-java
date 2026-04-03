# com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig

## class MessageSummaryOffloaderConfig

```java
public class MessageSummaryOffloaderConfig
```

`MessageSummaryOffloaderConfig` 定义总结式卸载器的阈值、可卸载角色、最近消息保留策略和模型摘要配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `messagesThreshold` | `Integer` | `null` | 总消息数超过该值时可触发总结式卸载。 |
| `tokensThreshold` | `int` | `20000` | 累计 token 超过该值时可触发总结式卸载。 |
| `largeMessageThreshold` | `int` | `1000` | 单条消息超过该值时视为“大消息”。 |
| `offloadMessageType` | `List<String>` | `["tool"]` | 允许被总结式卸载的消息角色白名单。 |
| `messagesToKeep` | `Integer` | `null` | 最近 N 条消息永不参与卸载。 |
| `keepLastRound` | `boolean` | `true` | 是否保留最近一轮完整对话。 |
| `model` | `ModelRequestConfig` | `null` | 模型请求配置。 |
| `modelClient` | `ModelClientConfig` | `null` | 模型客户端配置。 |
| `customizedSummaryPrompt` | `String` | `null` | 自定义摘要提示词。 |

## 显式方法

### `public void validate()`

校验 `messagesThreshold`、`tokensThreshold`、`largeMessageThreshold`、`messagesToKeep` 是否大于 `0`。

## 说明

- `MessageSummaryOffloader` 运行时还会额外检查 `messagesToKeep < messagesThreshold`。
- `MessageSummaryOffloaderTest` 覆盖了默认值、自定义值和这组额外校验。
