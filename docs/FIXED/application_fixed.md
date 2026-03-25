# application 模块缺漏复核清单

## 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\application`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\application`
- 本文只记录“Java 相对 Python application 模块仍未完全对齐的公开 API / 配置语义缺口”。
- 默认不计入缺漏:
  - `snake_case -> camelCase`
  - `async -> 同步`
  - `AsyncIterator -> Iterator`
  - Python `Controller` 改写为 Java `EventHandler` 所导致的纯语法差异，但若因此消失了公开 API，则仍计为缺口

## 复核结论

- Java application 的主流程能力已经具备，但如果以 Python application 的“公开类 + 顶层函数 + 配置字段”作为严格基线，仍存在一批明确缺口。
- 缺口集中在三类:
  - Python 顶层 helper / factory 函数没有 Java 兼容入口。
  - Python 公开 `LLMController` / `WorkflowController`，Java 只保留内部 `EventHandler` 实现，没有面向调用方的同级 facade。
  - Java application.schema 字段集比 Python legacy config/schema 明显更窄，部分兼容字段未保留。

## 当前仍缺 / 未完全对齐的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | 顶层 helper | `create_llm_agent_config(...)` | 无对位 factory | 旧代码无法直接按 Python 方式一键构造 `LlmAgentConfig` |
| `P1` | 顶层 helper | `create_llm_agent(...)` | 无对位 factory | 旧代码无法通过一个 helper 同时完成 agent 创建、workflow/tool 绑定 |
| `P1` | `llm_agent.llm_controller.LLMController` | Python 公开 Controller 类 | Java 只有 `LlmEventHandler`，没有公开 `LlmController` facade | 依赖 Controller 级 API 的调用方不能直接迁移 |
| `P1` | `workflow_agent.workflow_controller.WorkflowController` | Python 公开 Controller 类 | Java 只有 `WorkflowEventHandler`，没有公开 `WorkflowController` facade | 依赖 Controller 级 API 的调用方不能直接迁移 |
| `P1` | `convert_timestamp(...)` | Python 公开顶层时间转换 helper | Java 无公开同名函数 | 兼容调用点需要自行重写工具逻辑 |
| `P1` | `LegacyReActAgentConfig.controller_type` | Python 配置显式保留 controller 类型 | Java `LlmAgentConfig` 无该字段 | 配置对象无法保持完整形态兼容 |
| `P1` | `LegacyReActAgentConfig.prompt_template_name` | Python 保留 prompt 模板名 | Java `LlmAgentConfig` 无该字段 | 依赖模板名切换的兼容逻辑缺失 |
| `P1` | `LegacyReActAgentConfig.constrain.reserved_max_chat_rounds` | Python 保留上下文轮数约束 | Java `ConstrainConfig` 仅有 `maxIteration` | 上下文窗口约束配置丢失 |
| `P1` | `LegacyReActAgentConfig.context_window_limit` | Python 提供兼容 property | Java 无对应 getter / alias | 依赖兼容属性的调用方无法直接迁移 |
| `P1` | `WorkflowAgentConfig.start_workflow` | Python 保留开始工作流 | Java 无该字段 | 旧多工作流配置语义不完整 |
| `P1` | `WorkflowAgentConfig.end_workflow` | Python 保留结束工作流 | Java 无该字段 | 旧多工作流配置语义不完整 |
| `P1` | `WorkflowAgentConfig.global_variables` | Python 保留全局变量配置 | Java 无该字段 | 工作流前后共享变量配置缺失 |
| `P1` | `WorkflowAgentConfig.global_params` | Python 保留全局参数配置 | Java 无该字段 | 兼容配置丢失 |
| `P1` | `WorkflowAgentConfig.constrain` | Python 保留约束配置 | Java 无该字段 | 无法按 Python 配置控制行为上限 |
| `P1` | `DefaultResponse.type` | Python 支持 `text/workflow` 两种默认响应类型 | Java `DefaultResponse` 只有 `text` | 默认响应语义被压缩为纯文本 |
| `P1` | `PluginSchema.version` | Python plugin schema 含版本 | Java 无该字段 | 插件版本绑定信息丢失 |
| `P1` | `PluginSchema.inputs` | Python plugin schema 含输入 schema | Java 无该字段 | 插件参数模式无法完整表达 |
| `P1` | `PluginSchema.plugin_id` | Python 保留 plugin 标识别名 | Java 无该字段 | 旧配置兼容性不足 |
| `P1` | `ReActAgentConfig` alias | Python 提供 `ReActAgentConfig = LegacyReActAgentConfig` | Java 无兼容 alias | 基于旧命名的迁移成本更高 |
| `P2` | `WorkflowController.setup_from_agent(...)` | Python 公开 setup hook | Java 无对位公开 API | 自定义调用方无法复用与 Python 相同的注入扩展点 |
| `P2` | `WorkflowController.intent_detection(...)` | Python 公开意图识别入口 | Java 仅在 `WorkflowEventHandler` 私有 helper 中实现 | 该流程无法被单独复用或测试式调用 |
| `P2` | `WorkflowController.exec_task(...)` | Python 公开任务执行入口 | Java 仅保留私有 `execTask(...)` | 无法按 Python 方式单独驱动 workflow 执行 |
| `P2` | `WorkflowController.interrupt_task(...)` | Python 公开打断保存入口 | Java 仅保留私有 `interruptTask(...)` | 无法单独复用打断逻辑 |
| `P2` | `LLMController.handle_event(...)` 以外的 controller helper | Python controller 层对部分流程具备可调用方法 | Java 多数 helper 都是 `private` | 对测试、定制、二次封装不友好 |
| `P2` | `create_message(inputs)` | Python `LLMController` 公开消息创建入口 | Java application 无对应公开 API | 外部无法复用同一消息封装逻辑 |
| `P2` | `WorkflowSchema.inputs` 命名 | Python 用 `inputs` | Java 改为 `inputParams` | 严格按字段名反序列化时不兼容 |
| `P2` | `AgentMemoryConfig` 的 application 对位 | Python application 直接复用 memory 模块的 `AgentMemoryConfig` | Java application 目录虽有 `application.schema.AgentMemoryConfig`，但 `LlmAgentConfig` 实际引用的是另一个包下类型 | 应用层模型存在重复定义，增加理解和序列化成本 |
| `P3` | `AgentMemoryConfig.mem_variables` 元素类型 | Python 使用 `Param`，信息更通用 | Java application 版简化为 `MemVariable{name, description}` | 强类型兼容性弱于 Python |

## 建议优先级

1. 先补最影响兼容迁移的 facade / helper:
   - `createLlmAgentConfig(...)`
   - `createLlmAgent(...)`
   - `LlmController` facade
   - `WorkflowController` facade
2. 再补最关键的 schema 字段:
   - `controllerType`
   - `promptTemplateName`
   - `reservedMaxChatRounds`
   - `startWorkflow/endWorkflow/globalVariables/globalParams/constrain`
   - `DefaultResponse.type`
   - `PluginSchema.version/inputs/pluginId`
3. 最后处理兼容性和可维护性问题:
   - `ReActAgentConfig` alias
   - `createMessage(...)` 等可复用 helper 的公开化
   - application 层 `AgentMemoryConfig` 重复定义的清理或统一

## 不建议按缺陷处理的差异

- `LLMAgent -> LlmAgent`、`set_prompt_template -> setPromptTemplate` 这类命名风格变化属于语言适配。
- Python `async` 改成 Java 同步方法返回 `ControllerOutput/Iterator`，属于运行时模型差异，不单独视为缺陷。
- Java 用 `EventHandler` 承担 controller 实现，是可以接受的架构改写；真正的问题在于没有再补一个面向外部的兼容 facade。

## 小结

- 如果仅看“能不能运行 application 两条主链路”，Java 版已经基本具备。
- 如果看“Python application 的公开 API 能否低成本迁移”，Java 版仍有明显缺口，且集中在 facade 与 schema 两端。
- 下一步最有价值的补齐方向，不是重写核心流程，而是补一层兼容入口，把已经存在的内部实现重新包装成 Python 风格的外部 API。