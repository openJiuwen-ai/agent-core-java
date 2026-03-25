# Java API 文档索引

`docs/APIdocs` 主要按 `com.openjiuwen.core` 的一级模块组织；此外补充一页 `store-spi.md` 记录 `com.openjiuwen.spi.store` 的存储 SPI 抽象。

## 模块列表

| 文档 | 对应包 | 说明 |
|---|---|---|
| `application.md` | `com.openjiuwen.core.application` | 应用层 Agent 封装、事件处理器与配置模型 |
| `common.md` | `com.openjiuwen.core.common` | 常量、异常、日志、通用 schema 与安全/工具辅助类 |
| `context.md` | `com.openjiuwen.core.context` | 上下文窗口、处理器、压缩器与卸载器 |
| `controller.md` | `com.openjiuwen.core.controller` | 控制器主入口、意图识别、任务调度与控制层 schema |
| `foundation.md` | `com.openjiuwen.core.foundation` | LLM、Prompt、Tool 与基础设施实现 |
| `graph.md` | `com.openjiuwen.core.graph` | 图执行引擎、Pregel 运行时、图存储与可视化 |
| `memory.md` | `com.openjiuwen.core.memory` | 长期记忆、更新、迁移与搜索 |
| `multiagent.md` | `com.openjiuwen.core.multiagent` | 多智能体分组、编排与兼容层接口 |
| `operator.md` | `com.openjiuwen.core.operator` | LLM / Tool / Memory Operator 与调优参数 |
| `retrieval.md` | `com.openjiuwen.core.retrieval` | 嵌入、索引、解析、检索与重排 |
| `runner.md` | `com.openjiuwen.core.runner` | 运行入口、回调链、消息队列与资源管理 |
| `security.md` | `com.openjiuwen.core.security.guardrail` | Guardrail、风险评估与用户输入安全检查 |
| `session.md` | `com.openjiuwen.core.session` | Agent / Workflow Session、状态、交互与流式输出 |
| `singleagent.md` | `com.openjiuwen.core.singleagent` | 单智能体、ReAct Agent、技能与 Rail 回调体系 |
| `store-spi.md` | `com.openjiuwen.spi.store` | 存储 SPI 补充文档，记录 KV / DB / Object / Vector 抽象与查询表达式 |
| `sysop.md` | `com.openjiuwen.core.sysop` | 系统操作注册、本地执行、沙箱与工具适配 |
| `workflow.md` | `com.openjiuwen.core.workflow` | 工作流编排、组件、条件与循环 |

## 说明

- 文档以源码中的显式 API 为准，重点记录对外可用的方法、模型字段与关键行为。
- Lombok 自动生成的方法不逐项铺开时，会在对应条目中注明有哪些 getter / builder 由 Lombok 提供。
- `common.md` 仍负责 `com.openjiuwen.core.common.security` 工具类；`security.md` 只记录 `guardrail` 相关类型。
- `store-spi.md` 是补充页，不替代 `foundation.md` / `retrieval.md` 中的具体实现文档。
