# 执行器Runner

`Runner` 是 Java 版 openJiuwen 的全局执行门面。它把 `Workflow`、`Agent`、`AgentGroup` 的运行入口统一到一组静态方法里，并把资源注册、回调框架、消息队列和检查点初始化放到同一处管理。

如果你只想知道“怎么把一个 workflow 或 agent 跑起来”，从 `Runner` 开始就够了；如果你想进一步理解 session 复用、交互恢复、回调观测或分布式运行，再继续看 `RunnerImpl`、`ResourceMgr`、`CallbackFramework` 与后续几页。

> 这里聚焦 Java 当前公开 API 与示例，不展开底层 MQ 或 distributed server adapter 的实现细节。

## 核心角色

| 类型 | 作用 | 你通常什么时候接触它 |
| --- | --- | --- |
| `Runner` | 全局单例门面，统一暴露 `start()`、`runWorkflow(...)`、`runAgent(...)`、`release(...)` 等静态入口 | 日常调用时 |
| `RunnerImpl` | 真实执行器，负责 session 准备、资源管理、消息队列启动、checkpointer 初始化 | 理解运行时行为时 |
| `RunnerConfig` | 运行配置，包含分布式模式、环境前缀、实例 ID、`checkpointerConfig` 与多租户隔离开关（`enableTenantIsolation` / `tenantDataRoot`） | 启动前配置时 |
| `DistributedConfig` | 分布式运行相关参数，如 topic 模板、超时、并发数和消息队列配置 | 需要跨进程执行时 |
| `ResourceMgr` | 统一管理 workflow、agent、group、tool、model、prompt、sysop 等资源 | 你想按 ID 注册 / 获取资源时 |
| `CallbackFramework` | 事件回调框架，支持 filter、priority、chain、rollback、retry、timeout、metrics 等能力 | 你要观测运行过程或挂接自定义回调时 |

## `Runner` 与 `RunnerImpl` 的关系

Java 当前的 `Runner` 不是一个需要手动 new 的对象，而是一个固定代理到全局 `RunnerImpl` 的静态门面。

- `Runner` 内部持有一个全局 `RunnerImpl("global", RunnerConfig.DEFAULT)`。
- 你通过 `Runner.resourceMgr()`、`Runner.runAgent(...)`、`Runner.runWorkflow(...)` 调用时，实际都转发给这个全局实例。
- 如果需要改配置，应在启动前调用 `Runner.setConfig(...)`，再调用 `Runner.start()`。

这带来两个直接结果：

1. `Runner` 适合作为应用级统一入口；
2. `Runner.release(sessionId)` 清理的是**当前全局默认 checkpointer**里对应 session 的状态，而不是某个局部 runner 私有的状态。

## 启动前先看配置

最常见的配置入口是 `RunnerConfig`。它至少有三类信息：

- 是否启用 distributed mode；
- distributed topic / MQ 配置；
- checkpointer 配置。

```java
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import java.util.Map;

RunnerConfig config = RunnerConfig.builder()
        .distributedMode(false)
        .checkpointerConfig(Map.of(
                "type", "in_memory",
                "conf", Map.of()
        ))
        .build();

Runner.setConfig(config);
Runner.start();
```

### `RunnerConfig` 的一个易混点

源码里 `RunnerConfig` 字段的 builder 默认值是 `distributedMode = true`，但全局 `Runner` 启动时实际使用的是静态常量 `RunnerConfig.DEFAULT`，它是：

- `distributedMode = false`
- fake message queue

理解“默认运行器行为”时，应以 `RunnerConfig.DEFAULT` 为准，而不是只看字段声明的默认值。

## 运行时线程池配置

OpenJiuwen 通过 `OpenJiuwenExecutors` 统一创建、命名和回收运行时线程池。工具调用与未显式指定执行器的异步任务分别使用**共享线程池**；Workflow、Pregel、DeepAgent stream 等模块使用**模块有界线程池**（`newBoundedModulePool`），由同一入口管理，统一使用 `core=max + ArrayBlockingQueue` 排队语义与 `AbortPolicy` 拒绝策略。

AbilityManager 在同一轮模型输出中拿到多个工具或能力调用时，默认会并行执行，并使用工具调用线程池，而不是 JDK 默认的 `ForkJoinPool.commonPool`。

### 配置读取规则

下列配置均支持 **JVM 系统属性** 或 **环境变量** 两种方式。均为进程启动参数，在对应线程池**首次创建时**读取，**不支持运行期热更新**。

读取顺序（`OpenJiuwenExecutors` 内整数配置统一遵循）：

1. 先读 JVM 系统属性（`-D...`）；
2. 若未设置或为空，再读同名语义的环境变量；
3. 若仍未设置、为空或解析失败，使用代码中的默认值（解析失败会打 warning 日志并回退默认值）；
4. 解析成功的值会与允许的最小值取 `max`（线程池大小类配置最小为 `1`）。

**若两种方式同时配置，JVM 系统属性优先于环境变量。**

模块有界池的命名约定（`{模块前缀}` 与线程名前缀一致，如 `deep-agent-stream`）：

| 配置项 | JVM 系统属性 | 环境变量（规则） |
| --- | --- | --- |
| 最大线程数 | `openjiuwen.executor.{模块前缀}.max-size` | `OPENJIUWEN_EXECUTOR_{模块前缀大写}_MAX_SIZE`（`-` 换 `_`） |
| 队列容量 | `openjiuwen.executor.{模块前缀}.queue-size` | `OPENJIUWEN_EXECUTOR_{模块前缀大写}_QUEUE_SIZE` |

示例：`deep-agent-stream` → `OPENJIUWEN_EXECUTOR_DEEP_AGENT_STREAM_MAX_SIZE`。

> **与 LLM HTTP 配置区分**：`openjiuwen.llm.http.*`（如 `max-requests-per-host`）由 Model 客户端在构建 OkHttp 时读取，**仅支持 JVM 系统属性**，且无环境变量兜底；不属于本节 `OpenJiuwenExecutors` 配置体系。

### 共享线程池

| 配置含义 | JVM 系统属性 | 环境变量 | 默认值 |
| --- | --- | --- | --- |
| 工具或能力调用最大线程数 | `openjiuwen.executor.tool-call.max-size` | `OPENJIUWEN_EXECUTOR_TOOL_CALL_MAX_SIZE` | `max(8, CPU 核数 * 2)` |
| 工具或能力调用空闲线程保留时间，单位秒 | `openjiuwen.executor.tool-call.keep-alive-seconds` | `OPENJIUWEN_EXECUTOR_TOOL_CALL_KEEP_ALIVE_SECONDS` | `60` |
| 等待单次工具调用结果的超时时间，单位毫秒；非正数表示不启用 | `openjiuwen.executor.tool-call.timeout-millis` | `OPENJIUWEN_EXECUTOR_TOOL_CALL_TIMEOUT_MILLIS` | `0` |
| 普通后台任务最大线程数 | `openjiuwen.executor.background.max-size` | `OPENJIUWEN_EXECUTOR_BACKGROUND_MAX_SIZE` | `max(8, CPU 核数 * 2)` |
| 普通后台任务空闲线程保留时间，单位秒 | `openjiuwen.executor.background.keep-alive-seconds` | `OPENJIUWEN_EXECUTOR_BACKGROUND_KEEP_ALIVE_SECONDS` | `60` |

默认值设计说明：

- 工具调用和普通后台两个共享线程池的最大线程数默认 `max(8, CPU 核数 * 2)`，兼顾低核机器并发能力和资源上限。
- 两个共享线程池的空闲线程保留时间默认 `60s`，用于覆盖短时突发，同时空闲后可回收线程。
- 单次工具调用超时默认 `0`，表示不启用统一超时，以保持历史兼容性。超时后当前轮不再等待结果并记录失败，但不会中断底层已开始执行的工具；工具自身仍需负责超时或取消。
- 共享池使用 `SynchronousQueue` + `CallerRunsPolicy`：饱和时由提交线程执行，形成背压。

每类最大线程数是当前 JVM 进程内对应共享线程池的上限，多个会话或请求会共同竞争该池。

### 模块有界线程池

由 `OpenJiuwenExecutors.newBoundedModulePool(模块前缀, isDaemon)` 创建的池均纳入统一注册与 JVM 退出回收。未在表中列出的前缀会使用 **GENERIC** 默认（max=`32`，queue=`256`）。

下表为**未配置覆盖时**的默认上限；均可通过上一节的属性 / 环境变量覆盖 `max-size` 与 `queue-size`。

| 模块前缀 | 用途（概要） | 默认 max | 默认 queue | 核心线程超时回收 |
| --- | --- | --- | --- | --- |
| `pregel-task` | Pregel 图节点并行 | `32` | `256` | 是 |
| `workflow-stream` | Workflow 流式执行 | `32` | `256` | 是 |
| `vertex-stream` | Vertex 流能力 | **`max(32, CPU 核数 * 8)`** | `256` | 是 |
| `stream-actor` | StreamActor 流处理 | **`max(32, CPU 核数 * 8)`** | `256` | 是 |
| `end-template-render` | End 模板渲染 | `8` | `128` | 是 |
| `callback-parallel` | 回调并行触发（短生命周期） | `32` | `128` | 是 |
| `mq-server-adapter` | MQ 服务端适配器 | `16` | `128` | 是 |
| `task-manager-worker` | TaskManager 任务 worker | `16` | `128` | 是 |
| `deep-agent-stream` | DeepAgent `stream()` 会话 / task-loop | **`max(32, CPU 核数 * 8)`** | `128` | 否 |
| `react-agent-stream` | ReActAgent `stream()` 会话 | **`max(32, CPU 核数 * 8)`** | `128` | 否 |

模块池共性：

- **统一排队语义**：所有模块池使用 `ArrayBlockingQueue` + `corePoolSize=maxSize`，确保线程全热、队列只做溢出缓冲。自 0.1.15 起不再使用 `SynchronousQueue`——PR #229 加界后 `SynchronousQueue` + 有界 max 变成「满即拒绝」扳机，且各 submit 点普遍缺乏 `RejectedExecutionException` 兜底，排队语义把突发转为缓冲、失败模式更可控。
- **拒绝策略统一为 `AbortPolicy`**：不再使用 `CallerRunsPolicy`（`deep-agent-stream` 旧版曾用，在 SSE pump 阻塞模型下会导致调用线程被长任务钉死）。
- **核心线程超时回收**：仅 `deep-agent-stream` 与 `react-agent-stream` 设为 `allowCoreThreadTimeOut=false`（用户直接感知的 SSE 会话，热线程可消除首 token 的线程创建延迟）；其余池均为 `true`（任务均为 LLM 级，线程创建 1-5ms 相对任务耗时可忽略，空闲后归零以节省内存）。
- `keepAlive=60s`；`allowCoreThreadTimeOut=true` 的池空闲线程（含核心线程）超时后回收，`false` 的池核心线程常驻。

**DeepAgent stream 调优**：`deep-agent-stream` 限制同时进行中的 stream 会话数（I/O 型）。默认可随 CPU 缩放；高并发或长连接场景可显式调大，并配合 LLM 侧 HTTP / 配额限流，见 [DeepAgent 使用指南](DeepAgent/DeepAgent使用指南.md#stream-并发与线程池)。

### 配置示例

JVM 系统属性适合本地测试或启动脚本中直接传参：

```bash
java -Dopenjiuwen.executor.tool-call.max-size=16 \
     -Dopenjiuwen.executor.tool-call.timeout-millis=30000 \
     -Dopenjiuwen.executor.deep-agent-stream.max-size=48 \
     -jar app.jar
```

环境变量适合容器、CI/CD 或平台化部署：

```bash
OPENJIUWEN_EXECUTOR_TOOL_CALL_MAX_SIZE=16 \
OPENJIUWEN_EXECUTOR_TOOL_CALL_TIMEOUT_MILLIS=30000 \
OPENJIUWEN_EXECUTOR_DEEP_AGENT_STREAM_MAX_SIZE=48 \
java -jar app.jar
```

高并发场景建议结合业务压测结果调大；模块池与共享池的上限均为**进程内全局**，多会话会共同竞争。

## `DistributedConfig` 负责什么

如果你需要跨进程或分布式运行，相关入口集中在 `DistributedConfig`：

- `requestTimeout`
- `maxRequestConcurrency`
- `messageQueueConfig`
- `agentTopicTemplate`
- `replyTopicTemplate`
- `envPrefix`

当前 Java 实现会把 `envPrefix` 叠加到 topic template 上，再由 `Runner.start()` 启动 distributed message queue 和 reply topic subscription。

> 这里不展开 MQ 类型、Pulsar 或 server adapter 细节；重点是：`Runner` 负责启动这些入口，具体消息系统配置由 `DistributedConfig` 和相关子包承担。

## `ResourceMgr`：运行时资源都放哪里

`ResourceMgr` 是 `Runner` 体系里最常用的基础设施之一。它统一管理以下资源：

- `Workflow`
- `Agent`
- `AgentGroup`
- `Tool`
- `Model`
- `Prompt`
- `SysOperation`

这意味着你可以把资源预先注册到全局资源池，再通过字符串 ID 运行，而不必每次都直接传实例。

### 一个重要细节：workflow 资源 ID 常常是“带版本的 key”

Java 侧很多位置会把 workflow 资源注册成：

```text
<workflowId>_<version>
```

对应工具方法是：

- `WorkflowUtils.generateWorkflowKey(...)`
- `RunnerImpl.generateWorkflowKey(...)`

例如 `WorkflowAgent.addWorkflows(...)` 在把 workflow 注册进 `ResourceMgr` 时，用的就是这个版本化 key。所以如果你用字符串方式取 workflow，最好明确使用版本化 ID。

## 执行入口一：运行 `Workflow`

### 直接传实例运行

如果你手上已经有 workflow 实例，最直接的方式是把实例交给 `Runner.runWorkflow(...)`：

```java
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.workflow.Workflow;
import java.util.Map;

Workflow workflow = buildWorkflow();
Object result = Runner.runWorkflow(
        workflow,
        Map.of("query", "我要转账"),
        null,
        null
);
```

这里如果 `session` 传 `null`，`RunnerImpl` 会自动创建 `WorkflowSessionApi`。

### 通过资源 ID 运行

如果 workflow 已经注册进 `ResourceMgr`，也可以按 ID 运行：

```java
String workflowKey = "transfer_flow_1.0";
Object result = Runner.runWorkflow(
        workflowKey,
        Map.of("query", "我要转账"),
        null,
        null
);
```

这类写法更适合：

- workflow 由启动阶段统一注册；
- 运行阶段只关心资源 ID；
- 需要和 `WorkflowAgent`、工具系统或其他注册式能力统一资源命名。

## 执行入口二：运行 `Agent`

`Runner.runAgent(...)` 负责把 agent 执行入口统一起来。Java 当前实现会：

1. 准备 agent 实例；
2. 准备 `AgentSessionApi`；
3. 调用 agent 的 `invoke(...)`；
4. 在执行后触发 `agentSession.postRun()`。

```java
import com.openjiuwen.core.runner.Runner;
import java.util.Map;

Object result = Runner.runAgent(
        agent,
        Map.of(
                "query", "帮我查一下余额",
                "conversation_id", "conversation-001"
        ),
        null,
        null
);
```

### `conversation_id` 为什么重要

对 agent 来说，`RunnerImpl` 会优先从输入里读取 `conversation_id` 作为 session ID；如果没有，就回退到 `default_session`。

这会直接影响：

- 多轮上下文是否延续；
- 交互恢复是否能回到同一条执行链；
- `WorkflowAgent` 这类应用层 agent 是否能复用之前的中断任务。

因此，只要你要做多轮或恢复，最好显式传 `conversation_id`。

## 执行入口三：运行 `AgentGroup`

`Runner` 也把 group 执行入口统一到了：

- `runAgentGroup(...)`
- `runAgentGroupStreaming(...)`

调用方式和 workflow / agent 保持一致：既可以传实例，也可以传资源 ID。这一层的价值主要在于统一运行接口，而不是改变 group 自己的调度逻辑。

## 流式执行

Java 当前把流式入口统一成以下几组方法：

- `Runner.runWorkflowStreaming(...)`
- `Runner.runAgentStreaming(...)`
- `Runner.runAgentTeamStreaming(...)`
- `Runner.runAgentGroupStreaming(...)`

它们都返回 `Iterator<?>`，常见调用形态如下：

```java
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.StreamMode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

Iterator<Object> stream = Runner.runAgentStreaming(
        agent,
        Map.of(
                "query", "我要转账",
                "conversation_id", "conversation-001"
        ),
        null,
        null,
        List.of(StreamMode.OUTPUT)
);

while (stream.hasNext()) {
    Object chunk = stream.next();
    // 这里按 OutputSchema / TraceSchema / CustomSchema 等类型继续处理
}
```

在 `examples/workflow_agent/WorkflowAgentExampleSupport.java` 里，命令行示例就是通过 `Runner.runAgentStreaming(...)` 消费 `WorkflowAgent` 的输出和交互事件。

Agent Team 使用独立的受管流式入口。首次运行可传团队 spec、`LeaderTeammateAgentTeam` 门面或
`TeamAgent`；persistent 团队在流结束后暂停，同一 session 的下一轮可按团队名称恢复：

```java
Iterator<Object> firstRound = Runner.runAgentTeamStreaming(
        teamSpec,
        Map.of("query", "创建团队并拆解任务"),
        "team-session-001");
firstRound.forEachRemaining(chunk -> {
    // 处理第一轮输出
});

Iterator<Object> secondRound = Runner.runAgentTeamStreaming(
        teamSpec.getName(),
        Map.of("query", "继续完成任务"),
        "team-session-001");
secondRound.forEachRemaining(chunk -> {
    // 处理恢复后的输出
});

Runner.destroyAgentTeam(teamSpec.getName(), true);
```

## Runner 会怎样准备 session

理解 `RunnerImpl` 的 session 准备逻辑，有助于看懂为什么“同一个 session / conversation id”这么重要。

### 对 workflow

`RunnerImpl.createWorkflowSession(...)` 的行为是：

- `session == null`：创建新的 `WorkflowSessionApi`
- `session` 是字符串：把它当 session id 创建 `WorkflowSessionApi`
- `session` 是 `AgentSessionApi`：从 agent session 派生内部 workflow session
- `session` 已经是 `WorkflowSessionApi` 或 `BaseSession`：直接复用

### 对 agent

`RunnerImpl.prepareAgentSession(...)` 会优先从输入里取 `conversation_id`，否则退回 `default_session`。这也是为什么 `WorkflowAgent` 和 group 示例都会显式传 `conversation_id`。

## 回调框架：如何观察运行过程

`Runner.callbackFramework()` 暴露的是一个功能较完整的事件框架，而不只是简单的事件总线。Java 当前实现已经支持：

- callback priority
- event / callback filter
- callback chain 与 rollback
- retry 与 timeout
- metrics
- lifecycle hooks
- circuit breaker
- history / logging

如果你要做：

- 埋点
- 运行时统计
- 某类事件统一日志
- 执行失败后的回滚 / 降级

就应该从 `CallbackFramework` 和 `runner/callback` 子包继续往下看。

## 多租户隔离入口

`Runner` 暴露了一组带 `TenantContext tenantCtx` 末参的静态重载，签名与各自的无租户版本一致，仅末尾追加 `tenantCtx`：`runWorkflow` / `runWorkflowStreaming` / `runAgent` / `runAgentStreaming` / `runAgentGroup` / `runAgentGroupStreaming` / `runAgentAsync` / `runAgentStreamingAsync`。

```java
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.multitenant.TenantContext;

TenantContext tenantCtx = TenantContext.builder().tenantId("dept-01").build();
Object result = Runner.runAgent(agent, inputs, session, context, envs, tenantCtx);
```

`RunnerImpl` 统一完成 `resolveTenantContext(session, explicitCtx)` → `bindTenantContext()` → 执行 → `unbindTenantContext()`；流式入口用 `TenantUnbindIterator` 包装器，在迭代结束或异常时才 unbind。`enableTenantIsolation=true` 时入口必须携带有效 `tenantId`，否则严格模式快速失败。

完整的多租户隔离说明（严格模式、隔离资源、目录结构、KV 前缀、安全防护、清理接口）见 [多租户数据隔离](多租户数据隔离.md)。

## 清理：`release(sessionId)` 做什么

`Runner.release(sessionId)` 会把清理动作转发给当前默认 checkpointer：

- 对内存 checkpointer，就是清掉该 session 的内存状态；
- 对持久化 checkpointer，就是按 `sessionId + ":"` 前缀删除对应状态；
- 如果你切换成了其他默认 checkpointer，`release(...)` 清理的也是那个实现里的状态。

因此，`release(...)` 更像“结束一个执行会话并回收状态”，而不是“关闭 Runner 本身”。真正的全局资源回收仍然要看 `Runner.stop()`。

## 对照示例看 Runner

### `examples/workflow_agent`

这个示例展示了最典型的 Runner 用法：

1. 创建 `WorkflowAgent`
2. 注册多个 workflow
3. 使用 `Runner.runAgentStreaming(...)` 执行
4. 收到 `__interaction__` 事件后，用同一个 `conversation_id` 继续调用
5. 退出时调用 `Runner.release(conversationId)` 与 `Runner.stop()`

### `examples/interact`

这个示例更多展示原生 `Workflow.invoke(...)` + `Checkpointer` 的恢复路径，而不是直接通过 `Runner` 运行。但它和本页并不冲突：

- `Runner` 负责“全局执行门面”；
- 原生 `Workflow` + checkpointer 更适合展示底层恢复语义。

## 当前 Java 能力边界

- `Runner` 是全局单例门面；如果你在同一进程里多次切配置，应明确控制 `setConfig(...)`、`start()`、`stop()` 的顺序。
- 默认全局 runner 使用的是 `RunnerConfig.DEFAULT`，不是 builder 字段声明里的所有默认值。
- `release(sessionId)` 只负责清掉默认 checkpointer 中该 session 的状态，不代表所有外部资源都会被释放。
- 这里不展开分布式 MQ 子系统的底层消息协议细节；重点说明 Java 当前公开的启动入口和配置边界。

## 参考入口

- [API 文档：runner 根包](../API文档/com.openjiuwen.core/runner.README.md)
- [API 文档：Runner](../API文档/com.openjiuwen.core/runner/Runner.md)
- [API 文档：RunnerImpl](../API文档/com.openjiuwen.core/runner/RunnerImpl.md)
- [API 文档：RunnerConfig](../API文档/com.openjiuwen.core/runner/RunnerConfig.md)
- [API 文档：DistributedConfig](../API文档/com.openjiuwen.core/runner/DistributedConfig.md)
- [API 文档：ResourceMgr](../API文档/com.openjiuwen.core/runner/resourcemanager/ResourceMgr.md)
- [API 文档：CallbackFramework](../API文档/com.openjiuwen.core/runner/callback/CallbackFramework.md)
- [示例：workflow_agent](../../../../examples/workflow_agent/README.md)
- [示例：interact](../../../../examples/interact/README.md)
