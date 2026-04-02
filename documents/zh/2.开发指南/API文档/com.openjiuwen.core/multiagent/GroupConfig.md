# com.openjiuwen.core.multiagent.GroupConfig

## class GroupConfig

```java
public class GroupConfig
```

`GroupConfig` 描述多 Agent 分组运行时的容量、并发和超时参数，是新版 `BaseGroup` 的默认配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `maxAgents` | `int` | `10` | 分组允许注册的最大 Agent 数。 |
| `maxConcurrentMessages` | `int` | `100` | 运行层可使用的并发消息上限。 |
| `messageTimeout` | `double` | `30.0` | 单条消息的默认超时时间，单位秒。 |

## 访问器

| 方法 | 返回 | 说明 |
|---|---|---|
| `getMaxAgents()` / `setMaxAgents(int maxAgents)` | `int` / `void` | 读写 Agent 数量上限。 |
| `getMaxConcurrentMessages()` / `setMaxConcurrentMessages(int maxConcurrentMessages)` | `int` / `void` | 读写并发消息上限。 |
| `getMessageTimeout()` / `setMessageTimeout(double messageTimeout)` | `double` / `void` | 读写默认消息超时时间。 |

## 链式配置

| 方法 | 返回 | 说明 |
|---|---|---|
| `configureMaxAgents(int maxAgents)` | `GroupConfig` | 在原对象上更新 `maxAgents` 并返回自身。 |
| `configureTimeout(double timeout)` | `GroupConfig` | 在原对象上更新 `messageTimeout` 并返回自身。 |
| `configureConcurrency(int maxConcurrent)` | `GroupConfig` | 在原对象上更新 `maxConcurrentMessages` 并返回自身。 |

## 说明

- 无参构造直接启用源码中的默认值。
- 该类型本身不做参数合法性校验，调用方需要自行保证阈值合理。
