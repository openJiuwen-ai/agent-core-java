# 开发指南

本章节汇总 openJiuwen Java 版本的叙事式开发指南与按源码包结构组织的 API 文档，方便先按主题理解能力，再按包结构下钻到模块 README、子包页和类型页。

## 章节定位

- 教程目录按 Java 当前公开能力组织为四大栏目：`基础功能`、`多智能体`、`工作流`、`高阶用法`。
- 页面命名、正文内容、步骤和参考入口都以 Java 当前仓库中的 API、源码和测试为准。
- API 文档仍然保留按 `com.openjiuwen.core` 包结构组织的导航，适合在读完教程后继续查看具体类型和子包。

## 入口

### 教程栏目

| 栏目 | 适合什么问题 | 主要依据 |
| --- | --- | --- |
| [基础功能](基础功能/README.md) | 如何接入模型、组织提示词模板、注册工具 | `foundation` API、相关源码和测试 |
| [多智能体](多智能体/README.md) | 如何理解 Java 侧 group、多 agent 协作与能力暴露 | `multiagent` API、相关源码和测试 |
| [工作流](工作流/README.md) | 如何搭建工作流、理解组件与执行路径 | `workflow` API、`workflow_agent` 源码与测试 |
| [高阶用法](高阶用法/README.md) | 如何使用检索、记忆、上下文、Session、skills、runner 等高级能力 | `retrieval`、`memory`、`context`、`session`、`runner`、`singleagent`、`sysop` |

### API 文档

- [API文档](API文档/README.md)

## 文档说明

- openJiuwen Core Java 是独立维护的 Java 框架版本，文档不以其他语言版本的命名或抽象作为约束。
- 本目录按 Java 当前公开能力组织阅读路径；如果某个主题在 Java 中采用不同的抽象或命名，页面会直接使用 Java 的真实类型和实现边界。
- 最推荐的阅读方式是：先从教程栏目建立整体认知，再结合对应的 API 文档页、源码和测试深入到具体实现。
