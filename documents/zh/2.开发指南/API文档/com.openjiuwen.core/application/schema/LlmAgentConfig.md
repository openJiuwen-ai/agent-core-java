# com.openjiuwen.core.application.schema.LlmAgentConfig

## class LlmAgentConfig

```java
public class LlmAgentConfig
```

`LlmAgentConfig` 聚合了 ReAct Agent 运行所需的身份信息、模型配置、提示模板、工作流或插件引用、记忆配置与约束参数。

## 字段

| 字段 | 类型 | 默认值 | JSON 别名 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `null` | - | Agent 标识。 |
| `version` | `String` | `"1.0"` | - | Agent 版本。 |
| `description` | `String` | `""` | - | Agent 描述。 |
| `controllerType` | `ControllerType` | `REACT_CONTROLLER` | `controller_type` / `controllerType` | 控制器类型；`LlmAgent` 要求其保持为 ReAct。 |
| `workflows` | `List<WorkflowSchema>` | `[]` | - | 可用工作流列表。 |
| `plugins` | `List<PluginSchema>` | `[]` | - | 可用插件列表。 |
| `model` | `ModelConfig` | `null` | - | 模型配置。 |
| `promptTemplateName` | `String` | `"react_system_prompt"` | `prompt_template_name` / `promptTemplateName` | 提示模板名称。 |
| `promptTemplate` | `List<Map<String, String>>` | `[]` | `prompt_template` / `promptTemplate` | 提示模板内容。 |
| `tools` | `List<String>` | `[]` | - | 工具名称列表。 |
| `memoryScopeId` | `String` | `""` | `memory_scope_id` / `memoryScopeId` | 长期记忆作用域。 |
| `agentMemoryConfig` | `AgentMemoryConfig` | `AgentMemoryConfig.builder().build()` | `agent_memory_config` / `agentMemoryConfig` | 记忆配置。 |
| `constrain` | `ConstrainConfig` | `ConstrainConfig.builder().build()` | - | 轮次与迭代约束。 |
| `contextEngineConfig` | `ContextEngineConfig` | `null` | `context_engine_config` / `contextEngineConfig` | 自定义上下文引擎配置。 |
| `defaultResponse` | `DefaultResponse` | `null` | `default_response` / `defaultResponse` | 可选的默认响应配置。 |

## 显式方法

### `public int getContextWindowLimit()`

返回上下文窗口轮次上限。

**返回**

- 当 `constrain` 非空时，返回 `constrain.getReservedMaxChatRounds()`。
- 否则返回硬编码默认值 `10`。

## 说明

- 该类使用 `@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor` 生成常规访问器与构造器。
- `controller_type`、`prompt_template_name`、`memory_scope_id` 与 `constrain.*` 等字段同时支持下划线写法和 Java 风格字段名。
