# Runner 模块单元测试报告

## 1. 本次工作概述

本次工作以 Python 版 Runner 实现为基线，对 Java 版 Runner 模块进行了实现核对、缺漏修复、单元测试补充和回归验证。

对照范围：

- Python 实现
  - `agent-core-python/openjiuwen/core/runner/runner.py`
  - `agent-core-python/openjiuwen/core/runner/runner_config.py`
- Java 实现
  - `src/main/java/com/openjiuwen/core/runner/Runner.java`
  - `src/main/java/com/openjiuwen/core/runner/RunnerImpl.java`
  - `src/main/java/com/openjiuwen/core/runner/RunnerConfig.java`
  - `src/main/java/com/openjiuwen/core/session/AgentSessionApi.java`
  - `src/main/java/com/openjiuwen/core/workflow/Workflow.java`

本次重点结论：

- Java 版此前的 `RunnerTest` 主要覆盖 getter 和生命周期，缺少对 `runWorkflow`、`runAgent`、`runAgentStreaming`、`runAgentGroup` 等实际执行路径的验证。
- Java 版 `RunnerImpl` 在 session 适配、agent session 生命周期、流式执行后的状态落盘、反射调用兼容性方面存在与 Python 版不一致的问题。
- 经过修复后，Runner 包相关测试共 **170** 项，全部通过。

测试环境：

| 项目 | 说明 |
|---|---|
| JDK | Java 21 |
| 构建工具 | Maven 3.x |
| 测试框架 | JUnit 5.10.2 |
| 回归范围 | Runner、callback、mq、resourcemanager |
| 回归结果 | **170 tests, 0 failures, 0 errors, 0 skipped** |

---

## 2. 本次发现并修复的问题

### 2.1 `runWorkflow` 缺少 Python 版的 session 适配语义

#### 问题

Python 版 `run_workflow` 支持以下几类 `session` 输入：

- `None`：自动创建 workflow session
- `str`：按指定 session id 创建 workflow session
- `AgentSession`：从 agent session 派生 workflow session
- `WorkflowSession`：直接复用

Java 版此前的 `RunnerImpl.createWorkflowSession()` 只是简单透传：

- `session == null` 时返回 `null`
- `session` 为字符串时直接返回字符串

这会导致：

- `runWorkflow(..., session=null, ...)` 无法自动创建 session
- `runWorkflow(..., session="id", ...)` 无法生成合法的 workflow session
- `runWorkflow(..., session=AgentSessionApi, ...)` 无法继承 agent 上下文

#### 修复

- 在 `RunnerImpl.createWorkflowSession()` 中补齐 Python 对应语义：
  - `null` -> 自动创建 `WorkflowSessionApi`
  - `String` -> 按 session id 创建 `WorkflowSessionApi`
  - `AgentSessionApi` -> 派生内部 `WorkflowSession`
  - `WorkflowSessionApi` / `BaseSession` -> 直接接受

---

### 2.2 `runAgent` / `runAgentStreaming` 缺少 agent session 生命周期

#### 问题

Python 版 `run_agent` / `run_agent_streaming` 在执行前后会做：

- 根据 `conversation_id` / 显式 session / 默认 session id 创建 agent session
- 调用 `pre_run`
- 调用 agent 的 `invoke` / `stream`
- 在正常结束后调用 `post_run`

Java 版此前直接通过反射调用 agent 方法，没有：

- session id 解析
- `conversation_id` 语义
- `default_session` 语义
- `preRun()` / `postRun()`
- checkpointer 恢复与保存

这会导致：

- agent 状态无法跨轮恢复
- `conversation_id` 形同无效
- 流式调用在消费完成后也不会持久化状态

#### 修复

- 在 `RunnerImpl` 中新增 agent session 准备逻辑：
  - `inputs["conversation_id"]` 优先
  - 否则使用显式字符串 session id
  - 再否则回退到 `default_session`
- 在 `runAgent()` 中补齐 `preRun()` / `postRun()`
- 在 `runAgentStreaming()` 中为返回迭代器增加包装器，在迭代消费完成后触发 `postRun()`

---

### 2.3 反射调用过于严格，只支持精确 `Object` 签名

#### 问题

Java 版此前使用：

```java
getMethod("invoke", Object.class, Object.class)
getMethod("stream", Object.class)
```

这要求被调对象的方法参数类型必须“精确等于” `Object`。

但实际 Java 用户代码更常见的是：

- `invoke(Map<String, Object> inputs, AgentSessionApi session)`
- `stream(Map<String, Object> inputs, AgentSessionApi session)`
- `invoke(Map<String, Object> inputs, String sessionId)`

这些签名在此前实现下都会找不到方法。

#### 修复

- 在 `RunnerImpl` 中实现兼容反射匹配：
  - 按方法名 + 参数个数匹配
  - 按 `isAssignableFrom` 计算兼容性
  - 优先选择更具体的参数类型
- 同时让 agent/group 调用按以下顺序尝试：
  - `(inputs, session, context)`
  - `(inputs, session)`
  - `(inputs)`

---

### 2.4 agent 派生 workflow session 时，共享 global state 被覆盖

#### 问题

Java 版 `AgentSession.createWorkflowSession()` 已经能创建共享 global state 的内部 `WorkflowSession`。

但 `Workflow.createWorkflowSession()` 在收到该内部 session 后，又重新创建了一个新的 `WorkflowSession`，把原本共享的 state 丢掉了。

直接表现为：

- agent session 中写入的 global state
- 在 `RunnerImpl.runWorkflow(..., agentSession, ...)` 里无法被 workflow 读取

#### 修复

- 在 `Workflow.createWorkflowSession()` 中识别已存在的内部 `WorkflowSession`
- 复用其 state，只补充 stream writer / actor manager / tracer 等运行期设施

---

### 2.5 `AgentSessionApi.preRun()` 输入类型过窄

#### 问题

Python 版 `run_agent` 的 `inputs` 是 `Any`。
Java 版 `AgentSessionApi.preRun()` 之前只接受 `Map<String, Object>`，这与 Runner 的 `Object inputs` 设计不一致。

#### 修复

- 将 `AgentSessionApi.preRun()` 调整为接受 `Object inputs`
- 保持 checkpointer 语义不变

---

## 3. 对照 Python 版后的实现差异说明

### 3.1 本次已补齐的 Runner 行为

| 能力 | Python | Java 修复后 |
|---|---|---|
| `run_workflow(session=None)` | 支持 | 支持 |
| `run_workflow(session="id")` | 支持 | 支持 |
| `run_workflow(session=AgentSession)` | 支持 | 支持，且保留共享 global state |
| `run_agent(conversation_id=...)` | 支持 | 支持 |
| `run_agent` 状态恢复/落盘 | 支持 | 支持 |
| `run_agent_streaming` 消费完成后持久化 | 支持 | 支持 |
| 兼容具体 Java 参数签名 | 动态语言天然支持 | 已补齐兼容反射匹配 |

### 3.2 未纳入本次修复的差异

以下差异来自仓库当前迁移范围，而不是本次 Runner 局部实现缺陷：

| 项目 | Python | Java 当前仓库状态 | 说明 |
|---|---|---|---|
| `dist_pubsub` / 分布式 MQ | 已实现 | 仓库内未迁移对应 MessageQueueFactory / ReplyTopicSubscription / RemoteAgent 体系 | 本次不在 Runner 局部可修复范围内 |
| 异步协程接口 | `async` / `await` | 同步 + `Iterator` | 语言/运行时差异 |

---

## 4. 本次补充的单元测试

### 4.1 RunnerTest 从 8 项扩充到 21 项

新增验证点包括：

- `runWorkflow` 在 `session=null` 时自动创建 workflow session
- `runWorkflow` 接受字符串 session id
- `runWorkflow` 接受 `AgentSessionApi` 并继承 global state
- `runWorkflow` 通过资源管理器按 id 加载 workflow
- `runWorkflowStreaming` 正确透传 session id
- `runAgent` 的 `conversation_id` 语义
- `runAgent` 的显式 session id 回退
- `runAgent` 的 `default_session` 回退
- `runAgent` 的 checkpointer 持久化恢复
- `runAgentStreaming` 在流消费完成后持久化状态
- `runAgent` 支持资源管理器按 id 加载 agent
- `runAgentGroup` / `runAgentGroupStreaming` 支持具体参数签名的反射调用
- `RunnerConfig` topic template 前缀行为
- `generateWorkflowKey()` 语义

### 4.2 Runner 包整体测试统计

| 测试类 | 测试数 |
|---|---:|
| `ChainActionTest` | 2 |
| `FilterActionTest` | 2 |
| `HookTypeTest` | 2 |
| `CallbackModelsTest` | 22 |
| `CallbackChainTest` | 14 |
| `CallbackFiltersTest` | 25 |
| `CallbackFrameworkTest` | 34 |
| `MessageQueueInMemoryTest` | 11 |
| `TagMgrTest` | 20 |
| `ResourceMgrTest` | 17 |
| `RunnerTest` | 21 |
| **合计** | **170** |

---

## 5. 本次修改的文件

| 文件 | 修改类型 | 说明 |
|---|---|---|
| `src/main/java/com/openjiuwen/core/runner/RunnerImpl.java` | 行为修复 | 补齐 workflow/agent session 适配、agent 生命周期、兼容反射调用、streaming 后置持久化 |
| `src/main/java/com/openjiuwen/core/workflow/Workflow.java` | 行为修复 | 修复 agent 派生 workflow session 时共享 global state 被覆盖的问题 |
| `src/main/java/com/openjiuwen/core/session/AgentSessionApi.java` | 接口修复 | `preRun()` 入参放宽为 `Object` |
| `src/test/java/com/openjiuwen/core/runner/RunnerTest.java` | 单测补充 | 将 Runner 行为测试从 8 项扩充到 21 项 |
| `docs/Runner_UT.md` | 文档更新 | 记录本次核对、修复、补测与回归结果 |

---

## 6. 执行的测试命令与结果

### 6.1 定向 RunnerTest 回归

```bash
mvn -Dtest=RunnerTest test
```

结果：

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 6.2 Runner 包整体回归

```bash
mvn "-Dtest=RunnerTest,CallbackChainTest,CallbackFiltersTest,CallbackFrameworkTest,CallbackModelsTest,ChainActionTest,FilterActionTest,HookTypeTest,MessageQueueInMemoryTest,ResourceMgrTest,TagMgrTest" test
```

结果：

```text
Tests run: 170, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 7. 本次工作结论

本次对照 Python 版 Runner 后，Java 版已补齐以下关键执行语义：

- workflow session 自动创建与 session 适配
- agent session 的创建、恢复、保存与默认会话语义
- 流式 agent 执行在消费完成后的状态持久化
- 与 Java 具体参数签名兼容的反射调用
- agent -> workflow 共享 global state 的保留

当前 Runner 包回归测试 **170 项全部通过**，本次修复未引入 callback、mq、resource manager 相关回归。
