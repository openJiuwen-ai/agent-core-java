# com.openjiuwen.core.runner.drunner.remoteclient.RemoteClient

## 接口 RemoteClient

```java
public interface RemoteClient
```

`RemoteClient` 定义远程调用客户端的生命周期与同步/流式调用入口。

## 方法

| 签名 | 说明 |
| --- | --- |
| `void start()` | 初始化底层远程调用资源。 |
| `void stop()` | 停止客户端的本地运行状态。 |
| `Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | 发起一次性远程调用，并在超时或远端异常时抛出异常。 |
| `Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | 发起流式远程调用，并按迭代器逐块返回结果。 |
