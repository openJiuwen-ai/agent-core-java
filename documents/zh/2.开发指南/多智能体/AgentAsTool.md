# AgentAsTool

Java 版这一页讨论的不是一个单独的 `AgentAsTool` 框架类，而是 `AbilityManager` 如何把 `ToolCard`、`WorkflowCard`、`AgentCard` 和 `McpServerConfig` 暴露成 LLM 可见能力，并在运行时分发到对应执行入口。对 Java 来说，`AgentCard` 这条路径是“原语层可用”，但不像 `Tool` / `Workflow` 那样已经包成完整工厂。

## 功能定位

如果你想让一个宿主 Agent 像调用工具一样调用其他能力，当前最核心的入口就是 `AbilityManager`。它负责两件事：

1. 保存能力卡片，并把它们转换成发给模型的 `ToolInfo`。
2. 在模型发起 tool call 后，按能力类型把调用分发到 `Tool.invoke(...)`、`Runner.runWorkflow(...)` 或 `Runner.runAgent(...)`。

因此 Java 里的 “Agent as Tool” 更准确的说法是：`AgentCard` 也能进入能力列表，但你需要自己把真实 Agent 实例注册进 `Runner.resourceMgr()`。

## 能力对象与执行链路

| 能力对象 | 如何进入 `AbilityManager` | 模型看到什么 | 实际执行入口 | 当前状态 |
| --- | --- | --- | --- | --- |
| `ToolCard` | `add(tool.getCard())`，或 `WorkflowAgent.addTools(...)` / `LlmAgent.createLlmAgent(...)` | `toolCard.toolInfo()` | `Tool.invoke(...)` | 推荐主线 |
| `WorkflowCard` | `add(workflow.getCard())`，或 `WorkflowAgent.addWorkflows(...)` / `LlmAgent.createLlmAgent(...)` | `workflowCard.toolInfo()` | `Runner.runWorkflow(...)` | 推荐主线 |
| `AgentCard` | 手动 `add(agentCard)` | `agentCard.toolInfo()` | `Runner.runAgent(...)` | 可用，但需手动注册实例 |
| `McpServerConfig` | 手动 `add(mcpConfig)` | 由 MCP 工具卡片展开成 `ToolInfo` | 依赖资源管理器里的具体 MCP 工具 | 仅能力元数据与工具展开已落地 |

## `AbilityManager` 到底做了什么

### 1. `add(Object ability)` 只负责登记能力元数据

`AbilityManager.add(Object ability)` 会按对象类型把能力放入四类表：

- `ToolCard`
- `WorkflowCard`
- `AgentCard`
- `McpServerConfig`

这一步不会自动替你创建真实实例，只是告诉宿主 Agent：“这些名字现在可以进入能力集合”。

### 2. `listToolInfo()` 决定模型能看到哪些能力

当宿主 Agent 需要把可调用能力发给模型时，`AbilityManager.listToolInfo()` 会：

- 把 `ToolCard` 转成 `ToolInfo`
- 把 `WorkflowCard` 转成 `ToolInfo`
- 把 `AgentCard` 转成 `ToolInfo`
- 把 MCP server 展开成具体 MCP tool 的 `ToolInfo`

这也是为什么 `AgentCard` 虽然不是 `ToolCard`，仍然可以被模型当成“工具入口”看见。

### 3. `executeSingleToolCall(...)` 决定最终调用谁

`AbilityManager.executeSingleToolCall(...)` 的分发顺序很直接：

1. 如果名字命中 `ToolCard`，取出工具实例并执行 `tool.invoke(...)`
2. 如果名字命中 `WorkflowCard`，执行 `Runner.runWorkflow(...)`
3. 如果名字命中 `AgentCard`，执行 `Runner.runAgent(...)`
4. 如果能力表里都没命中，再回退到 `Runner.resourceMgr()` 里按工具名查找

这条执行链说明了一个关键边界：把 `AgentCard` 放进能力列表还不够，真正执行时还必须能在 `Runner.resourceMgr()` 里解析到对应 Agent。

## 最稳的注册路径

### Tool / Workflow：优先用现成封装

Java 当前已经给 `Tool` 和 `Workflow` 提供了比较完整的注册路径：

- `LlmAgent.createLlmAgent(...)` 会把 `WorkflowCard` / `ToolCard` 同步写入 `AbilityManager`、`agentConfig` 和 `Runner.resourceMgr()`
- `WorkflowAgent.addTools(...)` / `addWorkflows(...)` 会同步更新 `AbilityManager`、配置对象和资源管理器

如果你的“子能力”本质上是工具或工作流，优先走这条路径，封装最完整，也最接近当前 Java 的推荐写法。

### Agent：手动把卡片和实例都补齐

如果你的“子能力”本质上是另一个 Agent，当前写法更接近下面这样：

```java
AgentCard reviewerCard = AgentCard.builder()
        .id("reviewer_agent")
        .name("reviewer_agent")
        .description("负责复核回答并给出修改建议")
        .build();

BaseAgent reviewerAgent = new ReviewerAgent(reviewerCard);

Runner.resourceMgr().addAgent(reviewerCard, () -> reviewerAgent, null);
hostAgent.getAbilityManager().add(reviewerCard);
```

这里有三个容易踩的点：

1. `AgentCard.id` 不能为空，因为 `Runner.resourceMgr().addAgent(...)` 会校验资源 ID。
2. `executeSingleToolCall(...)` 会优先使用 `agentCard.getId()`，因此资源注册时的 ID 和卡片 ID 需要对齐。
3. 当前仓库没有一个与 `LlmAgent.createLlmAgent(...)` 对应的“批量注册子 Agent”为能力的现成工厂，所以文档里不应把这条路径写成完全开箱。

## 宿主 Agent 侧应该怎么理解

从宿主 Agent 视角看，`AgentAsTool` 其实是两层结构：

1. `AbilityManager` 负责把能力描述暴露给模型
2. `Runner.resourceMgr()` 负责在真正执行时找到对应实例

这说明 Java 当前更偏向一组可组合原语，尤其是 `AgentCard` 这条路径仍需要你自己装配。

## 当前能力边界

- `Tool` / `Workflow` 的注册路径已经比较成熟，适合写成推荐主线。
- `AgentCard` 暴露为能力是可行的，但当前更偏底层装配，不应伪装成已经有完整 Team/Tool 框架封装。
- `AbilityManager` 能登记 `McpServerConfig` 并展开 MCP tool 元数据，但文档不应把它写成“直接以 server name 执行 MCP 能力”的完整闭环。
- 如果你只是想组织多智能体协作，而不是把一个 Agent 暴露成宿主 Agent 的可调用能力，优先回到 [AgentTeams](AgentTeams.md) 或 [预置协作模式](预置协作模式.md) 看 group 和协作结构。

## 示例入口

- [示例：Groups Java Examples](../../../../examples/groups/README.md)
- [示例：hierarchical_group](../../../../examples/groups/hierarchical_group/README.md)

## 参考入口

- [API 文档：AbilityManager](../API文档/com.openjiuwen.core/singleagent/AbilityManager.md)
- [API 文档：LlmAgent](../API文档/com.openjiuwen.core/application/llm/LlmAgent.md)
- [API 文档：WorkflowAgent](../API文档/com.openjiuwen.core/application/workflow/WorkflowAgent.md)
- [API 文档：Runner](../API文档/com.openjiuwen.core/runner/Runner.md)
- [API 文档：ResourceMgr](../API文档/com.openjiuwen.core/runner/resourcemanager/ResourceMgr.md)

## 本页说明

- 本页重点落在 `AbilityManager` 的真实执行链路。
- 明确区分 `Tool` / `Workflow` 的成熟封装和 `AgentCard` 的手动装配路径。
- 文中所有结论都能回到 `AbilityManager`、`LlmAgent`、`WorkflowAgent` 与 `Runner.resourceMgr()` 的现有实现上。
