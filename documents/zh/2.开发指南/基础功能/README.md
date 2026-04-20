# 基础功能

本栏目面向第一次接入 openJiuwen Java 的开发者，聚焦模型调用、提示词模板和工具系统三条基础主线。内容严格以 Java 当前的 `foundation` API、可运行示例和已有测试为准。

## 栏目边界

- 这里只覆盖运行时接入：模型、模板装配、工具注册与调用。
- 与提示词离线生成/优化相关的 `prompt_builder` 能力不放在本栏目，统一留到高阶用法页说明。
- 与 agent 编排、workflow 组件、多智能体协作相关的内容分别放在对应栏目中。

## 页面映射

| 页面 | 关注点 | 主要 Java 依据 | 说明 |
| --- | --- | --- | --- |
| [接入大模型](接入大模型.md) | 创建模型连接、准备配置、发起基础调用 | `com.openjiuwen.core.foundation.llm`、`examples/reac_agent` | 只讲基础模型接入，不提前展开 agent 编排。 |
| [填充提示词模板](填充提示词模板.md) | 模板内容组织、变量替换、消息模板复用 | `com.openjiuwen.core.foundation.prompt`、`examples/reac_agent` | 重点是运行时 prompt template，不混入离线 prompt builder。 |
| [自定义工具](自定义工具.md) | 本地函数、REST 工具、MCP 与工具卡片接入 | `com.openjiuwen.core.foundation.tool`、`examples/reac_agent` | 以 Java 当前真实注册方式为准。 |

## 推荐阅读顺序

1. 先阅读 [接入大模型](接入大模型.md)，建立最小可调用链路。
2. 再阅读 [填充提示词模板](填充提示词模板.md)，补齐 prompt 输入组织方式。
3. 最后阅读 [自定义工具](自定义工具.md)，把外部能力接入模型或 agent。

## 参考入口

- [API 文档：foundation](../API文档/com.openjiuwen.core/foundation.README.md)
- [示例：ReAct Agent Java Example](../../../../examples/reac_agent/README.md)

## 使用边界

- 本栏目聚焦 Java 当前公开的运行时接入主线：模型、模板装配和工具系统。
- 页面中的步骤、类型名和示例入口都以当前 Java 仓库中的 API、examples 和测试为准。
- 同一能力如果存在多种接入方式，正文默认优先说明当前公开 API 的主路径。
