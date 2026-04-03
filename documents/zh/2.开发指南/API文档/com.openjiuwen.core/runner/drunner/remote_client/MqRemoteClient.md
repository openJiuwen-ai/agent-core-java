# com.openjiuwen.core.runner.drunner.remote_client.MqRemoteClient

## 类 MqRemoteClient

```java
public class MqRemoteClient implements RemoteClient
```

`MqRemoteClient` 是基于消息队列的远程客户端实现。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `config` | `RemoteClientConfig` | `-` | 当前客户端配置。 |
| `mq` | `MessageQueueBase` | `-` | 实际发送请求消息的消息队列实例。 |
| `replySubscription` | `ReplyTopicSubscription` | `-` | 用于接收回复消息的订阅器。 |
| `started` | `boolean` | `-` | 本地客户端是否已完成初始化。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MqRemoteClient(RemoteClientConfig config)` | 使用远程客户端配置创建实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void start()` | 绑定共享的分布式消息队列和回复订阅器。 |
| `public void stop()` | 仅将客户端标记为停止，不会关闭共享 MQ。 |
| `public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | 发送一次性请求并等待单个结果；若调用被取消，会补发 `STOP` 消息。 |
| `public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | 发送流式请求并返回结果迭代器；若调用被取消，会补发 `STOP` 消息。 |
