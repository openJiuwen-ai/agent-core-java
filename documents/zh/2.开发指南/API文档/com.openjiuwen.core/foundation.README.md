# foundation

`com.openjiuwen.core.foundation` 汇总 openJiuwen 基础能力层 API，当前包含 LLM、prompt、store 与 tool 等子模块。

## 模块

| 模块 | 说明 |
| --- | --- |
| [`llm`](foundation/llm.README.md) | 统一模型调用入口与会话调度能力。 |
| [`prompt`](foundation/prompt.README.md) | 提示模板与占位符组装能力。 |
| [`store`](foundation/store.README.md) | 存储与检索相关 API 的组织入口。 |
| [`tool`](foundation/tool.README.md) | 工具调用、MCP 与 REST schema 相关 API。 |

## 说明

- foundation 层按领域拆分基础 API，各包 README 再继续链接到具体类型页面。
- 当前 F01 巡检范围重点覆盖 `llm` 及其子包。
