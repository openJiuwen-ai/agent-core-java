# com.openjiuwen.core.common.constants.TimeoutConstants

## 类 TimeoutConstants

```java
public final class TimeoutConstants
```

框架统一的阻塞操作超时常量集合。Issue #70 维度 IV（热路径阻塞与异步化治理）引入。

所有阻塞调用点（`take` / `get` / `await` / `join`）在调用方未显式指定超时时，均应回退到本类的默认值，避免因上游 producer / worker / 子进程异常导致整个 agent 轮次无限挂起。

## 系统属性覆盖

每个默认值都支持通过 JVM 系统属性覆盖，无需改代码即可调优：

```bash
-Dopenjiuwen.timeout.blocking-queue-ms=120000
-Dopenjiuwen.timeout.future-ms=600000
-Dopenjiuwen.timeout.latch-ms=60000
-Dopenjiuwen.timeout.process-join-ms=1200000
```

所有值在类初始化时解析一次并缓存。非法值（非数字 / 非正数）会回退到内置默认值，并向 `Loggers.PERFORMANCE` 输出一条警告日志。

## 常量字段

| 字段 | 类型 | 默认值 | 覆盖系统属性 | 生效位置 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `PROP_BLOCKING_QUEUE_MS` | `String` | `"openjiuwen.timeout.blocking-queue-ms"` | — | — | 阻塞队列超时的系统属性键名。 |
| `PROP_FUTURE_MS` | `String` | `"openjiuwen.timeout.future-ms"` | — | — | Future 超时的系统属性键名。 |
| `PROP_LATCH_MS` | `String` | `"openjiuwen.timeout.latch-ms"` | — | — | Latch 超时的系统属性键名。 |
| `PROP_PROCESS_JOIN_MS` | `String` | `"openjiuwen.timeout.process-join-ms"` | — | — | 子进程 join 超时的系统属性键名。 |
| `DEFAULT_BLOCKING_QUEUE_MS` | `long` | `60_000L` | — | — | 阻塞队列默认超时（60 秒）。 |
| `DEFAULT_FUTURE_MS` | `long` | `300_000L` | — | — | Future 默认超时（5 分钟）。 |
| `DEFAULT_LATCH_MS` | `long` | `30_000L` | — | — | Latch 默认超时（30 秒）。 |
| `DEFAULT_PROCESS_JOIN_MS` | `long` | `600_000L` | — | — | 子进程 join 默认超时（10 分钟）。 |
| `BLOCKING_QUEUE_MS` | `long` | `60_000L` | `openjiuwen.timeout.blocking-queue-ms` | `StreamProcessor.pollPayload()` 主循环队列 poll；`StreamProcessor` 迭代器兜底 poll；`TaskManager` 任务队列 poll | 阻塞队列空闲超时。度量"队列多久没收到新数据"，不是总时长。60 秒默认值对 reasoning 模型 20-25 秒思考阶段留有 2 倍余量。 |
| `FUTURE_MS` | `long` | `300_000L` | `openjiuwen.timeout.future-ms` | `Vertex.awaitStreamInAbilities` 的 `streamDone.get()`；`Vertex.runExecutable` 的 `future.get()`；`Task.waitFor` 的 `future.get()`；`Workflow` 工作流执行 future 等待 | Future / CountDownLatch 总等待超时。用于整段流式处理或任务执行的总时长上限。 |
| `LATCH_MS` | `long` | `30_000L` | `openjiuwen.timeout.latch-ms` | `Vertex` 的 `abilityLatch.await()`（流启动阶段等待所有 ability 就绪） | CountDownLatch 等待超时。用于流式启动阶段等待上游 ability 注册就绪。 |
| `PROCESS_JOIN_MS` | `long` | `600_000L` | `openjiuwen.timeout.process-join-ms` | `BashTool`、`CodeTool`、`PowerShellTool` 的子进程 `process.waitFor()` | 子进程 join 超时。用于等待外部命令行工具执行完毕。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static long processJoinMs()` | 返回子进程 join 超时值。因 `PROCESS_JOIN_MS` 为包私有，对外通过此方法暴露给非 `constants` 包的调用方（如 `harness.tools` 下的工具类）。 |
| `public static long resolveCallerMs(Double callerTimeoutSeconds, long defaultMs)` | 将调用方传入的超时秒数（可为 `null` / 非正数）解析为毫秒；调用方未指定时回退到 `defaultMs`。集中处理"调用方没传超时 → 用框架默认"的统一模式。 |
| `public static long resolveCallerMs(Long callerTimeoutMs, long defaultMs)` | 同上，入参为毫秒。 |
| `public static long futureMs(Double callerTimeoutSeconds)` | 将调用方传入的超时秒数解析为毫秒；未指定时回退到 `FUTURE_MS`。用于 `future.get(timeout, unit)` 调用点。 |

## 与 SessionConstants 超时 key 的关系

`SessionConstants` 中的 session 级超时键默认值为 `-1.0`，表示"调用方未显式指定超时"。不同 key 在 -1 时的回退行为不同，需按调用点区分：

| Session 超时键 | 默认值 | 为 -1 时的行为 |
| --- | --- | --- |
| `_stream_input_generator_timeout` | `-1.0` | `StreamProcessor` 构造时 `timeoutSeconds = 0`，迭代器走 `BLOCKING_QUEUE_MS`（60s）兜底 poll。 |
| `_comp_stream_call_timeout` | `-1.0` | `Vertex.streamCalledTimeout` 置 0，`streamDone.get()` 走 `FUTURE_MS`（300s）兜底。 |
| `_stream_frame_timeout` | `-1.0` | `Workflow` 帧间超时置 -1，不走框架超时；若工作流执行截止时间有效则用其剩余时间，否则无限等待。 |
| `_stream_first_frame_timeout` | `-1.0` | 同上，首帧超时置 -1，不走框架超时。 |
| `_execute_timeout` | `60.0`（正数） | 工作流执行总超时，默认 60 秒；为正数时直接生效。 |

即：`_stream_input_generator_timeout` 和 `_comp_stream_call_timeout` 为 -1 时会回退到 `TimeoutConstants` 框架默认值；`_stream_frame_timeout` 和 `_stream_first_frame_timeout` 为 -1 时**不**回退到框架默认，而是依赖工作流执行截止时间或无限等待。

## 说明

- 该类为工具类，私有构造器阻止实例化。
- 所有超时值在类加载时一次性解析并缓存为 `static final`，运行期间修改系统属性不会重新生效。
- 非法系统属性值（空串、非数字、非正数）会回退到内置默认值，并通过 `Loggers.PERFORMANCE` 输出一条警告，便于运维定位配置错误。
- 建议调优时优先使用系统属性覆盖，而非修改源码默认值——这样可以在不改代码的前提下针对不同部署环境（如长推理模型 vs 快速问答）灵活调整。
