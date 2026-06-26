# llm

`com.openjiuwen.core.workflow.component.llm` 包含当前工作流运行时使用的 LLM 组件实现，覆盖模型调用、意图识别、Questioner 状态机，以及输出格式化与校验辅助。

## Types

| Type | Kind | 说明 |
| --- | --- | --- |
| [`ExecutionStatus`](./llm/ExecutionStatus.md) | `enum` | Questioner 状态机的执行状态枚举。 |
| [`FieldInfo`](./llm/FieldInfo.md) | `class` | Questioner 字段抽取与追问流程使用的字段描述对象。 |
| [`IntentDetectionCompConfig`](./llm/IntentDetectionCompConfig.md) | `class` | 意图识别组件的配置对象。 |
| [`IntentDetectionComponentImpl`](./llm/IntentDetectionComponentImpl.md) | `class` | 意图识别工作流组件实现，负责组装可执行体并维护分支路由。 |
| [`IntentDetectionDefaultConfig`](./llm/IntentDetectionDefaultConfig.md) | `class` | 意图识别组件的默认模板与分类配置。 |
| [`IntentDetectionExecutable`](./llm/IntentDetectionExecutable.md) | `class` | 意图识别组件的可执行体，负责调用 LLM、解析分类结果并输出路由信息。 |
| [`IntentDetectionInput`](./llm/IntentDetectionInput.md) | `class` | 意图识别组件的输入模型。 |
| [`IntentDetectionOutput`](./llm/IntentDetectionOutput.md) | `class` | 意图识别组件的输出模型。 |
| [`JsonParser`](./llm/JsonParser.md) | `class` | LLM 响应 JSON 内容解析工具。 |
| [`LLMCompConfig`](./llm/LLMCompConfig.md) | `class` | LLM 组件的配置对象。 |
| [`LLMComponent`](./llm/LLMComponent.md) | `class` | LLM 工作流组件封装，负责按配置创建可执行体。 |
| [`LLMExecutable`](./llm/LLMExecutable.md) | `class` | LLM 组件可执行体，支持同步调用与流式输出。 |
| [`LLMExecutableState`](./llm/LLMExecutableState.md) | `class` | 流式 LLM 执行期间的累计状态与最终输出缓存。 |
| [`LLMPromptFormatter`](./llm/LLMPromptFormatter.md) | `class` | LLM 提示词格式化工具，用于在消息列表中注入结构化输出要求。 |
| [`MessageRole`](./llm/MessageRole.md) | `enum` | LLM 消息角色枚举。 |
| [`OutputCache`](./llm/OutputCache.md) | `class` | Questioner 输出暂存对象。 |
| [`OutputFormatter`](./llm/OutputFormatter.md) | `class` | LLM 响应格式化工具，负责把模型输出转换为工作流结果。 |
| [`OutputParamConfig`](./llm/OutputParamConfig.md) | `class` | 单个输出字段的配置模型。 |
| [`QuestionerComponent`](./llm/QuestionerComponent.md) | `class` | Questioner 工作流组件封装。 |
| [`QuestionerConfig`](./llm/QuestionerConfig.md) | `class` | Questioner 组件配置对象。 |
| [`QuestionerDefaultConfig`](./llm/QuestionerDefaultConfig.md) | `class` | Questioner 组件默认提示词与文案配置。 |
| [`QuestionerDirectReplyHandler`](./llm/QuestionerDirectReplyHandler.md) | `class` | Questioner 的直接回复处理器，负责抽取字段、推进状态并生成追问。 |
| [`QuestionerEndState`](./llm/QuestionerEndState.md) | `class` | Questioner 的结束态对象。 |
| [`QuestionerEvent`](./llm/QuestionerEvent.md) | `enum` | Questioner 状态机事件枚举。 |
| [`QuestionerExecutable`](./llm/QuestionerExecutable.md) | `class` | Questioner 组件可执行体，负责状态恢复、模型初始化与执行调度。 |
| [`QuestionerInput`](./llm/QuestionerInput.md) | `class` | Questioner 组件输入模型。 |
| [`QuestionerInteractState`](./llm/QuestionerInteractState.md) | `class` | Questioner 的用户交互态对象。 |
| [`QuestionerOutput`](./llm/QuestionerOutput.md) | `class` | Questioner 组件输出模型。 |
| [`QuestionerStartState`](./llm/QuestionerStartState.md) | `class` | Questioner 的起始态对象。 |
| [`QuestionerState`](./llm/QuestionerState.md) | `class` | Questioner 状态机基类，负责序列化、状态迁移与会话持久化。 |
| [`QuestionerUtils`](./llm/QuestionerUtils.md) | `class` | Questioner 组件使用的模板、校验与输出整理工具。 |
| [`ResponseFormatConfig`](./llm/ResponseFormatConfig.md) | `class` | LLM 响应格式配置模型。 |
| [`ResponseType`](./llm/ResponseType.md) | `enum` | Questioner 响应类型枚举。 |
| [`SchemaGenerator`](./llm/SchemaGenerator.md) | `class` | 根据输出配置生成 JSON Schema 的工具类。 |
| [`ValidationUtils`](./llm/ValidationUtils.md) | `class` | LLM 组件输入输出校验工具。 |
| [`WorkflowLLMResponseType`](./llm/WorkflowLLMResponseType.md) | `enum` | 工作流 LLM 组件响应类型枚举。 |
| [`WorkflowLLMUtils`](./llm/WorkflowLLMUtils.md) | `class` | 工作流 LLM 返回值处理工具。 |

## Notes

- 当前包共覆盖 `37` 个直接公开类型。
- 当前任务包未提供专用 Java 测试，文档依据源码可见行为整理。
