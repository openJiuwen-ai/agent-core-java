# application 模块 Python / Java API 映射

## 对照范围

- Python: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\application`
- Java: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\application`
- 补充说明:
  - Python application 模块直接复用了 `openjiuwen.core.single_agent.legacy` 与 `openjiuwen.core.memory.config` 中的配置/Schema 类型。
  - Java application 模块把一部分配置类型下沉到 `application.schema`，但控制流实现改成了 `Agent + EventHandler` 组合，而不是 Python 的 `Agent + Controller`。
- 统计口径:
  - Python 统计 application 包下顶层函数、公开类、类方法，以及 application 实际依赖的 legacy/schema/config 类型。
  - Java 统计 application 包下公开类、公开方法，以及为承载同等语义而新增的 `EventHandler`/schema 类型。

## 总体结论

- `LLMAgent` / `WorkflowAgent` 两条主链路在 Java 中都能找到对应实现，调用入口 `invoke`、`stream`、prompt 更新、会话托管、打断恢复、长时记忆写入等核心能力已经落地。
- Java 没有照搬 Python 的公开 `Controller` API，而是把控制逻辑迁移到 `LlmEventHandler` / `WorkflowEventHandler`。从“实现语义”看基本对齐；从“公开 API 形态”看属于结构性改写。
- Java application.schema 与 Python legacy config/schema 只做到部分对齐，字段层面仍有缺口，尤其是 `controller_type`、`start_workflow/end_workflow`、`default_response.type`、`PluginSchema.version/plugin_id/inputs` 等。
- 严格按 Python application 对外 API 计算，Java 仍缺少工厂函数、公开 Controller 类，以及若干配置字段和别名属性；详见 `docs/FIXED/application_fixed.md`。

## 1. 包级与顶层 API

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `create_llm_agent_config(...)` | 无直接同名 API；通常使用 `LlmAgentConfig.builder()` / `new LlmAgentConfig(...)` | 工厂函数 -> builder / 构造器 | 部分映射 | Java 缺少兼容工厂函数 |
| `create_llm_agent(agent_config, workflows=None, tools=None)` | `new LlmAgent(config)` + 调用 `addWorkflow(s)` / `addTool(s)` | 工厂函数 -> 构造 + 显式注册资源 | 部分映射 | Java 没有一站式 helper |
| `convert_timestamp(utc_timestamp)` | 无公开同名 API | 独立工具函数缺失 | 缺失 | Java 内部未暴露对应公共工具函数 |
| Python `application.__init__` 空模块 | Java 无 package facade | 无显式包门面 | 完全映射 | 两侧都没有统一导出层 |

## 2. llm_agent / llm 子包

### 2.1 `LLMAgent` -> `LlmAgent`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LLMAgent(agent_config)` | `LlmAgent(LlmAgentConfig)` | 构造语义映射 | 完全映射 | Java 在构造中同时创建 `Controller` 并挂载 `LlmEventHandler` |
| `invoke(inputs, session=None)` | `invoke(Object inputs, Session session)` | `async -> 同步返回 ControllerOutput` | 适配映射 | 语义一致，Java 显式管理 `preRun/postRun` |
| `stream(inputs, session=None)` | `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `AsyncIterator -> Iterator` | 适配映射 | Java 通过包装迭代器在流结束时补做 memory 写入与 session 清理 |
| `set_prompt_template(prompt_template)` | `setPromptTemplate(List<Map<String, String>>)` | `snake_case -> camelCase` | 完全映射 | 都会把 prompt 变更透传给底层控制器/事件处理器 |
| `agent_config` / `self._config` 访问 | `getAgentConfig()` | 属性 -> getter | 适配映射 | Python 主要沿用基类字段；Java 显式公开 getter |
| `_write_messages_to_memory(inputs, result=None)` | `writeMessagesToMemory(Map<?, ?>, Object)` | 内部逻辑映射 | 完全映射 | Java 还拆出 `writeMessagesToMemoryAsync(...)` |
| `_extract_answer_output(result)` | `extractAnswerOutput(Object)` | 内部工具函数映射 | 完全映射 | - |
| `_convert_response_to_message(result)` | `convertResponseToMessage(Object)` | 内部工具函数映射 | 完全映射 | Java 额外支持 `ControllerOutput` 聚合输出 |
| `_memory_log_task_exception(task)` | 无同名独立函数；在 `writeMessagesToMemoryAsync(...)` 中统一记录异常 | 异步回调日志收敛到 helper 内部 | 适配映射 | Java 没有单独导出异常回调函数 |

### 2.2 `TaskInterruptionState` -> `TaskInterruptionState`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `TaskInterruptionState(task, session, ai_message, remaining_tasks, interaction_data=None, current_iteration=None)` | 两个构造器 `TaskInterruptionState(...)` | dataclass -> 明确构造器重载 | 完全映射 | Java 拆成基础构造器和完整构造器 |
| 字段 `task/session/ai_message/remaining_tasks` | `getTask()/getSession()/getAiMessage()/getRemainingTasks()` | 字段 -> getter | 完全映射 | - |
| 字段 `interaction_data/current_iteration` | `getInteractionData()/setInteractionData()`、`getCurrentIteration()/setCurrentIteration()` | 可选字段 -> getter/setter | 完全映射 | - |

### 2.3 `LLMController` -> `LlmEventHandler`

说明: Python 公开类型是 `LLMController(BaseController)`；Java 对位实现改成 `LlmEventHandler(EventHandler)`。主控制流程保留，但公开类型名和暴露面不同。

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LLMController(config, context_engine, session)` | `LlmEventHandler(LlmAgentConfig, ContextEngine)` | Controller -> EventHandler 构造 | 适配映射 | Java session 改由 `handleInput` 入参提供 |
| `handle_event(event, session)` | `handleInput(EventHandlerInput)` | 顶层处理入口映射 | 完全映射 | Java 将 `event + session` 封装到 `EventHandlerInput` |
| `_handle_user_input(event, session)` | `handleUserInput(Event, AgentSessionApi)` | 主流程 helper 直译 | 完全映射 | - |
| `_execute_react_loop(tasks, session, initial_iteration, ai_message)` | `executeReactLoop(...)` | 同名主循环映射 | 完全映射 | Java 改为同步 while-loop |
| `_execute_task(task, session)` | `executeTask(Task, AgentSessionApi, ModelContext)` | 任务调度映射 | 完全映射 | Java 额外显式传入 `ModelContext` |
| `_execute_workflow_task(task, session)` | `executeWorkflowTask(...)` | 工作流执行映射 | 完全映射 | - |
| `_execute_plugin_task(task, session)` | `executePluginTask(...)` | 插件执行映射 | 完全映射 | - |
| `_handle_task_interrupted(...)` | `handleTaskInterrupted(TaskInterruptionState)` | 打断处理映射 | 完全映射 | - |
| `interrupt_task(task, session, ai_message, remaining_tasks, interaction_data=None, current_iteration=None)` | `interruptTask(TaskInterruptionState)` | 多参数 -> 状态对象 | 适配映射 | Java 利用 `TaskInterruptionState` 收敛参数 |
| `_handle_task_error(...)` | `handleTaskError(...)` | 错误流处理映射 | 完全映射 | - |
| `_post_task_completion(...)` | `postTaskCompletion(...)` | 收尾处理映射 | 完全映射 | - |
| `_generate_plan_from_llm(event, session)` | `generatePlanFromLlm(...)` | 计划生成映射 | 完全映射 | Java 返回 `LlmPlanResult record` |
| `_call_llm_get_output(...)` | `parseLlmOutputToTasks(...)` + `getModel()` + `generatePlanFromLlm(...)` | 逻辑拆分后映射 | 部分映射 | Java 没有同名 helper，调用链被拆散 |
| `_get_model(session=None)` | `getModel()` | model lazy getter 映射 | 完全映射 | Java 无 session 参数 |
| `_get_workflow_id_from_schema(workflow_name)` | `getWorkflowIdFromSchema(String)` | 同名语义映射 | 完全映射 | - |
| `_ensure_workflow_id(task)` | `ensureWorkflowId(Task)` | 同名语义映射 | 完全映射 | - |
| `_resolve_workflow_from_tasks(tasks)` | `resolveWorkflowFromTasks(List<Task>)` | 同名语义映射 | 完全映射 | - |
| `_find_interrupted_task(workflow_task, session)` | `findInterruptedTask(Task, AgentSessionApi)` | 同名语义映射 | 完全映射 | - |
| `_find_interrupted_task_by_node_id(interactive_input, session)` | `findInterruptedTaskByNodeId(InteractiveInput, AgentSessionApi)` | 同名语义映射 | 完全映射 | - |
| `_create_resume_task(event, interrupted_task)` | `buildResumeInteractiveInput(...)` + `setTaskArguments(...)` | 创建恢复输入逻辑拆分 | 部分映射 | Java 没有独立 `ResumeTask` 构造 helper |
| `_find_workflow_by_id(workflow_id, session)` | 通过 `Runner.resourceMgr().getWorkflow(...)` / `resolveTargetId(...)` 组合完成 | 显式资源查询替代 helper | 部分映射 | Java 无同名独立方法 |
| `_prepare_workflow_stream_data(result)` | `prepareWorkflowStreamData(Object)` | 同名语义映射 | 完全映射 | - |
| `_is_workflow_interrupted(result)` | `isWorkflowInterrupted(Object)` | 同名语义映射 | 完全映射 | - |
| `_clear_interrupted_state(task, session)` | `clearInterruptedState(Task, AgentSessionApi, String workflowId)` | 增加 workflowId 参数 | 适配映射 | Java 需要显式工作流标识 |
| `_extract_component_ids_from_interaction_data(data)` | `extractComponentIdsFromInteractionData(List<Object>)` | 同名语义映射 | 完全映射 | - |
| `_write_message_stream_data(stream_data, session)` | `writeMessageStreamData(List<Object>, AgentSessionApi)` | 同名语义映射 | 完全映射 | - |
| `_send_final_stream(content, session)` | `sendFinalStream(String, AgentSessionApi)` | 同名语义映射 | 完全映射 | - |
| `_send_error_stream(error_msg, session)` | `sendErrorStream(String, AgentSessionApi)` | 同名语义映射 | 完全映射 | - |
| `_unwrap_result(result)` | `unwrapResult(Object)` | 同名语义映射 | 完全映射 | - |
| `_get_first_interrupt(...)` | `getFirstInterrupt(List<Object>)` | 同名语义映射 | 完全映射 | - |
| `_count_interactions(...)` | `countInteractions(List<Object>)` | 同名语义映射 | 完全映射 | - |
| `create_message(inputs)` | 无公开同名 API；Java 通过 `ControllerAgent` / `InputEvent` 管线构建输入事件 | 入口下沉 | 部分映射 | Java application 层没有公开 `createMessage` |
| `set_llm_controller_prompt_template(prompt_template)` | `setPromptTemplate(...)` | 同一职责并入更短命名 | 完全映射 | - |
| `_get_system_prompt_keywords(inputs, user_id)` | `getSystemPromptKeywords(Event, AgentSessionApi)` | 记忆关键词收集映射 | 部分映射 | Java 参数形态不同 |
| `_get_keywords_from_memory(inputs, user_id)` | 无独立同名方法；逻辑合并进 `getSystemPromptKeywords(...)` | helper 合并 | 部分映射 | Java 没有单独记忆查询 helper |
| `_convert_openai_tool_calls_to_tool_call_objects(tool_calls)` | `parseToolArguments(String)` + LLM 输出解析流程 | 逻辑内联 | 部分映射 | Java 未单独暴露 tool-call 转换 helper |
| `_find_plugin_id_by_name(tool_name)` | `findPluginIdByName(String)` | 同名语义映射 | 完全映射 | - |

## 3. workflow_agent / workflow 子包

### 3.1 `WorkflowAgent` -> `WorkflowAgent`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `WorkflowAgent(agent_config)` | `WorkflowAgent(WorkflowAgentConfig)` | 构造语义映射 | 完全映射 | 都通过组合控制器/事件处理器实现工作流调度 |
| `invoke(inputs, session=None)` | `invoke(Object inputs, Session session)` | `async -> 同步返回 ControllerOutput` | 适配映射 | Java 显式托管 session 生命周期 |
| `stream(inputs, session=None)` | `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `AsyncIterator -> Iterator` | 适配映射 | 流尾统一清理 managed session |
| Python 通过基类访问 `agent_config` | `getAgentConfig()` | 属性 -> getter | 适配映射 | Java 显式提供 getter |

### 3.2 `WorkflowController` -> `WorkflowEventHandler`

说明: Python 公开的是 `WorkflowController(IntentDetectionController)`；Java 改为 `WorkflowEventHandler(EventHandler)`，但保留了选流、恢复、打断、默认响应等核心流程。

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `WorkflowController(config=None, context_engine=None, session=None)` | `WorkflowEventHandler(WorkflowAgentConfig, ContextEngine)` | Controller -> EventHandler 构造 | 适配映射 | Java 由上层 `WorkflowAgent` 注入 controller 与 context |
| `_init_intent_detection()` | 无同名公开方法 | lazy 初始化逻辑不再单独暴露 | 部分映射 | Java 在 `detectWorkflowViaLlm(...)` 中直接使用 |
| `setup_from_agent(agent)` | 无 application 层对位 API | 控制器注入流程下沉到 `WorkflowAgent` 构造与 `setEventHandler(...)` | 部分映射 | Java 没有单独 setup hook |
| `intent_detection(event, session)` | `handleUserInput(...)` + `detectWorkflowViaLlm(...)` + `findInterruptedTask(...)` | 公共 intent API 拆散为内部 helper | 部分映射 | Java 未公开 `intentDetection(...)` |
| `exec_task(message_content, task, session)` | `execTask(Event, Task, AgentSessionApi, WorkflowSchema)` | 同名语义映射 | 完全映射 | - |
| `_handle_resume(...)` | `buildResumeArguments(...)` + `execTask(...)` | 恢复逻辑拆分 | 部分映射 | Java 没有同名公共 helper |
| `interrupt_task(task, session, interaction_data)` | `interruptTask(Task, AgentSessionApi, List<Object>)` | 同名语义映射 | 完全映射 | - |
| `_detect_workflow_via_llm(event, session)` | `detectWorkflowViaLlm(Event, AgentSessionApi)` | 同名语义映射 | 完全映射 | - |
| `_ensure_intent_detection_initialized(session)` | 无同名 helper | 初始化逻辑内联 | 部分映射 | Java 没有显式初始化方法 |
| `_should_resume_interrupted_task(task, event, session)` | `shouldResumeInterruptedTask(...)` | 同名语义映射 | 完全映射 | - |
| `_find_interrupted_task_by_node_id(interactive_input, session)` | `findInterruptedTaskByNodeId(...)` | 同名语义映射 | 完全映射 | - |
| `_find_interrupted_task(workflow, session)` | `findInterruptedTask(WorkflowSchema, AgentSessionApi)` | 同名语义映射 | 完全映射 | - |
| `_create_new_task(event, workflow)` | `createNewTask(Event, WorkflowSchema, AgentSessionApi)` | 同名语义映射 | 完全映射 | Java 增加 session 参数 |
| `_get_required_input_key(schema)` | `getRequiredInputKey(Map<String, Object>)` | 同名语义映射 | 完全映射 | - |
| `_filter_workflow_inputs(...)` | `filterWorkflowInputs(...)` | 同名语义映射 | 完全映射 | - |
| `_get_interrupted_component_id(task, session)` | `extractComponentIdFromInteractionData(...)` + `findInterruptedTask(...)` | 逻辑拆分 | 部分映射 | Java 无独立同名 helper |
| `_clear_interrupted_state(task, session)` | `clearInterruptedState(Task, AgentSessionApi, String workflowId)` | 增加 workflowId 参数 | 适配映射 | - |
| `_extract_component_id_from_interaction_data(...)` | `extractComponentIdFromInteractionData(List<Object>)` | 同名语义映射 | 完全映射 | - |
| `_extract_interaction_value_from_interaction_data(...)` | `extractInteractionValueFromInteractionData(List<Object>)` | 同名语义映射 | 完全映射 | - |
| `_get_first_interrupt(...)` | `getFirstInterrupt(List<Object>)` | 同名语义映射 | 完全映射 | - |
| `_count_interactions(...)` | `countInteractions(List<Object>)` | 同名语义映射 | 完全映射 | - |
| `_find_workflow_from_agent(workflow_id, session)` / `_find_workflow_by_id(workflow_id, session)` | 通过 `Runner.resourceMgr().getWorkflow(...)` 直接查询 | helper 内联 | 部分映射 | Java 无独立 finder API |
| `_is_workflow_interrupted(result)` | `isWorkflowInterrupted(Object)` | 同名语义映射 | 完全映射 | - |

## 4. schema / config 类型映射

### 4.1 `LegacyReActAgentConfig` -> `LlmAgentConfig`

| Python 字段 / API | Java 字段 / API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `id/version/description/workflows/model/tools` | `id/version/description/workflows/model/tools` | 同名语义映射 | 完全映射 | - |
| `controller_type` | 无对应字段 | 配置字段缺失 | 缺失 | Java 默认通过 `LlmAgent` 固化为 LLM agent |
| `prompt_template_name` | 无对应字段 | 模板名缺失 | 缺失 | Java 只保留 `promptTemplate` |
| `prompt_template` | `promptTemplate` | `snake_case -> camelCase` | 完全映射 | - |
| `plugins` | `plugins` | 同名语义映射 | 完全映射 | - |
| `memory_scope_id` | `memoryScopeId` | `snake_case -> camelCase` | 完全映射 | - |
| `agent_memory_config` | `agentMemoryConfig` | 同名语义映射 | 部分映射 | Java 字段类型与 Python 来源模块不完全一致 |
| `constrain.max_iteration` | `constrain.maxIteration` | 子字段映射 | 完全映射 | - |
| `constrain.reserved_max_chat_rounds` | 无对应字段 | 上下文轮数约束缺失 | 缺失 | Java 未保留该约束项 |
| `context_window_limit` property | 无对应 getter/property | 别名属性缺失 | 缺失 | Python 用于兼容新控制器 |
| Python alias `ReActAgentConfig = LegacyReActAgentConfig` | 无对应 alias | 兼容别名缺失 | 缺失 | Java 仅保留 `LlmAgentConfig` |

### 4.2 `WorkflowAgentConfig` -> `WorkflowAgentConfig`

| Python 字段 / API | Java 字段 / API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `id/version/description/workflows` | `id/version/description/workflows` | 同名语义映射 | 完全映射 | - |
| `controller_type` | 无对应字段 | 配置字段缺失 | 缺失 | Java 默认由 `WorkflowAgent` 固化 |
| `start_workflow` | 无对应字段 | 起始 workflow 缺失 | 缺失 | - |
| `end_workflow` | 无对应字段 | 结束 workflow 缺失 | 缺失 | - |
| `global_variables` | 无对应字段 | 全局变量配置缺失 | 缺失 | - |
| `global_params` | 无对应字段 | 全局参数配置缺失 | 缺失 | - |
| `constrain` | 无对应字段 | 约束配置缺失 | 缺失 | - |
| `default_response` | `defaultResponse` | 同名语义映射 | 部分映射 | Java `DefaultResponse` 字段更少 |
| `context_engine_config` | `contextEngineConfig` | `snake_case -> camelCase` | 完全映射 | - |

### 4.3 `DefaultResponse` -> `DefaultResponse`

| Python 字段 | Java 字段 | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `type: Literal["text", "workflow"]` | 无对应字段 | 默认响应类型缺失 | 缺失 | Java 只保留文本内容 |
| `text` | `text` | 同名语义映射 | 完全映射 | - |

### 4.4 `WorkflowSchema` -> `WorkflowSchema`

| Python 字段 | Java 字段 | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `id/name/version/description` | `id/name/version/description` | 同名语义映射 | 完全映射 | - |
| `inputs` | `inputParams` | `inputs -> inputParams` | 适配映射 | 语义接近，但字段名变化 |

### 4.5 `PluginSchema` -> `PluginSchema`

| Python 字段 | Java 字段 | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `id/name/description` | `id/name/description` | 同名语义映射 | 完全映射 | - |
| `version` | 无对应字段 | 版本字段缺失 | 缺失 | - |
| `inputs` | 无对应字段 | 输入 schema 缺失 | 缺失 | - |
| `plugin_id` | 无对应字段 | plugin 标识别名缺失 | 缺失 | - |

### 4.6 `AgentMemoryConfig` 对位情况

说明: Python application 实际使用的是 `openjiuwen.core.memory.config.config.AgentMemoryConfig`；Java application 目录里存在 `application.schema.AgentMemoryConfig`，但 `LlmAgentConfig` 实际 import 的是 `com.openjiuwen.core.memory.config.AgentMemoryConfig`。

| Python 字段 | Java 对位 | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `mem_variables: list[Param]` | `memVariables: List<MemVariable>` | `Param -> MemVariable{name, description}` | 部分映射 | Java application 版做了简化建模 |
| `enable_long_term_mem` | `enableLongTermMem` | `snake_case -> camelCase` | 完全映射 | - |
| `enable_fragment_memory` | `enableFragmentMemory` | `snake_case -> camelCase` | 完全映射 | - |
| `enable_summary_memory` | `enableSummaryMemory` | `snake_case -> camelCase` | 完全映射 | - |
| Python 直接复用 memory 模块配置 | Java 额外定义 `application.schema.AgentMemoryConfig` | Java-only 辅助类型 | Java 扩展 | 但它不是 `LlmAgentConfig` 当前实际引用的字段类型 |

## 5. 结论

- 从主运行行为看，Java application 模块已经覆盖了 Python application 的两条核心能力链路: `LLMAgent` 与 `WorkflowAgent`。
- 最大差异不是“功能完全没有实现”，而是“API 载体发生了结构性变化”: Python 公开 Controller，Java 改成 EventHandler；Python 提供 helper factory，Java 倾向 builder/构造器。
- 如果目标是“功能对齐”，Java application 已基本可用；如果目标是“公开 API 也尽量兼容 Python”，则仍需补工厂函数、Controller facade 和若干 schema 字段。