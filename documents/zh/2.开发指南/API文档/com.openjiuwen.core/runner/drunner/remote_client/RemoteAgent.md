# com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent

## 类 RemoteAgent

```java
public class RemoteAgent
```

`RemoteAgent` 是面向远端 Agent 的轻量代理，内部封装了具体 `RemoteClient` 实现。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | `String` | `-` | 远端 Agent 标识。 |
| `version` | `String` | `-` | 远端版本号，未提供时为空字符串。 |
| `description` | `String` | `-` | 远端 Agent 描述。 |
| `topic` | `String` | `-` | 发送请求时使用的目标 topic。 |
| `protocol` | `ProtocolEnum` | `-` | 远程传输协议，默认是 `MQ`。 |
| `client` | `RemoteClient` | `-` | 实际执行远程调用的客户端实例。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public RemoteAgent(String agentId, String version, String description, String topic, ProtocolEnum protocol, Map<String, Object> config)` | 使用显式参数构造远端 Agent 代理，并创建对应的 `MqRemoteClient`。 |
| `public RemoteAgent(String agentId)` | 使用默认版本、协议和 topic 规则创建远端 Agent 代理。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | 启动底层客户端并发起一次性远程调用。 |
| `public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | 启动底层客户端并发起流式远程调用。 |
