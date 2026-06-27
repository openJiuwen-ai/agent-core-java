# com.openjiuwen.core.single_agent.legacy.config.WorkflowAgentConfig

## 类 WorkflowAgentConfig

```java
public class WorkflowAgentConfig extends AgentConfig
```

基于工作流控制器的旧版 agent 配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `controllerType` | `ControllerType` | `ControllerType.WORKFLOW_CONTROLLER` | 固定为工作流控制器类型。 |
| `startWorkflow` | `WorkflowSchema` | `new WorkflowSchema()` | 起始工作流定义。 |
| `endWorkflow` | `WorkflowSchema` | `new WorkflowSchema()` | 结束工作流定义。 |
| `globalVariables` | `List<Map<String, Object>>` | `new ArrayList<>()` | 全局变量列表。 |
| `globalParams` | `Map<String, Object>` | `new LinkedHashMap<>()` | 全局参数映射。 |
| `constrain` | `ConstrainConfig` | `ConstrainConfig.builder().build()` | 工作流执行约束配置。 |
| `defaultResponse` | `DefaultResponse` | `DefaultResponse.builder().build()` | 默认回复配置。 |

## 说明

- 该类继承 `AgentConfig` 的通用元数据、模型、工作流和工具字段。
- 源码使用 Lombok `@Data` 与 `@SuperBuilder` 生成访问器和 builder。
