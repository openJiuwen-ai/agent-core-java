# remote_client

`com.openjiuwen.core.runner.drunner.remote_client` 定义远程客户端协议、客户端配置以及远端 Agent 代理。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`MqRemoteClient`](remote_client/MqRemoteClient.md) | 基于消息队列的 `RemoteClient` 实现。 |
| [`ProtocolEnum`](remote_client/ProtocolEnum.md) | 远程传输协议枚举，当前仅定义 `MQ`。 |
| [`RemoteAgent`](remote_client/RemoteAgent.md) | 面向远端 Agent 的轻量代理，内部封装具体远程客户端。 |
| [`RemoteClient`](remote_client/RemoteClient.md) | 远程客户端抽象接口。 |
| [`RemoteClientConfig`](remote_client/RemoteClientConfig.md) | 远程客户端配置对象，描述目标 Agent、协议与附加参数。 |
