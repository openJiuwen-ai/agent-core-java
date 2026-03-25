# Workflow 模块缺漏清单

## 复核结果

- 这次按 Python/Java 源码逐类逐方法复核后，旧清单里的多项“缺失”已经确认并不成立。
- 已确认 **不是缺漏**、而是已经存在或已通过桥接方式补齐的项包括：
  - `ComponentExecutionParams`
  - `execute_single_component` 的 Java 对位 `ComponentExecutionHelper.executeSingleComponent(...)`
  - `TemplateProcessor / TemplateBatchProcessor / TemplateUtils`
  - `ResponseFormatConfig / OutputParamConfig`
  - `QuestionerStartState / QuestionerInteractState / QuestionerEndState`
  - `KnowledgeRetrievalOutput / ToolComponentInput`
  - `Workflow.stream() -> Iterator<WorkflowChunk>`
  - `BaseWorkflow.compile(session, context)` 的 `context` 透传
  - `BaseWorkflow.toMermaidPng()` / `toMermaidSvg()`
  - `WorkflowSessions.createWorkflowSession(...)`
- 因此，下面只保留这次复核后仍然真实存在的缺口。

## 当前仍缺的部分

| 类别 | 缺口 | Python 现状 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| 绘图入口 | `Workflow.draw(output_format="png"|"svg")` | 同一个 `draw(...)` 同时支持 `mermaid/png/svg` | Java 把二进制输出拆成了 `drawBytes(...)`，`draw(...)` 本身只接受 Mermaid 文本 | API 入口不完全兼容；能力在，但调用方式不同 |
| LLM 资源查找 | `LLMExecutable(model_id=...)` | 走 `Runner.resource_mgr.get_model(...)` | 代码里仍写着 TODO；当前实际要求显式 `modelClientConfig + modelConfig` | 不能按 Python 的 `model_id` 方式初始化 |
| IntentDetection 资源查找 | `IntentDetectionExecutable(model_id=...)` | 同上 | 仍要求显式模型配置，没有接资源管理器 | 初始化语义不一致 |
| Questioner 资源查找 | `QuestionerExecutable(model_id=...)` | 同上 | 明确抛出“Java 暂不支持 model_id 查找” | 初始化语义不一致 |
| Retrieval 资源查找 | `KnowledgeRetrievalExecutable(model_id=...)` | 同上 | 明确抛出“model_id based model lookup not yet supported” | Agentic retrieval 初始化语义不一致 |
| Tool 资源查找 | `ToolComponent(tool_id=...)` | 构造时可自动从资源管理器取工具 | Java 构造函数里只保留注释，当前仍需手动 `bindTool(...)` | Tool 组件初始化语义不一致 |
| 公开 helper 可见性 | `KnowledgeRetrievalExecutable.validate_inputs(...)` | Python 是公开 helper | Java 只有私有 `validateInputs(...)` | 严格按公开 API 对齐时，仍少一个公开入口 |

## 不应再判成缺漏的部分

### 已经对齐，只是名字或入口不同

- `CompIOConfig` -> `IOConfig`
- `NodeSpec` -> `NodeConfig`
- `convert_condition(...)` -> `ExpressionCondition.convertCondition(...)`
- `generate_workflow_key(...)` -> `WorkflowUtils.generateWorkflowKey(...)`
- `execute_single_component(...)` -> `ComponentExecutionHelper.executeSingleComponent(...)`
- `get_default_template(...)` -> `IntentDetectionDefaultConfig.getDefaultTemplate(...)`
- `questioner_default_template(...)` -> `QuestionerDefaultConfig.getDefaultTemplate(...)`
- `create_workflow_session(...)` -> `WorkflowSessions.createWorkflowSession(...)`

### 已经对齐，只是 Java 采用桥接拆层

- `IntentDetectionComponent` -> `IntentDetectionComponent` + `IntentDetectionComponentImpl`
- `SubWorkflowComponent` -> `SubWorkflowComponent` + `SubWorkflowComponentImpl`
- `LoopComponent` -> `LoopComponent` + `LoopComponentImpl`
- `AdvancedLoopComponent` -> `AdvancedLoopComponent` + `AdvancedLoopComponentImpl`
- `WorkflowChunk` 在 Java 中由接口别名承载，而不是 Python 的联合类型别名

## 建议优先级

1. `P0`：补齐 `model_id / tool_id` 资源管理器路径，先处理 `LLM / IntentDetection / Questioner / Retrieval / Tool`
2. `P1`：收口 `Workflow.draw(...)` 与 `drawBytes(...)` 的入口差异，尽量与 Python 保持同一调用面
3. `P2`：把 `KnowledgeRetrievalExecutable.validateInputs(...)` 提升为公开 helper，补齐严格公开 API 对位
