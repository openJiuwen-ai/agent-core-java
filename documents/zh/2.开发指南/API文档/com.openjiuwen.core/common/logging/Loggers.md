# com.openjiuwen.core.common.logging.Loggers

## 类 Loggers

```java
public final class Loggers
```

`Loggers` 暴露了一组预定义的模块级 logger 常量。所有常量都是 `LazyLogger`，最终会通过 `LogManager.getLogger("...")` 解析真实实现。

## 通用 logger

| 字段 | 对应 `logType` | 说明 |
| --- | --- | --- |
| `COMMON` | `common` | 通用日志入口。 |
| `INTERFACE` | `interface` | 接口层日志入口。 |
| `PERFORMANCE` | `performance` | 性能指标日志入口。 |
| `PROMPT_BUILDER` | `prompt_builder` | prompt 构建过程日志入口。 |

## 核心模块 logger

| 字段 | 对应 `logType` | 说明 |
| --- | --- | --- |
| `AGENT` | `agent` | Agent 模块日志入口。 |
| `MULTI_AGENT` | `multi_agent` | 多 Agent 协作日志入口。 |
| `WORKFLOW` | `workflow` | Workflow 模块日志入口。 |
| `SESSION` | `session` | Session 模块日志入口。 |
| `CONTROLLER` | `controller` | Controller 模块日志入口。 |
| `RUNNER` | `runner` | Runner 模块日志入口。 |
| `SYS_OPERATION` | `sys_operation` | SysOperation 模块日志入口。 |

## 基础能力 logger

| 字段 | 对应 `logType` | 说明 |
| --- | --- | --- |
| `LLM` | `llm` | 模型调用日志入口。 |
| `TOOL` | `tool` | 工具调用日志入口。 |
| `PROMPT` | `prompt` | Prompt 处理日志入口。 |
| `STORE` | `store` | Store 模块日志入口。 |

## 数据与检索 logger

| 字段 | 对应 `logType` | 说明 |
| --- | --- | --- |
| `MEMORY` | `memory` | Memory 模块日志入口。 |
| `RETRIEVAL` | `retrieval` | Retrieval 模块日志入口。 |
| `CONTEXT_ENGINE` | `context_engine` | ContextEngine 模块日志入口。 |

## 执行与扩展 logger

| 字段 | 对应 `logType` | 说明 |
| --- | --- | --- |
| `GRAPH` | `graph` | Graph 模块日志入口。 |
| `OPERATOR` | `operator` | Operator 模块日志入口。 |
| `MCP` | `mcp` | MCP / 扩展协议日志入口。 |

## 说明

- 所有字段都是 `public static final LoggerProtocol`，适合直接在模块代码里作为常量引用。
- `LazyLogger` 保证这些常量在真正写日志之前不会触发 logger 初始化。
