# com.openjiuwen.core.multi_agent.legacy.AgentGroupConfig

## class AgentGroupConfig

```java
@Deprecated
public class AgentGroupConfig
```

`AgentGroupConfig` 是旧版 `LegacyBaseGroup` / `ControllerGroup` 模式使用的运行配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `groupId` | `String` | 构造时必填 | 分组标识，创建后不可修改。 |
| `maxAgents` | `int` | `10` | 分组允许注册的最大 Agent 数。 |
| `maxConcurrentMessages` | `int` | `100` | 旧版控制器并发消息上限。 |
| `messageTimeout` | `double` | `30.0` | 单条消息默认超时时间，单位秒。 |

## 构造方法

### `public AgentGroupConfig(String groupId)`

使用默认阈值创建配置对象。

### `public AgentGroupConfig(String groupId, int maxAgents, int maxConcurrentMessages, double messageTimeout)`

一次性显式指定全部运行参数。

## 访问器

| 方法 | 返回 | 说明 |
|---|---|---|
| `getGroupId()` | `String` | 返回固定的分组 ID。 |
| `getMaxAgents()` / `setMaxAgents(int maxAgents)` | `int` / `void` | 读写最大 Agent 数。 |
| `getMaxConcurrentMessages()` / `setMaxConcurrentMessages(int maxConcurrentMessages)` | `int` / `void` | 读写并发消息上限。 |
| `getMessageTimeout()` / `setMessageTimeout(double messageTimeout)` | `double` / `void` | 读写默认消息超时。 |

## 说明

- 这是 legacy 兼容类型，源码已通过 `@Deprecated` 明确提示迁移到新版 `GroupConfig`。
- `groupId` 由构造方法注入，`LegacyBaseGroup` 会直接将它保存为当前组 ID。
