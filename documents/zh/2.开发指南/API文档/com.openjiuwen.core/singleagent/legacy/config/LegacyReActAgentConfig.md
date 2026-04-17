# com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig

## 类 LegacyReActAgentConfig

```java
public class LegacyReActAgentConfig extends AgentConfig
```

旧版 ReAct agent 的完整运行配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `controllerType` | `ControllerType` | `ControllerType.REACT_CONTROLLER` | 固定为 ReAct 控制器类型。 |
| `promptTemplateName` | `String` | `"react_system_prompt"` | 默认系统提示词模板名称。 |
| `promptTemplate` | `List<Map<String, String>>` | `new ArrayList<>()` | 实际使用的提示词模板内容。 |
| `constrain` | `ConstrainConfig` | `ConstrainConfig.builder().build()` | 上下文轮次与迭代次数约束。 |
| `plugins` | `List<PluginSchema>` | `new ArrayList<>()` | 兼容旧插件注册方式的插件清单。 |
| `memoryScopeId` | `String` | `""` | 记忆作用域标识。 |
| `agentMemoryConfig` | `AgentMemoryConfig` | `AgentMemoryConfig.builder().build()` | 记忆模块配置。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public int getContextWindowLimit()` | 返回 `constrain.reservedMaxChatRounds`；若 `constrain` 为空则回落到 `10`。 |

## 说明

- 该类继承 `AgentConfig` 的 `id`、`version`、`description`、`workflows`、`model` 和 `tools` 字段。
- 源码使用 Lombok `@Data` 与 `@SuperBuilder` 生成访问器和 builder。
