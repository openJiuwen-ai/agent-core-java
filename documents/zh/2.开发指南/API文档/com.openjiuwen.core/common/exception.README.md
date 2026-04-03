# exception

`com.openjiuwen.core.common.exception` 定义框架统一状态码、异常层级、消息模板生成工具，以及按领域拆分的异常封装类型。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`BaseError`](./exception/BaseError.md) | 统一异常基类，负责状态码、消息模板、结构化细节与恢复语义。 |
| [`ExecutionError`](./exception/ExecutionError.md) | 执行期异常基类，默认可恢复且非致命。 |
| [`FrameworkError`](./exception/FrameworkError.md) | 框架或依赖异常基类，默认不可恢复且致命。 |
| [`ValidationError`](./exception/ValidationError.md) | 校验、约束或不支持能力异常基类。 |
| [`Termination`](./exception/Termination.md) | 非错误型控制流终止信号。 |
| [`RunnerTermination`](./exception/RunnerTermination.md) | 额外携带 `reason` 的终止类型。 |
| [`ToolError`](./exception/ToolError.md) | 工具执行异常，可附带 `BaseCard` 元数据。 |

## 工具类型

| 类型 | 说明 |
| --- | --- |
| [`ErrorHelper`](./exception/ErrorHelper.md) | 静态工厂与立即抛出辅助方法。 |
| [`ErrorMessageTemplate`](./exception/ErrorMessageTemplate.md) | 根据结构化输入生成消息模板。 |
| [`StatusCode`](./exception/StatusCode.md) | 框架统一状态码枚举。 |
| [`StatusCodeSpec`](./exception/StatusCodeSpec.md) | 将模板补全为具体状态码条目。 |
| [`StatusCodeTemplate`](./exception/StatusCodeTemplate.md) | 生成状态码建议项的 record。 |
| [`StatusMapping`](./exception/StatusMapping.md) | 负责 `StatusCode` 到具体异常类型的解析。 |

## 领域封装

| 类型 | 说明 |
| --- | --- |
| [`AgentError`](./exception/AgentError.md) | Agent 域执行异常封装。 |
| [`ApplicationError`](./exception/ApplicationError.md) | 应用层执行异常封装。 |
| [`ComponentError`](./exception/ComponentError.md) | 组件执行异常封装。 |
| [`ConfigurationError`](./exception/ConfigurationError.md) | 配置阶段框架异常封装。 |
| [`ContextError`](./exception/ContextError.md) | Context 域异常封装。 |
| [`ExternalDataError`](./exception/ExternalDataError.md) | 外部数据异常封装。 |
| [`ExternalServiceError`](./exception/ExternalServiceError.md) | 外部服务异常封装。 |
| [`GraphError`](./exception/GraphError.md) | 图执行异常封装。 |
| [`GuardrailError`](./exception/GuardrailError.md) | Guardrail 拦截异常封装。 |
| [`ModelError`](./exception/ModelError.md) | 模型相关异常封装。 |
| [`RunnerError`](./exception/RunnerError.md) | Runner 域异常封装。 |
| [`SessionError`](./exception/SessionError.md) | Session 域异常封装。 |
| [`SysOperationError`](./exception/SysOperationError.md) | 系统操作异常封装。 |
| [`ToolchainError`](./exception/ToolchainError.md) | Toolchain 域异常封装。 |
| [`WorkflowError`](./exception/WorkflowError.md) | Workflow 域异常封装。 |

## 说明

- `ErrorTest` 覆盖异常层级、恢复语义、`ErrorHelper` 行为以及 `StatusMapping` 解析结果。
- `StatusCodeTest` 覆盖消息模板生成、`StatusCodeTemplate` / `StatusCodeSpec` 行为与 `StatusCode` 的关键约束。
