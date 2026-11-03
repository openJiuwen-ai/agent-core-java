# com.openjiuwen.core.single_agent.legacy.config.WorkflowAgentConfig

## class WorkflowAgentConfig

```java
public class WorkflowAgentConfig
```

`WorkflowAgentConfig` 聚合了工作流 Agent 的模型、工作流集合、默认响应与全局变量配置。

## 字段

| 字段 | 类型 | 默认值 | JSON 别名 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `null` | - | Agent 标识。 |
| `version` | `String` | `"1.0"` | - | Agent 版本。 |
| `description` | `String` | `""` | - | Agent 描述。 |
| `model` | `ModelConfig` | `null` | - | 工作流意图识别和默认响应使用的模型。 |
| `controllerType` | `ControllerType` | `WORKFLOW_CONTROLLER` | `controller_type` / `controllerType` | 控制器类型。 |
| `workflows` | `List<WorkflowSchema>` | `[]` | - | 可用工作流列表。 |
| `promptTemplate` | `List<Map<String, String>>` | `[]` | `prompt_template` / `promptTemplate` | 提示模板。 |
| `tools` | `List<String>` | `[]` | - | 关联工具名称列表。 |
| `startWorkflow` | `WorkflowSchema` | `WorkflowSchema.builder().build()` | `start_workflow` / `startWorkflow` | 起始工作流定义。 |
| `endWorkflow` | `WorkflowSchema` | `WorkflowSchema.builder().build()` | `end_workflow` / `endWorkflow` | 结束工作流定义。 |
| `globalVariables` | `List<Map<String, Object>>` | `[]` | `global_variables` / `globalVariables` | 全局变量描述。 |
| `globalParams` | `Map<String, Object>` | `new LinkedHashMap<>()` | `global_params` / `globalParams` | 全局参数。 |
| `constrain` | `ConstrainConfig` | `ConstrainConfig.builder().build()` | - | 轮次与迭代约束。 |
| `defaultResponse` | `DefaultResponse` | `DefaultResponse.builder().build()` | `default_response` / `defaultResponse` | 未匹配任何工作流时的默认响应。 |
| `contextEngineConfig` | `ContextEngineConfig` | `null` | `context_engine_config` / `contextEngineConfig` | 自定义上下文引擎配置。 |

## 说明

- 该类使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor` 生成常规访问器与构造器。
- `ApplicationTranslationRegressionTest` 验证了 `start_workflow`、`end_workflow`、`global_variables`、`global_params` 与 `default_response` 的 JSON 映射。
