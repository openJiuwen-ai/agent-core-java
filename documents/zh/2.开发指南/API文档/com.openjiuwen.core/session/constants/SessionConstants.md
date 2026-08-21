# com.openjiuwen.core.session.constants.SessionConstants

## 类 SessionConstants

```java
public final class SessionConstants
```

session 模块常量集合，覆盖超时、循环限制、检查点控制和环境变量键名。

## 字段

| 签名 | 说明 |
| --- | --- |
| `public static final String WORKFLOW_EXECUTE_TIMEOUT = "_execute_timeout"` | 工作流执行超时配置键。 |
| `public static final String WORKFLOW_STREAM_FRAME_TIMEOUT = "_stream_frame_timeout"` | 流式输出逐帧超时配置键。 |
| `public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT = "_stream_first_frame_timeout"` | 流式输出首帧超时配置键。 |
| `public static final String COMP_STREAM_CALL_TIMEOUT_KEY = "_comp_stream_call_timeout"` | transform/collect 组件的流调用超时键。 |
| `public static final String STREAM_INPUT_GEN_TIMEOUT_KEY = "_stream_input_generator_timeout"` | 流输入生成器超时键。 |
| `public static final String END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY = "_end_comp_template_render_position_timeout"` | 终止组件模板渲染位置超时键。 |
| `public static final String END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY = "_end_comp_template_branch_render_timeout"` | 终止组件批量读取超时键。 |
| `public static final String LOOP_NUMBER_MAX_LIMIT_KEY = "_loop_number_max_limit"` | 循环组件最大次数配置键。 |
| `public static final int LOOP_NUMBER_MAX_LIMIT_DEFAULT = 1000` | 循环组件默认最大次数。 |
| `public static final String FORCE_DEL_WORKFLOW_STATE_KEY = "_force_del_workflow_state"` | 强制删除工作流状态的控制键。 |
| `public static final String LOOP_ID = "_loop_id"` | 循环节点 ID 字段名。 |
| `public static final String INDEX = "_index"` | 循环索引字段名。 |
| `public static final String WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY = "WORKFLOW_EXECUTE_TIMEOUT"` | 工作流执行超时对应的环境变量键。 |
| `public static final String WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FRAME_TIMEOUT"` | 逐帧超时对应的环境变量键。 |
| `public static final String WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY = "WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT"` | 首帧超时对应的环境变量键。 |
| `public static final String COMP_STREAM_CALL_TIMEOUT_ENV_KEY = "COMP_STREAM_CALL_TIMEOUT"` | 组件流调用超时对应的环境变量键。 |
| `public static final String STREAM_INPUT_GEN_TIMEOUT_ENV_KEY = "STREAM_INPUT_GEN_TIMEOUT"` | 流输入生成超时对应的环境变量键。 |
| `public static final String LOOP_NUMBER_MAX_LIMIT_ENV_KEY = "LOOP_NUMBER_MAX_LIMIT"` | 循环次数上限对应的环境变量键。 |
| `public static final String FORCE_DEL_WORKFLOW_STATE_ENV_KEY = "FORCE_DEL_WORKFLOW_STATE"` | 强制删除工作流状态对应的环境变量键。 |

## 超时配置说明

上述字段中与超时相关的键（`_execute_timeout`、`_stream_frame_timeout`、`_stream_first_frame_timeout`、`_comp_stream_call_timeout`、`_stream_input_generator_timeout`）默认值为 `-1.0`，表示"调用方未显式指定超时"。`-1` 的语义在不同 key 上行为不同：

| 键 | 默认值 | 为 -1 时的回退行为 |
| --- | --- | --- |
| `_stream_input_generator_timeout` | `-1.0` | `StreamProcessor` 构造时 `timeoutSeconds = 0`，迭代器走 `TimeoutConstants.BLOCKING_QUEUE_MS`（默认 60s）兜底 poll。详见 [TimeoutConstants](../common/constants/TimeoutConstants.md)。 |
| `_comp_stream_call_timeout` | `-1.0` | `Vertex.streamCalledTimeout` 置 0，`streamDone.get()` 走 `TimeoutConstants.FUTURE_MS`（默认 300s）兜底。 |
| `_stream_frame_timeout` | `-1.0` | 帧间超时置 -1，不走框架超时；若工作流执行截止时间有效则用其剩余时间，否则无限等待。 |
| `_stream_first_frame_timeout` | `-1.0` | 同上，首帧超时置 -1，不走框架超时。 |
| `_execute_timeout` | `60.0` | 工作流执行总超时，默认 60 秒；为正数时直接生效。 |

要点：

- `_stream_input_generator_timeout` 和 `_comp_stream_call_timeout` 为 -1 时会回退到 `TimeoutConstants` 的框架默认值（可通过 `-Dopenjiuwen.timeout.*` 系统属性覆盖）。
- `_stream_frame_timeout` 和 `_stream_first_frame_timeout` 为 -1 时**不**回退到框架默认，而是依赖工作流执行截止时间或无限等待。
- 显式配置正数超时值时，调用方值优先于框架默认值。
- 环境变量覆盖优先级：`Config.WORKFLOW_SESSION_VARS`（线程本地） > 系统环境变量 > 内置默认值。

## 说明

- 常量类私有化了构造方法，按源码设计只作为静态常量容器使用。
