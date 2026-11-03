# com.openjiuwen.core.application.workflow_agent.WorkflowAgent

## class WorkflowAgent

```java
public class WorkflowAgent extends ControllerAgent
```

`WorkflowAgent` 是应用层的工作流执行 Agent。它基于 `ControllerAgent` 组合 `WorkflowEventHandler`，并提供提示模板、工具和工作流注册等操作入口。

## 构造方法

### `public WorkflowAgent(WorkflowAgentConfig agentConfig)`

基于 `agentConfig` 创建工作流 Agent。

**说明**

- 当 `controllerType` 非空且不等于 `ControllerType.WORKFLOW_CONTROLLER` 时抛出 `UnsupportedOperationException`。
- 若 `contextEngineConfig` 为空，则按 `constrain.reservedMaxChatRounds` 推导上下文窗口配置，默认轮次为 `10`。
- 构造时会为内部 `Controller` 绑定 `WorkflowEventHandler`。

## 主要方法

### `public ControllerOutput invoke(Object inputs, Session session)`

执行一次非流式工作流 Agent 调用。

**说明**

- 当 `session == null` 时会自动创建 `AgentSessionApi`，并从 `conversation_id` 推导会话 ID。
- 会在会话状态中临时写入 `__workflow_agent_call_mode = "invoke"`，供 `WorkflowEventHandler` 决定中断输出的返回形态。
- 返回前会把底层 Controller 输出规整为交互块或最终 `WorkflowOutput`。

### `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)`

执行流式工作流 Agent 调用。

**说明**

- 与 `invoke()` 一样会自动管理会话中的 `__workflow_agent_call_mode`，但模式值为 `"stream"`。
- 流结束时会清理调用模式状态并执行 `postRun()`。

### `public WorkflowAgentConfig getAgentConfig()`

返回当前绑定的工作流 Agent 配置。

### `public void setPromptTemplate(List<Map<String, String>> promptTemplate)`

替换配置对象上的提示模板；传入 `null` 时会归一化为空列表。

### `public void addPrompt(List<Map<String, String>> promptTemplate)`

把新模板片段追加到当前模板尾部。

### `public void addTools(List<Tool> tools)`

把工具加入 `AbilityManager`、`Runner.resourceMgr()` 与 `agentConfig.tools`。

### `public void addWorkflows(List<Workflow> workflows)`

把工作流加入 `AbilityManager` 与 `agentConfig.workflows`；当 Agent 拥有非空 `id` 时，还会同步注册到 `Runner.resourceMgr()`。
