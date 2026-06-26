# com.openjiuwen.core.single_agent.legacy.config.AgentConfig

## 类 AgentConfig

```java
public class AgentConfig
```

旧版 agent 的通用配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `id` | `String` | `""` | agent 标识。 |
| `version` | `String` | `""` | agent 版本号。 |
| `description` | `String` | `""` | agent 描述。 |
| `controllerType` | `ControllerType` | `ControllerType.UNDEFINED` | 控制器类型；未指定时保持未定义。 |
| `workflows` | `List<Object>` | `new ArrayList<>()` | 工作流列表，允许同时容纳 `WorkflowSchema` 与 `WorkflowCard`。 |
| `model` | `ModelConfig` | `-` | 默认模型配置。 |
| `tools` | `List<String>` | `new ArrayList<>()` | 绑定到 agent 的工具名称列表。 |

## 说明

- 源码使用 Lombok `@Data` 与 `@SuperBuilder` 生成常规访问器、`equals/hashCode` 和 builder。
