# com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter

## 类 AgentAdapter

```java
public class AgentAdapter
```

`AgentAdapter` 将本地 Agent 暴露为基于 MQ 的分布式服务端入口。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | `String` | `-` | 本地 Agent 标识。 |
| `version` | `String` | `-` | Agent 版本号。 |
| `topic` | `String` | `-` | 对外暴露的消息 topic。 |
| `server` | `MqServerAdapter` | `-` | 实际执行请求分发的 MQ 服务端适配器。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgentAdapter(String agentId, String version)` | 根据 Agent 标识和版本构造适配器，并绑定同步/流式处理函数。 |
| `public AgentAdapter(String agentId)` | 使用空版本号创建适配器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void start()` | 启动底层 `MqServerAdapter`，开始接收远程请求。 |
| `public void stop()` | 停止底层 `MqServerAdapter`。 |
