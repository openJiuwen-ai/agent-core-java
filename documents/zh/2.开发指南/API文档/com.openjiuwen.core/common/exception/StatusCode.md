# com.openjiuwen.core.common.exception.StatusCode

## 枚举 StatusCode

```java
public enum StatusCode
```

`StatusCode` 是框架统一状态码枚举。当前源码共定义 `253` 个枚举值，用于携带整数编码与默认消息模板，覆盖 workflow、component、agent、tool、retrieval、common、session 等多个域。

## 运行时字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `int` | 当前枚举值对应的整数状态码。 |
| `errmsg` | `String` | 默认消息模板；模板中的占位符使用 `{placeholder}` 语法。 |

## 代表性枚举值

| 成员 | 编码 | 模板 |
| --- | ---: | --- |
| `SUCCESS` | `0` | `success` |
| `ERROR` | `-1` | `error` |
| `WORKFLOW_EXECUTION_ERROR` | `100102` | `workflow execution has error, error=''{reason}'', workflow=''{workflow}''` |
| `COMPONENT_BRANCH_EXECUTION_ERROR` | `101021` | `component branch execution error, error=''{reason}''` |
| `AGENT_TOOL_EXECUTION_ERROR` | `120001` | `agent tool execution error, reason: {error_msg}` |
| `RUNNER_TERMINATION_ERROR` | `110002` | `runner is already terminate` |
| `TOOL_EXECUTION_ERROR` | `182012` | `tool execution error, tool card={card}, reason={reason}` |
| `COMMON_LOG_PATH_INVALID` | `183000` | `common log_path is invalid, reason: {error_msg}` |
| `COMMON_SSL_CONTEXT_INIT_FAILED` | `188000` | `common ssl_context initialization failed, reason: {error_msg}` |
| `SCHEMA_VALIDATE_INVALID` | `189001` | `validate data with schema failed, error=''{reason}'', data={data}` |
| `GUARDRAIL_BLOCKED` | `190000` | `guardrail blocked: risk_type=''{risk_type}'', risk_level=''{risk_level}'', event=''{event}''` |

## 前缀覆盖

| 前缀 | 成员数 | 说明 |
| --- | ---: | --- |
| `RETRIEVAL` | 51 | 检索、索引与向量存储相关失败。 |
| `COMPONENT` | 35 | 工作流组件校验与运行失败。 |
| `AGENT` | 19 | Agent 控制、任务与工具调用失败。 |
| `RESOURCE` | 17 | 资源、标签与 MCP Server 失败。 |
| `TOOL` | 15 | 工具定义与执行失败。 |
| `WORKFLOW` | 13 | 工作流编排与执行失败。 |
| `MEMORY` | 12 | 记忆引擎失败。 |
| `COMMON` | 11 | 公共日志、JSON、SSL 与通用工具失败。 |
| `TOOLCHAIN` | 9 | Toolchain 优化、训练与评测失败。 |
| `MODEL` | 6 | 模型配置与调用失败。 |
| `STREAM` | 6 | 流式输出与 actor 相关失败。 |
| `SYS` | 6 | 系统操作失败。 |
| `CHECKPOINTER` | 5 | Checkpointer、Tracer 与状态存储失败。 |
| `GRAPH` | 5 | 图执行失败。 |
| `MESSAGE` | 5 | 消息队列生产、消费与处理失败。 |
| `PROMPT` | 5 | Prompt 组装与模板失败。 |
| `DRAWABLE` | 4 | 图可视化辅助失败。 |
| `PREGEL` | 4 | Pregel 专用状态码族。 |
| `CONTEXT` | 3 | 上下文引擎失败。 |
| `REMOTE` | 3 | 远程 Agent 失败。 |
| `STORE` | 3 | 存储支撑失败。 |
| `EXPRESSION` | 2 | 表达式解析与求值失败。 |
| `RUNNER` | 2 | Runner 生命周期失败。 |
| `SCHEMA` | 2 | Schema 校验与格式化失败。 |
| `TRACER` | 2 | Tracer 子系统失败。 |
| `ARRAY` | 1 | 数组条件失败。 |
| `COMP` | 1 | 组件交互失败。 |
| `DIST` | 1 | 分布式消息队列失败。 |
| `ERROR` | 1 | 通用错误状态。 |
| `GUARDRAIL` | 1 | Guardrail 拦截状态。 |
| `INTERACTION` | 1 | 交互输入失败。 |
| `NUMBER` | 1 | 数值条件失败。 |
| `SUCCESS` | 1 | 通用成功状态。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public int getCode()` | 返回当前枚举值的整数编码。 |
| `public int code()` | `getCode()` 的兼容性别名。 |
| `public String getErrmsg()` | 返回未格式化的默认消息模板。 |

## 说明

- `StatusCodeTest` 覆盖了 `SUCCESS = 0`、`ERROR = -1`、全部枚举值都具备 `errmsg`，以及若干关键区间断言。
- `BaseError`、`ErrorHelper` 与 `StatusMapping` 是该枚举在运行时的主要消费者。
- 完整枚举成员清单请以 Java 源码为准。
