# Session 模块 API 文档

> 包路径：`com.openjiuwen.core.session`

Session 模块提供会话生命周期管理，包括 Agent/Workflow/Node 会话、状态持久化（提交/回滚）、实时流式输出、用户交互处理、操作追踪监控、以及基于检查点的恢复机制。

---

## 目录

- [1. 顶层接口与类](#1-顶层接口与类)
- [2. 回调（callback）](#2-回调callback)
- [3. 检查点（checkpointer）](#3-检查点checkpointer)
- [4. 配置与常量](#4-配置与常量)
- [5. 交互（interaction）](#5-交互interaction)
- [6. 内部实现（internal）](#6-内部实现internal)
- [7. 状态（state）](#7-状态state)
- [8. 流式通信（stream）](#8-流式通信stream)
- [9. 追踪（tracer）](#9-追踪tracer)
- [10. 工具类（utils）](#10-工具类utils)
- [11. 存储（store）](#11-存储store)

---

## 1. 顶层接口与类

### 1.1 Session（接口）

会话的顶层接口，定义基础会话操作。

**包路径**：`com.openjiuwen.core.session`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSessionId()` | `String` | 获取会话 ID |
| `getState(String key)` | `Object` | 根据键获取状态 |
| `updateState(Map<String, Object> state)` | `void` | 更新状态 |

### 1.2 BaseSession（抽象类）

所有会话实现的抽象基类。

**包路径**：`com.openjiuwen.core.session`  
**实现**：`Session`

**抽象方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `config()` | `Config` | 获取配置对象 |
| `state()` | `State` | 获取状态对象 |
| `tracer()` | `Object` | 获取追踪器 |
| `streamWriterManager()` | `StreamWriterManager` | 获取流写入管理器 |
| `callbackManager()` | `CallbackManager` | 获取回调管理器 |
| `sessionId()` | `String` | 获取会话 ID |
| `checkpointer()` | `Object` | 获取检查点器 |

**具体方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSessionId()` | `String` | 实现 Session 接口 |
| `getState(String key)` | `Object` | 根据键获取状态 |
| `updateState(Map<String, Object> stateMap)` | `void` | 更新状态 |
| `close()` | `void` | 关闭会话 |

### 1.3 AgentSessionApi

面向用户的 Agent 会话 API，封装 AgentSession 的操作。

**包路径**：`com.openjiuwen.core.session`

**构造方法**：
```java
AgentSessionApi(String sessionId, Map<String, Object> envs, Object card)
AgentSessionApi(String sessionId, Map<String, Object> envs)
AgentSessionApi(String sessionId)
AgentSessionApi()
```

**静态方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `create(String sessionId, Map<String, Object> envs, Object card)` | `AgentSessionApi` | 工厂方法 |

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSessionId()` | `String` | 获取会话 ID |
| `getEnv(String key)` | `Object` | 获取环境变量 |
| `getEnv(String key, Object defaultValue)` | `Object` | 获取环境变量（带默认值） |
| `getEnvs()` | `Map<String, Object>` | 获取全部环境变量 |
| `getAgentId()` | `String` | 获取 Agent ID |
| `getAgentName()` | `String` | 获取 Agent 名称 |
| `getAgentDescription()` | `String` | 获取 Agent 描述 |
| `updateState(Map<String, Object> data)` | `void` | 更新状态 |
| `getState(Object key)` | `Object` | 获取状态 |
| `dumpState()` | `Map<String, Object>` | 导出全部状态 |
| `writeStream(Object data)` | `void` | 写入输出流 |
| `writeCustomStream(Map<String, Object> data)` | `void` | 写入自定义流 |
| `streamOutput(Consumer<Object> consumer)` | `void` | 消费流式输出 |
| `streamIterator()` | `Iterator<Object>` | 获取流迭代器（已废弃） |
| `preRun(Map<String, Object> inputs)` | `void` | 执行前初始化 |
| `postRun()` | `void` | 执行后清理 |
| `createWorkflowSession()` | `WorkflowSessionApi` | 创建工作流子会话 |
| `interact(Object value)` | `void` | 触发用户交互 |
| `getInner()` | `AgentSession` | 获取内部会话实例 |

### 1.4 NodeSessionApi

面向用户的节点会话 API，封装 NodeSession 的操作。

**包路径**：`com.openjiuwen.core.session`

**构造方法**：
```java
NodeSessionApi(NodeSession session, boolean streamMode)
NodeSessionApi(NodeSession session)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSessionId()` | `String` | 获取会话 ID |
| `getWorkflowId()` | `String` | 获取工作流 ID |
| `getComponentId()` | `String` | 获取组件 ID |
| `getComponentType()` | `String` | 获取组件类型 |
| `getComponentDescrip()` | `String` | 获取组件描述 |
| `getExecutableId()` | `String` | 获取可执行组件 ID |
| `updateState(Map<String, Object> data)` | `void` | 更新节点状态 |
| `getState(Object key)` | `Object` | 获取节点状态 |
| `updateGlobalState(Map<String, Object> data)` | `void` | 更新全局状态 |
| `getGlobalState(Object key)` | `Object` | 获取全局状态 |
| `dumpState()` | `Map<String, Object>` | 导出全部状态 |
| `writeStream(Object data)` | `void` | 写入输出流 |
| `writeCustomStream(Map<String, Object> data)` | `void` | 写入自定义流 |
| `trace(Map<String, Object> data)` | `void` | 追踪数据 |
| `traceError(Exception error)` | `void` | 追踪错误 |
| `interact(Object value)` | `Map<String, Object>` | 触发用户交互 |
| `getCallbackManager()` | `Object` | 获取回调管理器 |
| `getEnv(String key)` | `Object` | 获取环境变量 |
| `getInner()` | `NodeSession` | 获取内部会话实例 |

### 1.5 WorkflowSessionApi

面向用户的工作流会话 API。

**包路径**：`com.openjiuwen.core.session`

**构造方法**：
```java
WorkflowSessionApi(BaseSession parent, String sessionId, Map<String, Object> envs)
WorkflowSessionApi(BaseSession parent, String sessionId)
WorkflowSessionApi(String sessionId)
```

**静态方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `create(BaseSession parent, String sessionId, Map<String, Object> envs)` | `WorkflowSessionApi` | 工厂方法 |

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSessionId()` | `String` | 获取会话 ID |
| `getEnvs()` | `Map<String, Object>` | 获取环境变量 |
| `getCallbackManager()` | `CallbackManager` | 获取回调管理器 |
| `getParent()` | `BaseSession` | 获取父会话 |
| `setWorkflowCard(Object card)` | `void` | 设置工作流卡片 |
| `getWorkflowCard()` | `Object` | 获取工作流卡片 |

### 1.6 ProxySession

代理会话，将所有操作委托给内部 stub 实例。

**包路径**：`com.openjiuwen.core.session`  
**继承**：`BaseSession`

**构造方法**：
```java
ProxySession()
ProxySession(BaseSession stub)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `setSession(BaseSession stub)` | `void` | 设置委托 stub |
| `getStub()` | `BaseSession` | 获取委托 stub |
| `config()` | `Config` | 委托给 stub |
| `state()` | `State` | 委托给 stub |
| `tracer()` | `Object` | 委托给 stub |
| `streamWriterManager()` | `StreamWriterManager` | 委托给 stub |
| `callbackManager()` | `CallbackManager` | 委托给 stub |
| `sessionId()` | `String` | 委托给 stub |
| `checkpointer()` | `Object` | 委托给 stub |

### 1.7 AgentGroupSessionApi

面向多智能体分组场景的用户侧 Session API，内部包装 `AgentSession`，用于 `multiagent` 模块共享状态与流输出。

**包路径**：`com.openjiuwen.core.session`

**构造方法**：
```java
AgentGroupSessionApi(String sessionId, Map<String, Object> envs)
AgentGroupSessionApi(String sessionId)
AgentGroupSessionApi()
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSessionId()` | `String` | 获取会话 ID |
| `getEnv(String key, Object defaultValue)` | `Object` | 读取环境变量 |
| `getInner()` | `AgentSession` | 获取内部 `AgentSession` 实例 |
| `create(String sessionId, Map<String, Object> envs)` | `AgentGroupSessionApi` | 创建分组 Session（静态工厂） |

---

## 2. 回调（callback）

### 2.1 @TriggerEvent（注解）

标记方法为回调触发事件方法。

**包路径**：`com.openjiuwen.core.session.callback`

**用法**：
```java
@TriggerEvent
public void onSomeEvent(Map<String, Object> kwargs) { ... }
```

### 2.2 BaseHandler

回调处理器基类。

**包路径**：`com.openjiuwen.core.session.callback`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `eventName()` | `String` | 获取处理器事件名称 |
| `getTriggerEvents()` | `Map<String, Method>` | 获取所有 @TriggerEvent 标注方法 |

### 2.3 CallbackManager

回调管理器，基于反射调用处理器方法。

**包路径**：`com.openjiuwen.core.session.callback`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `register(BaseHandler handler)` | `void` | 注册处理器 |
| `trigger(String handlerClassName, String eventName, Map<String, Object> kwargs)` | `void` | 触发事件 |
| `getHandler(String handlerClassName)` | `BaseHandler` | 获取指定处理器 |

---

## 3. 检查点（checkpointer）

### 3.1 Checkpointer（抽象类）

检查点管理器抽象基类，负责会话状态的持久化与恢复。

**包路径**：`com.openjiuwen.core.session.checkpointer`

**常量**：

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `SESSION_NAMESPACE_AGENT` | `"agent"` | Agent 命名空间 |
| `SESSION_NAMESPACE_WORKFLOW` | `"workflow"` | 工作流命名空间 |
| `WORKFLOW_NAMESPACE_GRAPH` | `"workflow-graph"` | 工作流图命名空间 |

**抽象方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `preWorkflowExecute(BaseSession session, InteractiveInput inputs)` | `void` | 工作流执行前处理 |
| `postWorkflowExecute(BaseSession session, Object result, Exception exception)` | `void` | 工作流执行后处理 |
| `preAgentExecute(BaseSession session, Object inputs)` | `void` | Agent 执行前处理 |
| `interruptAgentExecute(BaseSession session)` | `void` | Agent 执行中断处理 |
| `postAgentExecute(BaseSession session)` | `void` | Agent 执行后处理 |
| `sessionExists(String sessionId)` | `boolean` | 检查会话是否存在 |
| `release(String sessionId)` | `void` | 释放会话资源 |
| `graphStore()` | `Store` | 获取图状态存储 |

**静态工具方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getThreadId(BaseSession session)` | `String` | 从会话获取线程 ID |
| `getWorkflowId(BaseSession session)` | `String` | 从会话获取工作流 ID（受保护） |
| `buildKey(String... parts)` | `String` | 用分隔符拼接键 |
| `buildKeyWithNamespace(String sessionId, String namespace, String entityId, String... suffixes)` | `String` | 构建带命名空间的完整键 |

### 3.2 CheckpointerFactory

检查点器工厂，支持注册和获取实现。

**包路径**：`com.openjiuwen.core.session.checkpointer`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `register(String name, CheckpointerProvider provider)` | `void` | 注册提供者（静态） |
| `getCheckpointer(Map<String, Object> config)` | `Checkpointer` | 通过配置获取实例（静态） |
| `setDefaultCheckpointer(CheckpointerProvider provider)` | `void` | 设置默认提供者（静态） |

### 3.3 CheckpointerProvider（函数式接口）

检查点器创建函数式接口。

**包路径**：`com.openjiuwen.core.session.checkpointer`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `create(Map<String, Object> config)` | `Checkpointer` | 根据配置创建实例 |

### 3.4 InMemoryCheckpointer

基于内存的检查点器完整实现。

**包路径**：`com.openjiuwen.core.session.checkpointer`  
**继承**：`Checkpointer`

实现所有 Checkpointer 抽象方法，使用内存存储保存会话状态。

### 3.5 Storage（抽象类）

检查点数据存储抽象基类。

**包路径**：`com.openjiuwen.core.session.checkpointer`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `save(String key, Object data)` | `void` | 保存数据（抽象） |
| `recover(String key)` | `Object` | 恢复数据（抽象） |
| `exists(String key)` | `boolean` | 检查键是否存在（抽象） |

---

## 4. 配置与常量

### 4.1 Config

会话配置管理器，管理环境变量、工作流配置和 Agent 配置。

**包路径**：`com.openjiuwen.core.session.config`

从环境变量和默认值加载配置，支持 Agent 和 Workflow 两种模式。

**内部类**：

#### MetadataLike

元数据容器。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 标识符 |
| `name` | `String` | 名称 |
| `event` | `String` | 事件名 |

### 4.2 SessionConstants

会话常量定义，包含 30+ 超时键、循环设置和环境键。

**包路径**：`com.openjiuwen.core.session.constants`

**主要常量分类**：

**超时相关**：
| 常量名 | 说明 |
|--------|------|
| `TIMEOUT_LLM_KEY` | LLM 调用超时 |
| `TIMEOUT_TOOL_KEY` | 工具调用超时 |
| `TIMEOUT_STREAM_GENERATOR_KEY` | 流式生成超时 |
| `TIMEOUT_STREAM_FIRST_FRAME_KEY` | 流首帧超时 |
| `TIMEOUT_STREAM_INTER_FRAME_KEY` | 流帧间超时 |

**循环相关**：
| 常量名 | 说明 |
|--------|------|
| `LOOP_MAX_ITERATIONS_KEY` | 循环最大迭代次数 |
| `LOOP_INDEX_KEY` | 循环索引键 |
| `LOOP_CURRENT_VALUE_KEY` | 循环当前值键 |

**环境变量**：
| 常量名 | 说明 |
|--------|------|
| `ENV_SESSION_ID` | 会话 ID 环境变量 |
| `ENV_WORKFLOW_ID` | 工作流 ID 环境变量 |
| `ENV_AGENT_ID` | Agent ID 环境变量 |

---

## 5. 交互（interaction）

### 5.1 InteractiveInput

交互输入容器。

**包路径**：`com.openjiuwen.core.session.interaction`

| 字段/方法 | 类型 | 说明 |
|-----------|------|------|
| `userInputs` | `Map<String, Object>` | 用户输入映射 |
| `rawInputs` | `Object` | 原始输入 |
| `update(String nodeId, Object value)` | `void` | 添加指定节点的输入 |

### 5.2 InteractionOutput

交互输出。

**包路径**：`com.openjiuwen.core.session.interaction`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 交互 ID |
| `value` | `Object` | 交互输出值 |

### 5.3 AgentInterrupt

Agent 交互中断异常。

**包路径**：`com.openjiuwen.core.session.interaction`  
**继承**：`Exception`

当 Agent 需要用户输入时抛出。

### 5.4 BaseInteraction

交互处理基类。

**包路径**：`com.openjiuwen.core.session.interaction`

| 字段/方法 | 类型 | 说明 |
|-----------|------|------|
| `interactiveInputs` | `Queue<InteractiveInput>` | 交互输入队列 |
| `idx` | `int` | 当前索引 |

### 5.5 AgentInteraction

Agent 交互处理器，等待用户输入或通过检查点抛出 AgentInterrupt。

**包路径**：`com.openjiuwen.core.session.interaction`  
**继承**：`BaseInteraction`

**构造方法**：
```java
AgentInteraction(BaseSession session)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `waitUserInputs(Object value)` | `Object` | 等待用户输入 |

### 5.6 SimpleAgentInteraction

简化的 Agent 交互处理器，通过检查点触发中断。

**包路径**：`com.openjiuwen.core.session.interaction`

### 5.7 WorkflowInteraction

工作流交互处理器，等待用户输入或抛出 GraphInterrupt 包装器。

**包路径**：`com.openjiuwen.core.session.interaction`

---

## 6. 内部实现（internal）

### 6.1 WrappedSession（抽象类）

会话包装器基类，封装 executableId、sessionId、状态操作和流操作。

**包路径**：`com.openjiuwen.core.session.internal`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `executableId()` | `String` | 获取可执行组件 ID |
| `sessionId()` | `String` | 获取会话 ID |

### 6.2 StateSession

状态会话，委托给内部 state() 进行状态操作。

**包路径**：`com.openjiuwen.core.session.internal`  
**继承**：`WrappedSession`

### 6.3 RouterSession

路由器会话，大多数操作为空操作，用于路由组件。

**包路径**：`com.openjiuwen.core.session.internal`

### 6.4 AgentSession

Agent 会话实现，管理 Agent 级别的状态、流和追踪。

**包路径**：`com.openjiuwen.core.session.internal`

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | `String` | 会话 ID |
| `config` | `Config` | 配置 |
| `state` | `AgentStateCollection` | Agent 状态集合 |
| `streamWriterManager` | `StreamWriterManager` | 流写入管理器 |
| `tracer` | `Tracer` | 追踪器 |
| `checkpointer` | `Checkpointer` | 检查点器 |
| `card` | `Object` | Agent 卡片 |

### 6.5 NodeSession

节点会话实现，管理图中单个节点的会话状态。

**包路径**：`com.openjiuwen.core.session.internal`

| 字段 | 类型 | 说明 |
|------|------|------|
| `nodeId` | `String` | 节点 ID |
| `nodeType` | `String` | 节点类型 |
| `executableId` | `String` | 可执行组件 ID |
| `parentId` | `String` | 父节点 ID |
| `stateField` | `String` | 状态字段 |
| `workflowId` | `String` | 工作流 ID |

### 6.6 SubWorkflowSession

子工作流会话，嵌套工作流中使用。

**包路径**：`com.openjiuwen.core.session.internal`  
**继承**：`NodeSession`

| 字段 | 类型 | 说明 |
|------|------|------|
| `subWorkflowId` | `String` | 子工作流 ID |

嵌套深度自动递增。

### 6.7 WorkflowSession

工作流会话实现，管理工作流级别的状态和流。

**包路径**：`com.openjiuwen.core.session.internal`

| 字段 | 类型 | 说明 |
|------|------|------|
| `workflowId` | `String` | 工作流 ID |
| `parent` | `BaseSession` | 父会话 |
| `state` | 状态对象 | 工作流状态分区 |
| `actorManager` | `ActorManager` | 流 Actor 管理器 |

---

## 7. 状态（state）

### 7.1 ReadableState（接口）

只读状态接口。

**包路径**：`com.openjiuwen.core.session.state`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `get(Object key)` | `Object` | 根据键获取值 |
| `getByPrefix(String prefix)` | `Map<String, Object>` | 根据前缀获取值 |

### 7.2 RecoverableState（接口）

可恢复状态接口。

**包路径**：`com.openjiuwen.core.session.state`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getState()` | `Map<String, Object>` | 获取完整状态 |
| `setState(Map<String, Object> state)` | `void` | 设置完整状态 |

### 7.3 StateLike（接口）

可读写状态接口。

**包路径**：`com.openjiuwen.core.session.state`  
**继承**：`ReadableState, RecoverableState`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `update(Map<String, Object> data)` | `void` | 更新状态 |
| `getByTransformer(Function<Object, Object> transformer)` | `Object` | 使用转换函数获取值 |

### 7.4 CommitStateLike（接口）

支持提交/回滚的状态接口。

**包路径**：`com.openjiuwen.core.session.state`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `updateById(String nodeId, Map<String, Object> data)` | `void` | 按节点 ID 更新 |
| `commit(String nodeId)` | `void` | 提交节点更新 |
| `rollback()` | `void` | 回滚所有未提交更新 |
| `getUpdates()` | `Map<String, Map<String, Object>>` | 获取待提交更新 |

### 7.5 State（接口）

顶层状态接口，定义 Agent/Workflow 的多分区状态。

**包路径**：`com.openjiuwen.core.session.state`  
**继承**：`RecoverableState`

**常量**：

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `GLOBAL_STATE_KEY` | `"global_state"` | 全局状态键 |
| `IO_STATE_KEY` | `"io_state"` | IO 状态键 |
| `IO_STATE_UPDATES_KEY` | `"io_state_updates"` | IO 状态更新键 |
| `GLOBAL_STATE_UPDATES_KEY` | `"global_state_updates"` | 全局状态更新键 |
| `COMP_STATE_KEY` | `"comp_state"` | 组件状态键 |
| `COMP_STATE_UPDATES_KEY` | `"comp_state_updates"` | 组件状态更新键 |
| `WORKFLOW_STATE_KEY` | `"workflow_state"` | 工作流状态键 |
| `WORKFLOW_STATE_UPDATES_KEY` | `"workflow_state_updates"` | 工作流状态更新键 |
| `AGENT_STATE_KEY` | `"agent_state"` | Agent 状态键 |
| `TRACE_STATE_KEY` | `"trace_state"` | 追踪状态键 |
| `DEFAULT_NODE_ID` | `"default"` | 默认节点 ID |
| `DEFAULT_WORKFLOW_ID` | `"workflow"` | 默认工作流 ID |

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getGlobal(Object key)` | `Object` | 获取全局状态值 |
| `updateGlobal(Map<String, Object> data)` | `void` | 更新全局状态 |
| `updateTrace(Object span)` | `void` | 更新追踪状态 |
| `update(Map<String, Object> data)` | `void` | 更新状态 |
| `get(Object key)` | `Object` | 获取状态值 |
| `dump()` | `Map<String, Object>` | 导出全部状态 |

### 7.6 InMemoryStateLike

基于内存 Map 的 StateLike 实现（深拷贝语义）。

**包路径**：`com.openjiuwen.core.session.state`  
**实现**：`StateLike`

### 7.7 InMemoryCommitState

支持待提交更新追踪和提交/回滚的内存实现。

**包路径**：`com.openjiuwen.core.session.state`  
**实现**：`CommitStateLike`

### 7.8 AgentStateCollection

Agent 状态集合，包含全局状态与 Agent 状态分区。

**包路径**：`com.openjiuwen.core.session.state`  
**实现**：`State`

| 字段 | 类型 | 说明 |
|------|------|------|
| `globalState` | `StateLike` | 全局状态 |
| `agentState` | `StateLike` | Agent 状态分区 |

### 7.9 WorkflowStateCollection

工作流状态集合，包含 IO、全局、组件、工作流四个分区。

**包路径**：`com.openjiuwen.core.session.state`  
**实现**：`State`

| 字段 | 类型 | 说明 |
|------|------|------|
| `ioState` | IO 状态 | 输入输出状态 |
| `globalState` | 全局状态 | 全局共享状态 |
| `compState` | 组件状态 | 组件级别状态 |
| `workflowState` | 工作流状态 | 工作流级别状态 |

### 7.10 WorkflowCommitState

工作流提交状态，扩展 WorkflowStateCollection 支持提交/回滚语义。

**包路径**：`com.openjiuwen.core.session.state`  
**继承**：`WorkflowStateCollection`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `commit()` | `void` | 提交所有更新 |
| `commitCmp(String nodeId)` | `void` | 提交指定组件 |
| `rollback()` | `void` | 回滚所有未提交更新 |
| `createNodeState(String nodeId)` | `StateLike` | 为节点创建状态视图 |

### 7.11 InMemoryState

内存状态工厂。

**包路径**：`com.openjiuwen.core.session.state`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `create()` | `State` | 创建空状态（静态） |
| `fromMap(Map<String, Object> data)` | `State` | 从 Map 创建状态（静态） |

---

## 8. 流式通信（stream）

### 8.1 StreamSchema（接口）

流数据 Schema 标记接口。

**包路径**：`com.openjiuwen.core.session.stream`

### 8.2 OutputSchema

输出流 Schema。

**包路径**：`com.openjiuwen.core.session.stream`  
**实现**：`StreamSchema`

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `String` | 输出类型 |
| `index` | `int` | 输出索引 |
| `payload` | `Object` | 负载数据 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `fromMap(Map<String, Object> map)` | `OutputSchema` | 从 Map 构建（静态） |

### 8.3 CustomSchema

自定义流 Schema，灵活属性映射。

**包路径**：`com.openjiuwen.core.session.stream`  
**实现**：`StreamSchema`

属性以 `Map<String, Object>` 存储。

### 8.4 TraceSchema

追踪流 Schema。

**包路径**：`com.openjiuwen.core.session.stream`  
**实现**：`StreamSchema`

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `String` | 追踪类型 |
| `payload` | `Object` | 追踪负载 |

### 8.5 StreamMode（枚举）

流式输出模式枚举。

**包路径**：`com.openjiuwen.core.session.stream`

| 枚举值 | 说明 |
|--------|------|
| `OUTPUT` | 标准输出流 |
| `TRACE` | 追踪流 |
| `CUSTOM` | 自定义流 |

### 8.6 AsyncStreamQueue

异步流队列，支持重试逻辑。

**包路径**：`com.openjiuwen.core.session.stream`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `send(Object data)` | `void` | 发送数据到队列 |
| `receive()` | `Object` | 从队列接收数据 |
| `close()` | `void` | 关闭队列 |

### 8.7 StreamEmitter

流发射器，发送数据帧并在结束时发送 END_FRAME 哨兵标记。

**包路径**：`com.openjiuwen.core.session.stream`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `emit(Object data)` | `void` | 发射数据帧 |
| `close()` | `void` | 关闭（发送 END_FRAME） |

### 8.8 StreamWriter\<S\>

泛型流写入器，带数据验证功能。

**包路径**：`com.openjiuwen.core.session.stream`  
**类型参数**：`<S extends StreamSchema>` — Schema 类型

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `write(S data)` | `void` | 验证并写入数据 |

### 8.9 StreamWriterManager

流写入管理器，统一管理 OUTPUT/TRACE/CUSTOM 三种流。

**包路径**：`com.openjiuwen.core.session.stream`

**构造方法**：
```java
StreamWriterManager(StreamEmitter streamEmitter, List<StreamMode> modes)
StreamWriterManager(StreamEmitter streamEmitter)
```

**静态方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createManager(StreamEmitter emitter, List<StreamMode> modes)` | `StreamWriterManager` | 创建管理器（静态） |
| `createManager(StreamEmitter emitter)` | `StreamWriterManager` | 创建默认管理器（静态） |

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getStreamEmitter()` | `StreamEmitter` | 获取流发射器 |
| `streamOutput(long firstFrameTimeoutMs, long timeoutMs, boolean needClose, Consumer<Object> consumer)` | `void` | 消费流输出（完整参数） |
| `streamOutput(Consumer<Object> consumer)` | `void` | 消费流输出（简化） |
| `streamIterator()` | `Iterator<Object>` | 获取流迭代器 |
| `streamIterator(long firstFrameTimeoutMs, long timeoutMs, boolean needClose)` | `Iterator<Object>` | 获取流迭代器（完整参数） |
| `collectStreamOutput()` | `List<Object>` | 收集全部流输出 |
| `addWriter(StreamMode key, StreamWriter<?> writer)` | `void` | 添加写入器 |
| `getWriter(StreamMode key)` | `StreamWriter<?>` | 获取写入器 |
| `getOutputWriter()` | `StreamWriter<OutputSchema>` | 获取输出流写入器 |
| `getTraceWriter()` | `StreamWriter<TraceSchema>` | 获取追踪流写入器 |
| `getCustomWriter()` | `StreamWriter<CustomSchema>` | 获取自定义流写入器 |
| `removeWriter(StreamMode key)` | `StreamWriter<?>` | 移除写入器 |

---

## 9. 追踪（tracer）

### 9.1 InvokeType（枚举）

调用类型枚举。

**包路径**：`com.openjiuwen.core.session.tracer`

| 枚举值 | 说明 |
|--------|------|
| `PROMPT` | 提示词调用 |
| `LLM` | 大模型调用 |
| `PLUGIN` | 插件调用 |
| `WORKFLOW` | 工作流调用 |
| `CHAIN` | 链式调用 |
| `RETRIEVER` | 检索调用 |
| `EVALUATOR` | 评估调用 |

### 9.2 NodeStatus（枚举）

节点状态枚举。

| 枚举值 | 说明 |
|--------|------|
| `START` | 开始 |
| `FINISH` | 完成 |
| `RUNNING` | 运行中 |
| `ERROR` | 错误 |

### 9.3 TracerHandlerName（枚举）

追踪处理器名称枚举。

| 枚举值 | 说明 |
|--------|------|
| `TRACE_AGENT` | Agent 追踪处理器 |
| `TRACER_WORKFLOW` | 工作流追踪处理器 |

### 9.4 Span

追踪跨度基类。

**包路径**：`com.openjiuwen.core.session.tracer`

| 字段 | 类型 | 说明 |
|------|------|------|
| `traceId` | `String` | 追踪 ID |
| `invokeId` | `String` | 调用 ID |
| `parentInvokeId` | `String` | 父调用 ID |
| `startTime` | `long` | 开始时间 |
| `endTime` | `long` | 结束时间 |
| `inputs` | `Object` | 输入数据 |
| `outputs` | `Object` | 输出数据 |
| `error` | `Object` | 错误信息 |
| `childInvokesId` | `List<String>` | 子调用 ID 列表 |
| `status` | `NodeStatus` | 节点状态 |
| `onInvokeData` | `Map<String, Object>` | 调用数据 |

### 9.5 TraceAgentSpan

Agent 追踪跨度。

**包路径**：`com.openjiuwen.core.session.tracer`  
**继承**：`Span`

| 字段 | 类型 | 说明 |
|------|------|------|
| `invokeType` | `InvokeType` | 调用类型 |
| `name` | `String` | 名称 |
| `elapsedTime` | `long` | 耗时（毫秒） |
| `metaData` | `Map<String, Object>` | 元数据 |

### 9.6 TraceWorkflowSpan

工作流追踪跨度。

**包路径**：`com.openjiuwen.core.session.tracer`  
**继承**：`Span`

| 字段 | 类型 | 说明 |
|------|------|------|
| `executionId` | `String` | 执行 ID |
| `sourceIds` | `List<String>` | 源节点 ID 列表 |
| `workflowId` | `String` | 工作流 ID |
| `componentId` | `String` | 组件 ID |
| `loopNodeId` | `String` | 循环节点 ID |
| `loopIndex` | `int` | 循环索引 |
| `streamInputs` | `Object` | 流输入 |
| `streamOutputs` | `Object` | 流输出 |
| `llmInvokeData` | `Object` | LLM 调用数据 |

### 9.7 SpanManager

跨度管理器，管理追踪跨度栈。

**包路径**：`com.openjiuwen.core.session.tracer`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSpan(String invokeId)` | `Span` | 获取指定跨度 |
| `popSpan(String invokeId)` | `Span` | 弹出指定跨度 |
| `createAgentSpan(...)` | `TraceAgentSpan` | 创建 Agent 跨度 |
| `createWorkflowSpan(...)` | `TraceWorkflowSpan` | 创建工作流跨度 |
| `updateSpan(String invokeId, Map<String, Object> data)` | `void` | 更新跨度数据 |

### 9.8 TraceBaseHandler（抽象类）

追踪处理器基类。

**包路径**：`com.openjiuwen.core.session.tracer`  
**继承**：`BaseHandler`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `formatData(Map<String, Object> kwargs)` | `Object` | 格式化数据（抽象） |
| `sendData(Object data)` | `void` | 发送追踪数据（抽象） |
| `getElapsedTime()` | `long` | 获取耗时 |
| `getNodeStatus()` | `NodeStatus` | 获取节点状态 |

### 9.9 TraceAgentHandler

Agent 追踪处理器，带 @TriggerEvent 事件方法。

**包路径**：`com.openjiuwen.core.session.tracer`  
**继承**：`TraceBaseHandler`

**触发事件方法**：

| 方法 | 说明 |
|------|------|
| `onChainStart / onChainEnd / onChainError` | 链式调用开始/结束/错误 |
| `onLlmStart / onLlmEnd / onLlmError` | LLM 调用开始/结束/错误 |
| `onPromptStart / onPromptEnd / onPromptError` | 提示词调用开始/结束/错误 |
| `onPluginStart / onPluginEnd / onPluginError` | 插件调用开始/结束/错误 |
| `onRetrieverStart / onRetrieverEnd / onRetrieverError` | 检索调用开始/结束/错误 |
| `onEvaluatorStart / onEvaluatorEnd / onEvaluatorError` | 评估调用开始/结束/错误 |
| `onWorkflowStart / onWorkflowEnd / onWorkflowError` | 工作流调用开始/结束/错误 |

### 9.10 TraceWorkflowHandler

工作流追踪处理器，带 @TriggerEvent 事件方法。

**包路径**：`com.openjiuwen.core.session.tracer`  
**继承**：`TraceBaseHandler`

**触发事件方法**：

| 方法 | 说明 |
|------|------|
| `onCallStart` | 调用开始 |
| `onPreInvoke` | 调用前 |
| `onPreStream` | 流式前 |
| `onInvoke` | 调用中 |
| `onPostStream` | 流式后 |
| `onPostInvoke` | 调用后 |
| `onCallDone` | 调用完成 |

### 9.11 Tracer

追踪器，协调 Agent 和工作流的追踪。

**包路径**：`com.openjiuwen.core.session.tracer`

**构造方法**：`Tracer()`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `init(StreamWriterManager swm, CallbackManager cbm)` | `void` | 初始化追踪器 |
| `registerWorkflowSpanManager(String parentNodeId)` | `void` | 注册工作流跨度管理器 |
| `getWorkflowSpan(String invokeId, String parentNodeId)` | `TraceWorkflowSpan` | 获取工作流跨度 |
| `trigger(String handlerClassName, String eventName, Map<String, Object> kwargs)` | `void` | 触发追踪事件 |
| `popWorkflowSpan(String invokeId, String parentNodeId)` | `void` | 弹出工作流跨度 |
| `getTraceId()` | `String` | 获取追踪 ID |
| `getTracerAgentSpanManager()` | `SpanManager` | 获取 Agent 跨度管理器 |
| `getTracerWorkflowSpanManagerDict()` | `Map<String, SpanManager>` | 获取工作流跨度管理器字典 |

### 9.12 TracerWorkflowUtils

工作流追踪工具类（静态方法）。

**包路径**：`com.openjiuwen.core.session.tracer`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `traceWorkflowStart(...)` | `void` | 追踪工作流开始 |
| `traceComponentBegin(...)` | `void` | 追踪组件开始 |
| `traceComponentInputs(...)` | `void` | 追踪组件输入 |
| `traceComponentOutputs(...)` | `void` | 追踪组件输出 |
| `trace(...)` | `void` | 通用追踪 |
| `traceError(...)` | `void` | 追踪错误 |

---

## 10. 工具类（utils）

### SessionUtils

会话工具类，提供嵌套数据结构操作。

**包路径**：`com.openjiuwen.core.session.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `isRefPath(String value)` | `boolean` | 判断是否为 `${...}` 引用格式（静态） |
| `extractOriginKey(String refPath)` | `String` | 提取引用中的原始键（静态） |
| `splitNestedPath(String path)` | `List<Object>` | 将 `a.b[1].c` 解析为路径组件（静态） |
| `getValueByNestedPath(Object data, String path)` | `Object` | 按嵌套路径获取值（静态） |
| `rootToPath(Object root, List<Object> path)` | `Object` | 导航到路径最终位置（静态） |
| `updateDict(Map<String, Object> target, Map<String, Object> source)` | `void` | 合并 Map（支持嵌套，静态） |
| `expandNestedStructure(Object obj)` | `Object` | 递归展开嵌套对象（静态） |
| `getBySchema(Object data, Object schema)` | `Object` | 根据 Schema 获取数据（静态） |

---

## 11. 存储（store）

### 11.1 Store（抽象类）

会话数据键值存储抽象基类。

**包路径**：`com.openjiuwen.core.session.store`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `read(String key)` | `Object` | 读取数据（抽象） |
| `write(Map<String, Object> data)` | `void` | 写入数据（抽象） |

### 11.2 MemoryStore

基于内存 HashMap 的 Store 实现。

**包路径**：`com.openjiuwen.core.session.store`  
**继承**：`Store`

### 11.3 FileStore

基于文件的 Store（占位，尚未完整实现）。

**包路径**：`com.openjiuwen.core.session.store`  
**继承**：`Store`
