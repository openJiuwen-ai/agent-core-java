# drunner

`com.openjiuwen.core.runner.drunner` 封装分布式 Runner 的运行时入口以及消息队列启动流程。

## 子包

| 包 | 说明 |
| --- | --- |
| [`dmessage_queue`](drunner/dmessage_queue.README.md) | 提供分布式消息队列工厂、消息序列化器和测试替身实现。 |
| [`remote_client`](drunner/remote_client.README.md) | 定义远程客户端协议、客户端配置以及远端 Agent 代理。 |
| [`server_adapter`](drunner/server_adapter.README.md) | 将本地 Agent 适配到消息队列服务端，并构造标准响应消息。 |

## 类型

| 类型 | 说明 |
| --- | --- |
| [`DistributedRunner`](drunner/DistributedRunner.md) | 维护分布式运行所需的消息队列实例、`reply topic` 订阅器以及 topic 生成逻辑。 |
