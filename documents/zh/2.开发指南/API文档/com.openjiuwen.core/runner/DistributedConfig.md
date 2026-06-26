# com.openjiuwen.core.runner.DistributedConfig

## class DistributedConfig

```java
public class DistributedConfig
```

`DistributedConfig` 用于封装 `com.openjiuwen.core.runner` 相关配置项。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `requestTimeout` | `double` | `30.0` | - |
| `maxRequestConcurrency` | `int` | `10000` | - |
| `messageQueueConfig` | `MessageQueueConfig` | `new MessageQueueConfig()` | - |
| `agentTopicTemplate` | `String` | `"openjiuwen.single_agent.{agent_id}.{version}"` | - |
| `replyTopicTemplate` | `String` | `"openjiuwen.reply.runner.{instance_id}"` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public String getAgentTopicTemplate(String envPrefix)` | Get agent topic template with environment prefix. |
| `public String getReplyTopicTemplate(String envPrefix)` | Get reply topic template with environment prefix. |

## 相关测试

- `RunnerTest`
