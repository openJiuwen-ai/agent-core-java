# com.openjiuwen.core.application.llm.LlmAgent

## class LlmAgent

```java
public class LlmAgent extends ControllerAgent
```

`LlmAgent` 是应用层 ReAct Agent 实现。它基于 `ControllerAgent` 组合 `Controller`、`LlmEventHandler`、上下文窗口配置和可选的长期记忆写回逻辑。

## 构造方法

### `public LlmAgent(LlmAgentConfig agentConfig)`

基于 `agentConfig` 创建 Agent。

**参数**

- `agentConfig`: LLM Agent 配置对象。

**说明**

- 当 `controllerType` 非空且不等于 `ControllerType.REACT_CONTROLLER` 时抛出 `UnsupportedOperationException`。
- 若 `contextEngineConfig` 为空，则按 `constrain.reservedMaxChatRounds` 推导 `ContextEngineConfig`，窗口大小为 `maxRounds * 2`。
- 构造时会为内部 `Controller` 绑定 `LlmEventHandler`。
- 仅当 `memoryScopeId` 非空，且 `agentMemoryConfig` 启用了长期记忆或声明了 `memVariables` 时，才会启用记忆写回。

## 主要方法

### `public ControllerOutput invoke(Object inputs, Session session)`

执行一次非流式 Agent 调用。

**参数**

- `inputs`: 一般为包含 `query`、`conversation_id`、`user_id` 的输入对象或 `Map`。
- `session`: 外部会话；传 `null` 时内部会按 `conversation_id` 或 `default_session` 自动创建 `AgentSessionApi`。

**返回**

- `ControllerOutput`: Controller 的最终执行结果。

**说明**

- 自动执行 `preRun()` / `postRun()` 生命周期。
- 当启用记忆且输入中包含 `user_id` 时，会异步将用户消息与回答写入 `LongTermMemory`。

### `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)`

执行流式 Agent 调用。

**参数**

- `inputs`: 与 `invoke()` 相同。
- `session`: 外部会话或 `null`。
- `streamModes`: 输出流模式列表。

**返回**

- `Iterator<Object>`: 底层 Controller 产生的流式输出。

**说明**

- 结束时会汇总 `answer` 类型的输出片段，并在满足条件时异步写入长期记忆。
- 自动管理会话生命周期，并在迭代器自然结束或抛出 `NoSuchElementException` 时执行清理。

### `public void setPromptTemplate(List<Map<String, String>> promptTemplate)`

替换 `agentConfig` 上的提示模板，并同步更新当前 `LlmEventHandler`。

### `public void addPrompt(List<Map<String, String>> promptTemplate)`

将新的提示片段追加到现有模板末尾；空列表会被直接忽略。

### `public LlmAgentConfig getAgentConfig()`

返回构造时绑定的应用层配置对象。

## 静态工厂

### `public static LlmAgentConfig createLlmAgentConfig(...)`

创建应用层 `LlmAgentConfig` 配置对象。

**说明**

- `workflows`、`plugins`、`promptTemplate`、`tools` 传入 `null` 时会归一化为空列表。

### `public static LlmAgent createLlmAgent(LlmAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools)`

创建 Agent，并批量注册工作流与工具。

**说明**

- 会把 `WorkflowCard` / `ToolCard` 同步加入 `AbilityManager` 与 `Runner.resourceMgr()`。
- 还会把缺失的 `WorkflowSchema` / `PluginSchema` 补回到 `agentConfig`，避免配置与运行时资源脱节。
- 通过该工厂注册的工具可被 Agent 直接发现和执行，且工具列表按 Agent tag 隔离。
