# com.openjiuwen.core

`com.openjiuwen.core` 是 openJiuwen Java 核心 API 的命名空间根页，用于汇总各一级模块 README，并把共享导航链接到具体子包与公开类型页面。

## 模块

| 模块 | 说明 |
| --- | --- |
| [`application`](./com.openjiuwen.core/application.README.md) | 面向 agent 的应用入口、配置模型与工作流封装。 |
| [`common`](./com.openjiuwen.core/common.README.md) | 共享异常、日志契约、工具类、常量和 schema 辅助类型。 |
| [`context`](./com.openjiuwen.core/context.README.md) | 上下文窗口、token 统计、处理器以及相关 schema 类型。 |
| [`controller`](./com.openjiuwen.core/controller.README.md) | 控制器抽象、运行时编排与 legacy controller 支持。 |
| [`foundation`](./com.openjiuwen.core/foundation.README.md) | 供上层能力复用的底层模型、prompt、store 与 tool 抽象。 |
| [`graph`](./com.openjiuwen.core/graph.README.md) | 图结构定义、Pregel 执行、存储辅助、流式 actor 与可视化类型。 |
| [`memory`](./com.openjiuwen.core/memory.README.md) | 长期记忆引擎、配置模型、管理器、迁移、提取辅助与 prompt 工具。 |
| [`multiagent`](./com.openjiuwen.core/multiagent.README.md) | 多智能体 schema、编排辅助与兼容层。 |
| [`operator`](./com.openjiuwen.core/operator.README.md) | operator 抽象，以及 LLM、memory、tool 调用 operator。 |
| [`retrieval`](./com.openjiuwen.core/retrieval.README.md) | 知识库入口、embedding、query rewriting、indexing 流水线、retriever、vector store、reranker 与辅助工具。 |
| [`runner`](./com.openjiuwen.core/runner.README.md) | runner 入口、callback、queue、resource 与分布式执行辅助。 |
| [`security`](./com.openjiuwen.core/security.README.md) | guardrail 契约、风险模型与用户输入安全辅助。 |
| [`session`](./com.openjiuwen.core/session.README.md) | 会话状态、流式处理、追踪、持久化与 checkpoint 辅助。 |
| [`singleagent`](./com.openjiuwen.core/singleagent.README.md) | 单智能体运行时组件、rails、legacy 辅助与配套 schema。 |
| [`sysop`](./com.openjiuwen.core/sysop.README.md) | system-operation 门面、本地或沙箱实现、注册表 API 与结果 DTO。 |
| [`workflow`](./com.openjiuwen.core/workflow.README.md) | 工作流图构建、运行时模型、direct component、legacy 兼容包、条件组件与辅助工具。 |

## 说明

- 建议先从一级包 README 了解模块职责，再下钻到子包页或叶子类型页。
- `SUMMARY.md` 与本页使用同一套模块树，确保共享导航中的每个入口都能到达对应页面。
