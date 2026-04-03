# internal

`com.openjiuwen.core.session.internal` 提供 agent、workflow、节点和包装器层的内部会话实现，负责把配置、状态、回调、流输出与 tracer 连接到不同执行作用域。

## Types

| 类型 | 说明 |
| --- | --- |
| [`AgentSession`](./internal/AgentSession.md) | agent 执行时使用的完整会话实现。 |
| [`NodeSession`](./internal/NodeSession.md) | workflow 节点作用域的会话，负责节点级状态与执行标识。 |
| [`RouterSession`](./internal/RouterSession.md) | 面向路由/分支节点的轻量包装器，大多数操作为 no-op。 |
| [`StateSession`](./internal/StateSession.md) | 为包装器子类提供状态与流写入委托的抽象基类。 |
| [`SubWorkflowSession`](./internal/SubWorkflowSession.md) | 嵌套 workflow 使用的节点会话扩展。 |
| [`WorkflowSession`](./internal/WorkflowSession.md) | workflow 运行时的内部会话实现。 |
| [`WrappedSession`](./internal/WrappedSession.md) | 围绕 `BaseSession` 提供便捷访问器的抽象包装器。 |
