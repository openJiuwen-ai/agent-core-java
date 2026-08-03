# com.openjiuwen.core.single_agent.BaseAgent

## 抽象类 BaseAgent

```java
public abstract class BaseAgent
```

旧版单智能体抽象基类，统一维护配置、上下文引擎、工具与工作流注册。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `agentConfig` | `final AgentConfig` | `-` | 当前 agent 的配置对象。 |
| `tools` | `final List<Tool>` | `new ArrayList<>()` | 已注册到资源管理器的工具实例集合。 |
| `workflows` | `final List<Workflow>` | `new ArrayList<>()` | 已注册的工作流实例集合。 |
| `contextEngine` | `final ContextEngine` | `createContextEngine()` | 根据约束配置生成的上下文窗口管理器。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public AgentConfig getAgentConfig()` | 返回当前旧版配置对象。 |
| `public Config config()` | 返回兼容旧调用链的 `Config` 包装器。 |
| `public ContextEngine getContextEngine()` | 返回构造时创建的上下文引擎。 |
| `public void addPrompt(List<Map<String, String>> promptTemplate)` | 追加提示词模板；如果配置类没有 `getPromptTemplate()`，仅记录告警。 |
| `public void addTools(List<Tool> newTools)` | 注册工具到本地集合和 `Runner.resourceMgr()`，并把工具名补充到 `agentConfig.tools`。 |
| `public void addWorkflows(List<Workflow> newWorkflows)` | 注册工作流实例到本地集合和资源管理器。 |
| `@SuppressWarnings("unchecked") public void addWorkflowItems(List<?> items)` | 接受 `Workflow`、`WorkflowFactory` 或 `Supplier<Workflow>` 的混合列表，并逐项注册。 |
| `public void removeWorkflows(List<String[]> workflowKeys)` | 按 `[workflowId, workflowVersion]` 从配置和资源管理器中移除工作流。 |
| `public void bindWorkflows(List<Workflow> newWorkflows)` | `addWorkflows` 的兼容别名。 |
| `public void bindWorkflowItems(List<?> items)` | `addWorkflowItems` 的兼容别名。 |
| `public void addPlugins(List<PluginSchema> plugins)` | 通过反射读取 `getPlugins()`，按插件名去重后追加到配置。 |
| `public void clearSession(String sessionId)` | 调用 `Runner.release(sessionId)` 释放会话资源。 |
| `public abstract Object invoke(Map<String, Object> inputs, Session session)` | 执行一次非流式调用，由子类提供具体实现。 |
| `public abstract Iterator<Object> stream(Map<String, Object> inputs, Session session)` | 执行一次流式调用，由子类提供具体实现。 |

## 嵌套类型

- `Config`：轻量包装器，仅暴露 `getAgentConfig()`，用于兼容旧的 `agent.config().get_agent_config()` 调用方式。

## 说明

- 子类通过唯一的受保护构造方法传入 `AgentConfig`，基类会据此创建默认 `ContextEngine`。
