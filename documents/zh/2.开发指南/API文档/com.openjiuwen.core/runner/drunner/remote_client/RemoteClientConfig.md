# com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientConfig

## 类 RemoteClientConfig

```java
public class RemoteClientConfig
```

`RemoteClientConfig` 用于封装 `com.openjiuwen.core.runner.drunner.remoteclient` 相关配置项。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `String` | `-` | 远端 Agent 或客户端标识。 |
| `version` | `String` | `-` | 远端版本号。 |
| `name` | `String` | `-` | 配置项名称。 |
| `description` | `String` | `-` | 远端目标描述。 |
| `protocol` | `ProtocolEnum` | `ProtocolEnum.MQ` | 远程调用使用的传输协议。 |
| `type` | `String` | `-` | 客户端实现类型或分类标签。 |
| `topic` | `String` | `-` | 发送请求时使用的目标 topic。 |
| `url` | `String` | `-` | 预留的远端地址配置。 |
| `kwargs` | `Map<String, Object>` | `new LinkedHashMap<>()` | 附加配置参数集合。 |
