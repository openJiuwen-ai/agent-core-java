# Java API 文档索引

`docs/APIdocs` 目录按 Java 顶层模块组织 API 文档。当前已覆盖源码中的所有一级模块，并补齐了 `application`、`multiagent`、`runner`、`singleagent` 这 4 个新实现模块。

## 模块列表

| 文档 | 对应包 | 说明 |
|------|--------|------|
| `application.md` | `com.openjiuwen.core.application` | 应用层 Agent 封装与配置模型 |
| `common.md` | `com.openjiuwen.core.common` | 常量、异常、日志、通用 Schema 与工具 |
| `context.md` | `com.openjiuwen.core.context` | 上下文引擎、处理器、压缩/卸载组件 |
| `controller.md` | `com.openjiuwen.core.controller` | 控制器、事件处理与任务调度 |
| `foundation.md` | `com.openjiuwen.core.foundation` | 模型、提示词、工具基础设施 |
| `graph.md` | `com.openjiuwen.core.graph` | 图执行引擎与可视化 |
| `memory.md` | `com.openjiuwen.core.memory` | 长期记忆、检索、迁移与更新 |
| `multiagent.md` | `com.openjiuwen.core.multiagent` | 多智能体分组与遗留编排接口 |
| `operator.md` | `com.openjiuwen.core.operator` | LLM、工具、记忆相关 Operator |
| `retrieval.md` | `com.openjiuwen.core.retrieval` | 索引、向量库、检索器与重排 |
| `runner.md` | `com.openjiuwen.core.runner` | 运行入口、回调框架、资源管理与消息队列 |
| `session.md` | `com.openjiuwen.core.session` | Session、状态、流式输出与追踪 |
| `singleagent.md` | `com.openjiuwen.core.singleagent` | 单智能体、Rail 回调、技能体系 |
| `sysop.md` | `com.openjiuwen.core.sysop` | 系统操作与结果模型 |
| `workflow.md` | `com.openjiuwen.core.workflow` | 工作流编排、组件、条件与循环 |

## 说明

- 文档粒度与现有 `workflow.md`、`session.md` 保持一致，按模块聚合核心类型与常用方法。
- `@Data`、`@Builder`、`@SuperBuilder` 等 Lombok 生成的 getter/setter/builder 方法通常不逐项展开，重点记录显式字段与手写 API。
- 对于仍处于兼容层或遗留实现的类型，文档中会明确标注 `@Deprecated` 或兼容用途。
