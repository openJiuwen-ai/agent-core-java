# Workflow 模块 Python / Java API 映射

## 对照范围

- Python：`agent-core-python/openjiuwen/core/workflow/**`
- Java：`agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/workflow/**`
- 统计口径：
  - Python 统计公开类、公开函数、公开方法（不以下划线开头）
  - Java 统计 `public` 类型与 `public` 方法；接口上的公开方法也纳入
- 本文同时参考 Python `openjiuwen.core.workflow.__init__` 的顶层导出，以及 Java `workflow` 包里的桥接门面类

## 复核结论
- 当前真实仍未完全对齐的点，主要只剩三类：
  - API 入口形态差异：`Workflow.draw("png"|"svg")` 在 Java 中拆成了 `drawBytes(...)`
  - 资源管理器接入差异：多处 `model_id` / `tool_id` 路径还没有像 Python 一样打通 `Runner.resource_mgr`
  - 少量公开可见性差异：例如 Python `KnowledgeRetrievalExecutable.validate_inputs()` 是公开 helper，而 Java 侧是私有 `validateInputs(...)`

## 包级映射

| Python 模块 | Java 对应位置 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.workflow` | `com.openjiuwen.core.workflow` | 部分映射 | 主干类型都在；部分顶层 helper 被拆到 `WorkflowUtils`、`WorkflowSessions`、`ComponentExecutionHelper`。 |
| `openjiuwen.core.workflow.components.base` | `com.openjiuwen.core.workflow.component` | 完全映射 | 基础配置与元数据模型已齐。 |
| `openjiuwen.core.workflow.components.component` | `com.openjiuwen.core.workflow` | 完全映射 | Java 把 `ComponentComposable / ComponentExecutable / WorkflowComponent` 放在根包。 |
| `openjiuwen.core.workflow.components.condition` | `com.openjiuwen.core.workflow.condition` | 部分映射 | 类型齐全；主要差异是 `invoke -> doInvoke`、`__call__ -> evaluate`。 |
| `openjiuwen.core.workflow.components.flow` | `com.openjiuwen.core.workflow.component` + `component.loop` + `com.openjiuwen.core.workflow` | 部分映射 | `BranchRouter` 在根包；`SubWorkflow / Loop / AdvancedLoop` 采用 `接口 + Impl`。 |
| `openjiuwen.core.workflow.components.llm` | `com.openjiuwen.core.workflow.component.llm` | 部分映射 | 类型与大多数公开方法已齐；差异集中在 `model_id` 资源查找。 |
| `openjiuwen.core.workflow.components.resource` | `com.openjiuwen.core.workflow.component.resource` | 部分映射 | 类型已齐；`validate_inputs` 的公开可见性与 `model_id` 路径仍有差异。 |
| `openjiuwen.core.workflow.components.tool` | `com.openjiuwen.core.workflow.component.tool` | 部分映射 | 类型已齐；`tool_id` 自动绑定资源管理器仍未接入。 |

## 命名映射约定

- Python `snake_case` 在 Java 中通常转为 `camelCase`
- Python 模块级函数在 Java 中通常落到静态工具类
- Python 具体类在 Java 中有时拆成 `接口 + Impl`
- Python `AsyncIterator[...]` 在 Java 中通常落为同步 `Iterator<?>`

## 1. 顶层与内核

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Workflow` | `Workflow` | `card -> getCard`; `set_start_comp -> setStartComp`; `add_workflow_comp -> addWorkflowComp`; `set_end_comp -> setEndComp`; `add_connection -> addConnection`; `add_stream_connection -> addStreamConnection`; `add_conditional_connection -> addConditionalConnection`; `invoke -> invoke`; `stream -> stream`; `draw -> draw/drawBytes` | 部分映射 | 主体执行能力已齐；Java 把二进制绘图拆到 `drawBytes(...)`，并额外公开 `invokeSubWorkflow` / `streamSubWorkflow` / `getInternalDrawable`。 |
| `WorkflowCard` | `WorkflowCard` | `tool_info -> toolInfo`; `input_params -> getInputParams/setInputParams`; `str -> str` | 部分映射 | 语义对齐；Java 通过 Lombok getter/setter 承载字段。 |
| `WorkflowOutput` | `WorkflowOutput` | `result/state -> getResult/setResult/getState/setState` | 完全映射 | - |
| `WorkflowChunk` | `WorkflowChunk` | Python 联合类型别名 -> Java 接口别名 | 部分映射 | 两边都代表“工作流流式输出块”，但类型表达方式不同。 |
| `WorkflowChunkType` | `WorkflowChunkType` | 同名枚举；Java 额外有 `getValue()` | 完全映射 | - |
| `WorkflowExecutionState` | `WorkflowExecutionState` | 同名枚举 | 完全映射 | - |
| `generate_workflow_key(workflow_id, workflow_version)` | `WorkflowUtils.generateWorkflowKey(workflowId, workflowVersion)` | 模块级函数 -> 静态工具方法 | 部分映射 | 能力已对齐，入口位置改为 `WorkflowUtils`。 |
| `Session` | `WorkflowSessionApi` | `get_callback_manager -> getCallbackManager`; `get_session_id -> getSessionId`; `get_envs -> getEnvs`; `get_parent -> getParent`; `set_workflow_card -> setWorkflowCard`; `get_workflow_card -> getWorkflowCard` | 部分映射 | 语义对位，但 Java 类型名不是 `Session`。 |
| `create_workflow_session(...)` | `WorkflowSessions.createWorkflowSession(...)` / `WorkflowSessionApi.create(...)` | 模块级工厂 -> 静态工厂 | 部分映射 | Java workflow 包已提供门面，但函数名与承载类不同。 |
| `_workflow.BaseWorkflow` | `BaseWorkflow` | `config -> getConfig`; `stream_actor -> getStreamActor`; `add_workflow_comp -> addWorkflowComp`; `start_comp -> startComp`; `end_comp -> endComp`; `add_connection -> addConnection`; `add_stream_connection -> addStreamConnection`; `add_conditional_connection -> addConditionalConnection`; `compile -> compile`; `drawable -> getDrawable`; `to_mermaid -> toMermaid`; `to_mermaid_png -> toMermaidPng`; `to_mermaid_svg -> toMermaidSvg`; `reset -> reset`; `auto_complete_abilities -> autoCompleteAbilities` | 完全映射 | `compile(session, context)` 在 Java 中确实把 `context` 透传给了图编译链路。 |
| `EdgeTopology` | `EdgeTopology` | `all_edge_nodes -> allEdgeNodes`; `source_map/target_map/... -> getter` | 完全映射 | - |
| `ConnectionType` | `ConnectionType` | 同名枚举；Java 额外有 `getValue()` | 完全映射 | - |
| `ComponentExecutionParams` | `ComponentExecutionParams` | 字段 -> `getNodeId/getSession/getExecutor/getInputs/getInputsSchema/getOutputsSchema/getContext` | 完全映射 | - |
| `execute_single_component(params)` | `ComponentExecutionHelper.executeSingleComponent(params)` | 模块级函数 -> 静态 helper | 部分映射 | 能力已对齐，Java 入口改为 helper 类。 |
| `CompIOConfig` | `IOConfig` | `inputs_schema/outputs_schema -> getInputsSchema/getOutputsSchema + setter` | 部分映射 | 纯改名对位。 |
| `NodeSpec` | `NodeConfig` | `abilities/io_configs/stream_io_configs -> getter/setter` | 部分映射 | 纯改名对位。 |
| `WorkflowSpec` | `WorkflowSpec` | `edges/stream_edges/comp_configs/start_nodes -> getter/setter` | 完全映射 | - |
| `WorkflowConfig` | `WorkflowConfig` | `card/spec/workflow_max_nesting_depth -> getter/setter` | 完全映射 | Java setter 额外对 depth 做边界处理。 |

## 2. 组件基础抽象

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `ComponentAbility` | `ComponentAbility` | `name -> getAbilityName`; `desc -> getDesc` | 完全映射 | - |
| `WorkflowComponentMetadata` | `WorkflowComponentMetadata` | 字段对齐 | 完全映射 | - |
| `ComponentConfig` | `ComponentConfig` | 字段对齐 | 完全映射 | - |
| `ComponentState` | `ComponentState` | 字段对齐 | 完全映射 | - |
| `ComponentComposable` | `ComponentComposable` | `add_component -> addComponent`; `to_executable -> toExecutable` | 完全映射 | Java 用接口默认方法承载。 |
| `ComponentExecutable` | `ComponentExecutable` | `on_invoke -> onInvoke`; `on_stream -> onStream`; `on_collect -> onCollect`; `on_transform -> onTransform`; `invoke/stream/collect/transform -> 同名` | 完全映射 | - |
| `WorkflowComponent` | `WorkflowComponent` | `add_component -> addComponent` | 完全映射 | - |

## 3. 条件、分支、Flow 与 Loop

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Condition` | `Condition` | `invoke -> doInvoke`; `trace_info -> traceInfo`; `__call__ -> evaluate` | 部分映射 | Java 把 callable 门面显式化。 |
| `FuncCondition` | `FuncCondition` | `invoke -> doInvoke`; `trace_info -> traceInfo` | 部分映射 | - |
| `AlwaysTrue` | `AlwaysTrue` | `invoke -> doInvoke`; `trace_info -> traceInfo` | 部分映射 | - |
| `ExpressionCondition` | `ExpressionCondition` | `invoke -> doInvoke`; `trace_info -> traceInfo`; `__call__ -> evaluate` | 部分映射 | `convert_condition(...)` 在 Java 中变为静态方法。 |
| `convert_condition(condition, inputs)` | `ExpressionCondition.convertCondition(String expr)` | 模块级函数 -> 静态方法 | 部分映射 | Java 不再暴露 `inputs` 参数。 |
| `ArrayCondition` | `ArrayCondition` | `invoke -> doInvoke` | 部分映射 | - |
| `ArrayConditionInSession` | `ArrayConditionInSession` | `invoke -> doInvoke` | 部分映射 | - |
| `NumberCondition` | `NumberCondition` | `invoke -> doInvoke` | 部分映射 | - |
| `NumberConditionInSession` | `NumberConditionInSession` | `invoke -> doInvoke` | 部分映射 | - |
| `Branch` | `Branch` | `evaluate -> evaluate`; `trace_info -> traceInfo`; `branch_id/target -> getBranchId/getTarget` | 完全映射 | - |
| `BranchRouter` | `BranchRouter` | `add_branch -> addBranch`; `get_drawable_branch_router -> getDrawableBranchRouter`; `set_session -> setSession`; `__call__ -> apply` | 部分映射 | - |
| `BranchComponent` | `BranchComponent` | `add_branch -> addBranch`; `router -> router`; `invoke -> invoke`; `add_component -> addComponent`; `skip_trace -> skipTrace` | 完全映射 | Java 另有重载 `addBranch(condition, target)`。 |
| `Start` | `Start` | `invoke -> invoke` | 完全映射 | - |
| `EndConfig` | `EndConfig` | `response_template -> getResponseTemplate`; `model_validate(dict) -> fromMap(Map)` | 完全映射 | - |
| `End` | `End` | `set_mix -> setMix`; `invoke/stream/transform/collect -> 同名` | 完全映射 | Java 额外有 `isMix()`。 |
| `TemplateProcessor` | `TemplateProcessor` | `set_data_source_count -> setDataSourceCount`; `current_position -> currentPosition`; `get_current_segment -> getCurrentSegment`; `should_render -> shouldRender`; `advance_position -> advancePosition`; `render -> render`; `reset -> reset`; `render_stream -> renderStream`; `is_finished -> isFinished` | 完全映射 | - |
| `TemplateBatchProcessor` | `TemplateBatchProcessor` | `is_rendered -> isRendered`; `render -> render` | 完全映射 | - |
| `TemplateUtils` | `TemplateUtils` | `render_template -> renderTemplate`; `render_template_to_list -> renderTemplateToList` | 完全映射 | - |
| `SubWorkflowComponent` | `SubWorkflowComponent` + `SubWorkflowComponentImpl` | `invoke/stream/graph_invoker/component_type/sub_workflow` 主要落在 `SubWorkflowComponentImpl`; `getSubWorkflowInternal` 提供 drawable 入口 | 部分映射 | Java 采用 `接口 + Impl`。 |
| `LoopGroup` | `LoopGroup` | `add_workflow_comp -> addWorkflowComp`; `start_nodes -> startNodes`; `start_comp -> startComp`; `end_nodes -> endNodes`; `end_comp -> endComp`; `on_invoke -> onInvoke`; `skip_trace -> skipTrace`; `graph_invoker -> graphInvoker`; `break_components -> getBreakComponents`; `check_validate -> checkValidate` | 完全映射 | Java 额外有 `getStartNodesList/getEndNodesList`。 |
| `LoopComponent` | `LoopComponent` + `LoopComponentImpl` | `invoke/graph_invoker/loop_group` 主要落在 `LoopComponentImpl`; `getLoopGroup()` 位于接口层 | 部分映射 | Java 采用 `接口 + Impl`。 |
| `LoopController` | `LoopController` | `break_loop -> breakLoop`; `is_broken -> isBroken` | 完全映射 | - |
| `LoopBreakComponent` | `LoopBreakComponent` | `set_controller -> setController`; `invoke -> invoke` | 完全映射 | - |
| `LoopSetVariableComponent` | `LoopSetVariableComponent` | `invoke -> invoke`; `generate_value -> generateValue`; `generate_output -> generateOutput` | 完全映射 | - |
| `EmptyExecutable` | `EmptyExecutable` | `on_invoke -> onInvoke`; `skip_trace -> skipTrace` | 完全映射 | - |
| `PostLoopBody` | `PostLoopBody` | `on_invoke -> onInvoke`; `skip_trace -> skipTrace`; `get_finish_index/set_finish_index -> getFinishIndex/setFinishIndex` | 完全映射 | - |
| `AdvancedLoopComponent` | `AdvancedLoopComponent` + `AdvancedLoopComponentImpl` | `register_callback -> registerCallback`; `is_broken -> isBroken`; `break_loop -> breakLoop`; `on_invoke -> onInvoke`; `graph_invoker -> graphInvoker`; `body -> getBody/getBodyExecutable` | 部分映射 | Java 采用 `接口 + Impl`。 |
| `LoopType` | `LoopType` | 同名枚举；Java 额外有 `fromValue/getValue` | 完全映射 | - |
| `LoopInput` | `LoopInput` | 字段 -> getter/setter；`fromMap` 同名 | 完全映射 | - |
| `LoopCallback` | `LoopCallback` | `first_in_loop/out_loop/start_round/end_round -> firstInLoop/outLoop/startRound/endRound` | 完全映射 | Java 额外有 `call(...)`。 |
| `IntermediateLoopVarCallback` | `IntermediateLoopVarCallback` | 同上 | 完全映射 | - |
| `OutputCallback` | `OutputCallback` | 同上 | 完全映射 | - |

## 4. LLM、IntentDetection、Questioner

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `MessageRole` | `MessageRole` | 同名枚举；Java 额外有 `getValue()` | 完全映射 | - |
| `WorkflowLLMResponseType` | `WorkflowLLMResponseType` | 同名枚举；Java 额外有 `getValue()` | 完全映射 | - |
| `WorkflowLLMUtils` | `WorkflowLLMUtils` | `extract_content -> extractContent` | 完全映射 | - |
| `ValidationUtils` | `ValidationUtils` | `raise_invalid_params_error -> raiseInvalidParamsError`; `validate_type -> validateType`; `validate_json_schema -> validateJsonSchema`; `validate_outputs_config -> validateOutputsConfig` | 完全映射 | - |
| `SchemaGenerator` | `SchemaGenerator` | `generate_json_schema -> generateJsonSchema` | 完全映射 | - |
| `JsonParser` | `JsonParser` | `parse_json_content -> parseJsonContent` | 完全映射 | - |
| `OutputFormatter` | `OutputFormatter` | `format_response -> formatResponse` | 完全映射 | - |
| `LLMPromptFormatter` | `LLMPromptFormatter` | `format_prompt -> formatPrompt` | 完全映射 | - |
| `LLMCompConfig` | `LLMCompConfig` | 配置字段对齐 | 完全映射 | - |
| `ResponseFormatConfig` | `ResponseFormatConfig` | `response_type -> getResponseType`; `model_validate(dict) -> fromMap(Map)` | 完全映射 | - |
| `OutputParamConfig` | `OutputParamConfig` | `param_type -> getParamType/setParamType`; `param_description -> getParamDescription/setParamDescription`; `param_required -> isParamRequired/setParamRequired`; `model_validate(dict) -> fromMap(Map)` | 完全映射 | - |
| `LLMExecutableState` | `LLMExecutableState` | `accumulate_content -> accumulateContent`; `build_final_result -> buildFinalResult`; `clear -> clear` | 完全映射 | Java 额外有 `getFinalResult/setFinalResult`。 |
| `LLMExecutable` | `LLMExecutable` | `config -> getConfig`; `invoke -> invoke`; `stream -> stream`; `get_stream_output -> getStreamOutput` | 部分映射 | `model_id` 路径在 Java 中还没有真正接入资源管理器。 |
| `LLMComponent` | `LLMComponent` | `executable -> getExecutable`; `to_executable -> toExecutable` | 完全映射 | - |
| `get_default_template(accept_language)` | `IntentDetectionDefaultConfig.getDefaultTemplate(String)` | 模块级函数 -> 静态方法 | 部分映射 | 入口位置变化。 |
| `IntentDetectionCompConfig` | `IntentDetectionCompConfig` | 配置字段对齐 | 完全映射 | - |
| `IntentDetectionDefaultConfig` | `IntentDetectionDefaultConfig` | 默认模板 / 默认分类配置对齐 | 完全映射 | - |
| `IntentDetectionInput` | `IntentDetectionInput` | `model_validate(dict) -> fromMap(Map)` | 完全映射 | - |
| `IntentDetectionOutput` | `IntentDetectionOutput` | `to_map -> toMap`; 字段 -> getter/setter | 完全映射 | - |
| `IntentDetectionExecutable` | `IntentDetectionExecutable` | `invoke -> invoke`; `set_router -> setRouter`; `post_commit -> postCommit` | 部分映射 | `model_id` 路径仍未像 Python 一样接资源管理器。 |
| `IntentDetectionComponent` | `IntentDetectionComponent` + `IntentDetectionComponentImpl` | `executable/add_component/to_executable/add_branch/router` 主要落在 `IntentDetectionComponentImpl` | 部分映射 | Java 采用抽象占位 + 实现类。 |
| `questioner_default_template(accept_language)` | `QuestionerDefaultConfig.getDefaultTemplate(String)` / `QuestionerDefaultConfig.fromLanguage(...)` | 模块级函数 -> 静态方法 / 工厂方法 | 部分映射 | 入口位置变化。 |
| `ExecutionStatus` | `ExecutionStatus` | 同名枚举；Java 额外有 `fromValue/getValue` | 完全映射 | - |
| `QuestionerEvent` | `QuestionerEvent` | 同名枚举；Java 额外有 `getValue()` | 完全映射 | - |
| `ResponseType` | `ResponseType` | 同名枚举；Java 额外有 `getValue/isValid` | 完全映射 | - |
| `FieldInfo` | `FieldInfo` | 字段对齐 | 完全映射 | - |
| `QuestionerConfig` | `QuestionerConfig` | 配置字段对齐 | 完全映射 | - |
| `QuestionerDefaultConfig` | `QuestionerDefaultConfig` | `from_language -> fromLanguage`; `prompt_template -> getPromptTemplate`; `get_default_template -> getDefaultTemplate` | 完全映射 | - |
| `QuestionerInput` | `QuestionerInput` | `model_validate(dict) -> fromMap/toMap` | 完全映射 | - |
| `OutputCache` | `OutputCache` | 字段对齐 | 完全映射 | - |
| `QuestionerOutput` | `QuestionerOutput` | 字段 -> getter/setter；`put_field -> putField`; `model_dump -> toMap`; `from_fields -> fromFields` | 完全映射 | - |
| `QuestionerState` | `QuestionerState` | `deserialize -> deserialize`; `serialize -> serialize`; `handle_event -> handleEvent`; `is_undergoing_interaction -> isUndergoingInteraction`; `is_fresh_state -> isFreshState` | 完全映射 | Java 额外有 `loadFromSession/storeToSession` 及一组 getter/setter。 |
| `QuestionerStartState` | `QuestionerStartState` | `from_state -> fromState`; `handle_event -> handleEvent` | 完全映射 | - |
| `QuestionerInteractState` | `QuestionerInteractState` | `from_state -> fromState`; `handle_event -> handleEvent` | 完全映射 | - |
| `QuestionerEndState` | `QuestionerEndState` | `from_state -> fromState`; `handle_event -> handleEvent` | 完全映射 | - |
| `QuestionerUtils` | `QuestionerUtils` | `format_template -> formatTemplate`; `format_continue_ask_question -> formatContinueAskQuestion`; `format_questioner_output -> formatQuestionerOutput`; `validate_inputs -> validateInputs`; `is_valid_value -> isValidValue`; `validate_and_convert_type -> validateAndConvertType` | 完全映射 | - |
| `QuestionerDirectReplyHandler` | `QuestionerDirectReplyHandler` | `config/model/state/prompt/handle -> 同名`; `get_state -> getState` | 完全映射 | - |
| `QuestionerExecutable` | `QuestionerExecutable` | `state -> state`; `invoke -> invoke` | 部分映射 | `model_id` 路径在 Java 中明确标注为“暂不支持资源管理器查找”。 |
| `QuestionerComponent` | `QuestionerComponent` | `to_executable -> toExecutable` | 完全映射 | - |

## 5. Retrieval 与 Tool

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `KnowledgeRetrievalCompConfig` | `KnowledgeRetrievalCompConfig` | 配置字段对齐 | 完全映射 | - |
| `KnowledgeRetrievalInput` | `KnowledgeRetrievalInput` | `model_validate(dict) -> fromMap(Map)` | 完全映射 | - |
| `KnowledgeRetrievalOutput` | `KnowledgeRetrievalOutput` | 字段 -> getter/setter；`model_dump -> toMap`; `model_validate(dict) -> fromMap(Map)` | 完全映射 | - |
| `KnowledgeRetrievalExecutable` | `KnowledgeRetrievalExecutable` | `invoke -> invoke`; `validate_inputs -> validateInputs` | 部分映射 | `validateInputs(...)` 在 Java 中是私有 helper；同时 `model_id` 资源查找仍未接入。 |
| `KnowledgeRetrievalComponent` | `KnowledgeRetrievalComponent` | `add_component -> addComponent`; `to_executable -> toExecutable` | 完全映射 | - |
| `ToolComponentConfig` | `ToolComponentConfig` | 配置字段对齐 | 完全映射 | - |
| `ToolComponentInput` | `ToolComponentInput` | `model_dump -> toMap`; `from_map -> fromMap`; 字段读写 -> `getFields/get/put` | 完全映射 | - |
| `ToolComponentOutput` | `ToolComponentOutput` | `model_dump -> toMap`; `model_validate(dict) -> fromMap` | 完全映射 | - |
| `ToolExecutable` | `ToolExecutable` | `invoke -> invoke`; `set_tool -> setTool` | 完全映射 | - |
| `ToolComponent` | `ToolComponent` | `to_executable -> toExecutable`; `bind_tool -> bindTool` | 部分映射 | Java 仍需显式 `bindTool()`；`tool_id` 自动从资源管理器绑定尚未打通。 |

## 6. Java 桥接类与额外公开门面

| Java API | Python 对应 | 角色 | 说明 |
| --- | --- | --- | --- |
| `WorkflowSessions` | `Session` / `create_workflow_session` | Java 桥接 | 为 workflow 包补一个会话工厂门面。 |
| `WorkflowUtils` | `generate_workflow_key()` | Java 桥接 | 用工具类承载模块级函数。 |
| `ComponentExecutionHelper` | `execute_single_component()` | Java 桥接 | 用 helper 类承载模块级函数。 |
| `HasDrawable` | Python 中分散在 `drawable / body / sub_workflow._internal` 的可视化入口 | Java 桥接 | 统一可绘制对象接口。 |
| `IOConfig` | `CompIOConfig` | 改名对位 | 不是缺失。 |
| `NodeConfig` | `NodeSpec` | 改名对位 | 不是缺失。 |
| `IntentDetectionComponentImpl` | `IntentDetectionComponent` | 接口实现拆层 | Java 采用 `抽象/接口 + Impl`。 |
| `SubWorkflowComponentImpl` | `SubWorkflowComponent` | 接口实现拆层 | - |
| `LoopComponentImpl` | `LoopComponent` | 接口实现拆层 | - |
| `AdvancedLoopComponentImpl` | `AdvancedLoopComponent` | 接口实现拆层 | - |
| `WorkflowChunk` | Python `WorkflowChunk` 联合类型别名 | 类型桥接 | Java 用接口承载联合类型语义。 |

## 7. 仍未完全对齐的真实缺口

1. `Workflow.draw(output_format="png"|"svg")`
   - Python 通过同一个 `draw(...)` 返回 `bytes`
   - Java 需要改调 `drawBytes(...)`
   - 这是“入口形态差异”，不是能力缺失
2. `model_id` 资源管理器查找
   - Python：`LLMExecutable / IntentDetectionExecutable / QuestionerExecutable / KnowledgeRetrievalExecutable` 都能走 `Runner.resource_mgr.get_model(...)`
   - Java：目前仍要求显式提供 `modelClientConfig + modelConfig`，或直接抛“不支持”
3. `tool_id` 资源管理器查找
   - Python：`ToolComponent(tool_id=...)` 可自动绑定工具
   - Java：构造函数里只留了 TODO 注释，当前仍需显式 `bindTool(...)`
4. `KnowledgeRetrievalExecutable.validate_inputs`
   - Python 是公开 helper
   - Java 是私有 `validateInputs(...)`
   - 如果要做“公开 API 完全兼容”，这里仍少一个公开入口

## 8. 精确名称未对齐但已语义对位

- `CompIOConfig` -> `IOConfig`
- `NodeSpec` -> `NodeConfig`
- `convert_condition(...)` -> `ExpressionCondition.convertCondition(...)`
- `generate_workflow_key(...)` -> `WorkflowUtils.generateWorkflowKey(...)`
- `execute_single_component(...)` -> `ComponentExecutionHelper.executeSingleComponent(...)`
- `get_default_template(...)` -> `IntentDetectionDefaultConfig.getDefaultTemplate(...)`
- `questioner_default_template(...)` -> `QuestionerDefaultConfig.getDefaultTemplate(...)`
- `Session` -> `WorkflowSessionApi`
- `create_workflow_session(...)` -> `WorkflowSessions.createWorkflowSession(...)`
