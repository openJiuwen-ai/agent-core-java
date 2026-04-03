# compressor

`com.openjiuwen.core.context.processor.compressor` 提供多种上下文压缩器，用于在消息条数或 token 超过阈值时压缩当前轮、完整轮次或多个连续轮次。

## Types

| 类型 | 说明 |
|---|---|
| [`CurrentRoundCompressor`](./compressor/CurrentRoundCompressor.md) | 压缩当前轮中最后一个用户消息之后的内容。 |
| [`CurrentRoundCompressorConfig`](./compressor/CurrentRoundCompressorConfig.md) | `CurrentRoundCompressor` 的阈值、保留消息数和模型配置。 |
| [`DialogueCompressor`](./compressor/DialogueCompressor.md) | 压缩“用户消息 -> 工具执行 -> 最终助手回答”完成轮次。 |
| [`DialogueCompressorConfig`](./compressor/DialogueCompressorConfig.md) | `DialogueCompressor` 的消息/token 阈值和提示配置。 |
| [`RoundLevelCompressor`](./compressor/RoundLevelCompressor.md) | 把多个连续对话轮合并压缩成一个新轮次。 |
| [`RoundLevelCompressorConfig`](./compressor/RoundLevelCompressorConfig.md) | `RoundLevelCompressor` 的轮次阈值、token 阈值和提示配置。 |

## Notes

- 三个压缩器都继承 `ContextProcessor`，默认不保存内部状态。
- 当前测试重点覆盖触发条件和结构行为；真正的摘要内容仍依赖外部模型调用。
