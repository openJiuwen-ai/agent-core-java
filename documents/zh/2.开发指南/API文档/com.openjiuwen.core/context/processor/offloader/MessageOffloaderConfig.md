# com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig

## class MessageOffloaderConfig

```java
public class MessageOffloaderConfig
```

`MessageOffloaderConfig` 定义消息卸载器的触发阈值、可卸载角色、裁剪长度和最近消息保留策略。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `messagesThreshold` | `Integer` | `null` | 总消息数超过该值时触发卸载。 |
| `tokensThreshold` | `int` | `20000` | 累计 token 超过该值时触发卸载。 |
| `largeMessageThreshold` | `int` | `1000` | 单条消息内容长度超过该值时视为“大消息”。 |
| `offloadMessageType` | `List<String>` | `["tool"]` | 允许被卸载的消息角色白名单。 |
| `trimSize` | `int` | `100` | 被卸载消息保留下来的前缀字符数。 |
| `messagesToKeep` | `Integer` | `null` | 最近 N 条消息永不参与卸载。 |
| `keepLastRound` | `boolean` | `true` | 是否保留最近一轮完整对话不参与卸载。 |

## 显式方法

### `public void validate()`

校验 `messagesThreshold`、`tokensThreshold`、`largeMessageThreshold`、`trimSize`、`messagesToKeep` 是否大于 `0`。

## 说明

- 运行时还需要满足 `trimSize < largeMessageThreshold` 且 `messagesToKeep < messagesThreshold`，这些约束由 `MessageOffloader.validateConfig()` 负责。
