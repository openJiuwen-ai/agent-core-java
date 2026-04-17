# com.openjiuwen.core.runner.RunnerConfig

## class RunnerConfig

```java
public class RunnerConfig
```

`RunnerConfig` 用于封装 `com.openjiuwen.core.runner` 相关配置项。

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `distributedMode` | `boolean` | `true` | - |
| `distributedConfig` | `DistributedConfig` | `new DistributedConfig()` | - |
| `envPrefix` | `String` | `""` | - |
| `instanceId` | `String` | `UUID.randomUUID().toString()` | - |
| `checkpointerConfig` | `Map<String, Object>` | `-` | Checkpointer configuration. Uses a generic map since CheckpointerConfig has (type, conf) fields. Key "type" -> String (e.g. "in_memory", "redis") Key "conf" -> Map of configuration properties |
| `DEFAULT` | `RunnerConfig` | `RunnerConfig.builder() .distributedMode(false) .distributedConfig(Distributed...` | Default runner configuration (non-distributed, fake MQ). |
| `GLOBAL_CONFIG` | `AtomicReference<RunnerConfig>` | `new AtomicReference<>(null)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public String agentTopicTemplate()` | Get agent topic template with environment prefix. |
| `public String replyTopicTemplate()` | Get reply topic template with environment prefix. |
| `public static void setRunnerConfig(RunnerConfig config)` | Set the global runner configuration. |
| `public static RunnerConfig getRunnerConfig()` | Get the global runner configuration. Returns the default config if none has been set. |

## 相关测试

- `RunnerTest`
