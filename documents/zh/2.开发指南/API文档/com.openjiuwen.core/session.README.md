# session

`com.openjiuwen.core.session` 汇总了对外会话门面、基础会话抽象，以及状态、流式输出、交互、回调、存储和追踪相关子包。

## 模块

| 模块 | 说明 |
| --- | --- |
| [`callback`](./session/callback.README.md) | 会话回调处理器、管理器注册与触发注解。 |
| [`checkpointer`](./session/checkpointer.README.md) | 检查点生命周期、工厂注册与存储适配。 |
| [`config`](./session/config.README.md) | 会话环境变量、工作流配置与 agent 配置。 |
| [`constants`](./session/constants.README.md) | 会话相关环境变量键与配置常量。 |
| [`interaction`](./session/interaction.README.md) | 用户交互载荷、打断恢复流程与等待逻辑。 |
| [`internal`](./session/internal.README.md) | 内部运行时 session 实现、包装器与子工作流会话。 |
| [`state`](./session/state.README.md) | 状态读写、提交回滚抽象与内存实现。 |
| [`store`](./session/store.README.md) | 文件型与内存型会话持久化存储。 |
| [`stream`](./session/stream.README.md) | 流模式、schema、发射器、队列与 writer 管理。 |
| [`tracer`](./session/tracer.README.md) | tracing span、handler、decorator 与辅助工具。 |
| [`utils`](./session/utils.README.md) | 嵌套路径与状态更新相关工具。 |

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AgentGroupSessionApi`](./session/AgentGroupSessionApi.md) | 面向 agent group 的会话门面，继承 `AgentSessionApi`。 |
| [`AgentSessionApi`](./session/AgentSessionApi.md) | 面向单个 agent 的高层会话 API。 |
| [`BaseSession`](./session/BaseSession.md) | 暴露配置、状态、流式输出和回调子系统的基础抽象。 |
| [`NodeSessionApi`](./session/NodeSessionApi.md) | 面向工作流节点的简化会话 API。 |
| [`ProxySession`](./session/ProxySession.md) | 把全部调用转发给底层 `BaseSession` 的代理实现。 |
| [`Session`](./session/Session.md) | `ContextEngine` 依赖的最小会话接口。 |
| [`WorkflowSessionApi`](./session/WorkflowSessionApi.md) | 面向工作流执行生命周期的会话门面。 |

## 说明

- 相关测试：`AgentSessionApiTest`、`SessionBasicTest`、`SessionTest`、`SessionUtilsTest`。
- 根包同时保留了 `checkpointer`、`internal`、`tracer` 等子包的导航入口，便于沿层级继续浏览整个 `session` 树。
