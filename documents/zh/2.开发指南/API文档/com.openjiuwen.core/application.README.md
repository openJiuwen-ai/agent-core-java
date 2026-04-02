# application

`com.openjiuwen.core.application` 提供应用层 Agent、配置 Schema 与工作流相关类型，覆盖 `llm`、`schema`、`workflow` 三个子包。

## Modules

| 模块 | 说明 |
|---|---|
| [`llm`](./application/llm.README.md) | ReAct 风格 Agent、控制器封装与任务中断状态对象。 |
| [`schema`](./application/schema.README.md) | 应用层 Agent 配置、工作流引用、默认响应与约束模型。 |
| [`workflow`](./application/workflow.README.md) | 工作流 Agent、控制器与意图识别结果类型。 |

## 说明

- 本包文档以 `LlmAgent.java`、`WorkflowAgent.java`、相关 Schema 类型和应用层回归测试为依据。
- 公开 API 重点关注配置含义、构造入口、调用方法和运行时状态对象。
