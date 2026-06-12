# openjiuwen.agent_teams.agent.stream_controller

## StreamController

`StreamController` 管理 TeamAgent 单轮执行、stream chunk 处理、输入续跑和取消清理，对应 Python 源文件 `openjiuwen/agent_teams/agent/stream_controller.py`。

Java 侧覆盖的核心行为：

- `startRound` 启动一轮执行并保存 `agentTask`。
- `streamOneRound` 调用 `MemberRuntime.runStreaming`，检测 `task_failed` chunk，并把普通 `OutputSchema` 标记成带 member 和 role 的 `TeamOutputChunk`。
- chunk observer 在 chunk 入队后触发；observer 抛错会自动移除。
- `_run_retrying_stream` 的重试语义映射为 `runRetryingStream`，对错误码 `181001` 最多重试 10 次。
- `executeRound` 推进 execution status：starting、running、completing/completed、failed、idle。
- `cancelAgent` 和 `drainAgentTask` 清理待处理输入并执行 cooperative cancel。
- `teamCleaned` 被置位时，round finally 会关闭 stream。

Java 运行时采用 `CompletionStage` 和 `Iterator` 表达 Python async stream 语义，`TeamOutputChunk` 是本翻译单元中对 Python `TeamOutputSchema` 标记行为的窄实现。
