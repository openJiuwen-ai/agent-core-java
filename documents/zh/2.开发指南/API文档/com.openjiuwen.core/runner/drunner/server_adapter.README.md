# server_adapter

`com.openjiuwen.core.runner.drunner.server_adapter` 将本地 Agent 适配到消息队列服务端，并构造标准响应消息。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AgentAdapter`](server_adapter/AgentAdapter.md) | 将本地 Agent 暴露为分布式 MQ 服务端适配器。 |
| [`MessageTask`](server_adapter/MessageTask.md) | 绑定请求消息与其正在执行的任务。 |
| [`MqMessageUtils`](server_adapter/MqMessageUtils.md) | 构造分布式 MQ 响应消息的辅助方法集合。 |
| [`MqServerAdapter`](server_adapter/MqServerAdapter.md) | 基于 MQ 接收请求并调用处理器的服务端适配器。 |
