# Checkpointer检查点机制

`Checkpointer` 是 Java 版 openJiuwen 在执行期保存、恢复和清理 session 状态的核心机制。它把“这次执行走到哪了”“哪些节点还在等输入”“恢复时要把状态放回哪里”这些问题统一起来，因此它和 [人机交互](人机交互.md)、`Runner.release(...)`、`WorkflowAgent` 恢复能力天然相关。

如果把运行时主线简化成一句话，就是：

> 执行时在关键节点保存状态，下一次继续时按 session / workflow 维度恢复，执行结束或显式释放后再清理。

## 核心类型

| 类型 | 作用 | 当前 Java 落点 |
| --- | --- | --- |
| `Checkpointer` | 抽象基类，定义 workflow / agent 的 pre / post / interrupt / release 生命周期 | `com.openjiuwen.core.session.checkpointer` |
| `CheckpointerConfig` | 配置对象，包含 `type` 和 `conf` | 用于工厂创建 |
| `CheckpointerFactory` | 工厂与注册表，负责创建并返回默认 checkpointer | `in_memory`、`persistence` 已内置 |
| `InMemoryCheckpointer` | 基于内存 `Map` 与 `InMemoryStore` 的实现 | 开发 / 测试最常用 |
| `PersistenceCheckpointer` | 基于 `BaseKVStore` 的持久化实现 | 单机持久化或统一 KV 存储 |

> 补充说明
>
> 当前仓库还注册了 `redis` 与 `redis_checkpointer_cluster` 两个扩展类型，但它们位于 `extensions/checkpointer/redis`，不作为本页主线展开。

## 三个命名空间先记住

`Checkpointer` 把状态按命名空间组织：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `SESSION_NAMESPACE_AGENT` | `agent` | session 下的 agent 状态 |
| `SESSION_NAMESPACE_WORKFLOW` | `workflow` | session 下的 workflow 状态 |
| `WORKFLOW_NAMESPACE_GRAPH` | `workflow-graph` | workflow 下的 graph checkpoint 状态 |

构造 key 的通用格式是：

```text
sessionId:namespace:entityId:suffix
```

例如：

- `conversation-001:agent:workflow_agent_java_example:agent_state_blobs`
- `conversation-001:workflow:transfer_flow_multi_1.0:workflow_state_blobs`
- `conversation-001:workflow-graph:transfer_flow_multi_1.0:checkpoint_data_value`

这也是为什么 workflow 状态和 graph 状态要分开理解：

- workflow state 关注 `session.state()` 里的业务 / 交互状态；
- graph state 关注图执行器的 checkpoint 数据。

## 生命周期总览

## 1. Workflow 生命周期

workflow 相关的生命周期由这两个入口组成：

- `preWorkflowExecute(BaseSession session, InteractiveInput inputs)`
- `postWorkflowExecute(BaseSession session, Object result, Exception exception)`

### `preWorkflowExecute(...)`

workflow 执行前会分成两条路：

#### 情况 A：这是一次恢复执行

如果本次传入的是 `InteractiveInput`，checkpointer 会尝试：

1. 按 `sessionId + workflowId` 找到已保存状态；
2. 恢复 workflow state；
3. 把本次交互输入重新塞回节点 / workflow state；
4. 恢复 commit updates。

#### 情况 B：这是一次新执行

如果本次不是 `InteractiveInput`，但旧状态还存在：

- 默认会抛出“workflow state exists”一类错误；
- 只有 session env 中显式开启 `FORCE_DEL_WORKFLOW_STATE_KEY=true` 时，才会强制删除旧状态后重跑。

这也是为什么“同一 session 想重新开始一条 workflow”必须先清状态，或者换一个新 session。

### `postWorkflowExecute(...)`

workflow 执行后又分三种常见结果：

- **异常**：保存 workflow checkpoint，供后续恢复；
- **中断**：保存 workflow checkpoint，等待交互恢复；
- **正常完成**：清理 workflow checkpoint 与 graph checkpoint。

因此，对 workflow 来说，checkpointer 更像是“**中断 / 异常保护机制**”，正常完成后状态通常会自动清掉。

## 2. Agent 生命周期

agent 相关生命周期由这三个入口组成：

- `preAgentExecute(BaseSession session, Object inputs)`
- `interruptAgentExecute(BaseSession session)`
- `postAgentExecute(BaseSession session)`

### `preAgentExecute(...)`

agent 执行前会：

1. 恢复已有 agent state；
2. 如果本次传入了输入，就把它注入 `Constant.INTERACTIVE_INPUT`。

### `interruptAgentExecute(...)`

当 agent 进入等待用户输入的中断态时，会保存当前 agent state。

### `postAgentExecute(...)`

一个很重要的 Java 侧事实是：`postAgentExecute(...)` 当前会**继续保存 agent state**，而不是自动清掉它。

这意味着：

- agent session 的上下文可以在多轮对话里延续；
- 如果你想彻底结束这段会话，需要显式调用 `Runner.release(sessionId)` 或 `checkpointer.release(sessionId)`。

所以 agent checkpointer 的定位更偏“持续会话状态”，workflow checkpointer 则更偏“图执行恢复状态”。

## `InMemoryCheckpointer`：开发阶段最常见

`InMemoryCheckpointer` 使用：

- 内存 `Map`
- `InMemoryStore`
- 按 `sessionId` 维护 agent store / workflow store

它的特点是：

- 配置最简单；
- 恢复速度快；
- 进程退出后状态全部丢失；
- 非常适合本地开发、示例和单元测试。

`examples/interact` 默认就是围绕这种“同进程内恢复”的思路来展示交互与 retry 语义的。

## `PersistenceCheckpointer`：持久化工作流 / agent 状态

`PersistenceCheckpointer` 的核心思路是把状态委托给 `BaseKVStore`：

- agent state 走 `PersistenceAgentStorage`
- workflow state 走 `PersistenceWorkflowStorage`
- graph checkpoint 走 `PersistenceGraphStore`

### 它会保存哪些内容

#### Agent

- `agent_state_blobs_dump_type`
- `agent_state_blobs`

#### Workflow

- `workflow_state_blobs_dump_type`
- `workflow_state_blobs`
- `workflow_update_blobs_dump_type`
- `workflow_update_blobs`

#### Graph

- `checkpoint_data_type`
- `checkpoint_data_value`

Java 当前实现会把 dump type 写成：

```text
java_serialized
```

这说明当前持久化主线是 Java 序列化对象恢复，而不是统一交换格式。

### 删除与释放

`PersistenceCheckpointer.release(sessionId)` 会按：

```text
sessionId + ":"
```

这个前缀做整体删除。因此从语义上看，`release(...)` 代表“清掉该 session 的所有 agent / workflow / graph checkpoint”。

## 如何通过 `Runner` 配置 checkpointer

最推荐的入口不是在业务代码里到处手动 new checkpointer，而是在 `Runner` 启动前统一配置：

```java
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import java.util.Map;

RunnerConfig config = RunnerConfig.builder()
        .distributedMode(false)
        .checkpointerConfig(Map.of(
                "type", "persistence",
                "conf", Map.of(
                        // 这里放 BaseKVStore 对应配置
                )
        ))
        .build();

Runner.setConfig(config);
Runner.start();
```

`Runner.start()` 会做的事情是：

1. 读取 `RunnerConfig.checkpointerConfig`
2. 调 `CheckpointerFactory.create(type, conf)` 创建实例
3. 把这个实例设置成默认 checkpointer

之后：

- `Runner.release(sessionId)`
- `AgentInteraction`
- `Workflow` / `Agent` 执行期 session

都会默认使用它。

## 也可以直接通过工厂创建

如果你要在更底层装配，也可以直接使用 `CheckpointerFactory`：

```java
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import java.util.Map;

Checkpointer checkpointer = CheckpointerFactory.create(
        "in_memory",
        Map.of()
);
CheckpointerFactory.setDefaultCheckpointer(checkpointer);
```

### 当前内置注册类型

`CheckpointerFactory` 当前已注册：

- `in_memory`
- `persistence`
- `redis`
- `redis_checkpointer_cluster`

如果没有显式配置默认实例，`CheckpointerFactory.getCheckpointer()` 会回退到单例 `InMemoryCheckpointer`。

## 把 checkpointer 放回交互恢复主线里看

### 在 `examples/interact` 里

天气示例完整展示了这条路径：

1. `workflow.invoke(...)` 执行
2. workflow 进入 `INPUT_REQUIRED`，保存 workflow / graph 状态
3. 用户通过 `InteractiveInput` 回答城市和单位
4. 用同一 `sessionId` 恢复
5. 天气节点首次失败，再通过 `new InteractiveInput("retry")` 用同一 `sessionId` 恢复
6. 完成后显式 `checkpointer.release(sessionId)`

### 在 `examples/workflow_agent` 里

`WorkflowAgent` 示例展示的是 agent 级状态回收：

- 每轮都复用同一个 `conversation_id`
- 结束时调用 `Runner.release(conversationId)`

这恰好对应 agent checkpointer 不会在完成后自动清空的事实。

## 什么时候该删除，什么时候该保留

可以把策略简单理解为：

| 场景 | 建议 |
| --- | --- |
| workflow 因补问中断，稍后还要继续 | 保留 checkpoint |
| workflow 异常后需要 retry | 保留 checkpoint |
| workflow 正常完成 | 让 `postWorkflowExecute(...)` 自动清理 |
| agent 多轮对话还要继续 | 保留 agent state |
| 整个会话彻底结束 | 调 `Runner.release(sessionId)` 或 `checkpointer.release(sessionId)` |

## 当前 Java 能力边界

- agent 完成后当前不会自动删除 agent checkpoint；如果会话结束，记得显式 `release(...)`。
- workflow 新执行遇到旧 checkpoint 时，默认会报错；只有显式开启 `FORCE_DEL_WORKFLOW_STATE_KEY` 才会强制删旧状态。
- graph checkpoint 和 workflow state 是分开存储、分开清理的，不能只理解成“一份 workflow 序列化”。
- Redis 扩展当前有源码和测试，但本页不把它写成已完整文档化的主线能力。

## 参考入口

- [API 文档：session.checkpointer 根包](../API文档/com.openjiuwen.core/session/checkpointer.README.md)
- [API 文档：Checkpointer](../API文档/com.openjiuwen.core/session/checkpointer/Checkpointer.md)
- [API 文档：CheckpointerFactory](../API文档/com.openjiuwen.core/session/checkpointer/CheckpointerFactory.md)
- [API 文档：InMemoryCheckpointer](../API文档/com.openjiuwen.core/session/checkpointer/InMemoryCheckpointer.md)
- [API 文档：PersistenceCheckpointer](../API文档/com.openjiuwen.core/session/checkpointer/PersistenceCheckpointer.md)
- [示例：interact](../../../../examples/interact/README.md)
- [示例：workflow_agent](../../../../examples/workflow_agent/README.md)
