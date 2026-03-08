# Graph 模块 API 文档

> 包路径：`com.openjiuwen.core.graph`

Graph 模块提供基于 Pregel 计算模型的有向图执行引擎，支持图的构建、编译、执行、状态持久化、流式通信以及可视化。

---

## 目录

- [1. 核心类](#1-核心类)
- [2. Pregel 引擎](#2-pregel-引擎)
- [3. 状态存储（store）](#3-状态存储store)
- [4. 流式通信（stream_actor）](#4-流式通信stream_actor)
- [5. 可视化（visualization）](#5-可视化visualization)

---

## 1. 核心类

### 1.1 Executable\<I, O\>

可执行组件的泛型抽象基类，提供 invoke/stream/collect/transform 四种执行模式。

**包路径**：`com.openjiuwen.core.graph`  
**类型参数**：`<I>` 输入类型，`<O>` 输出类型

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `onInvoke(I inputs, BaseSession session, Object... kwargs)` | `O` | 同步调用 |
| `onStream(I inputs, BaseSession session, Object... kwargs)` | `Iterator<O>` | 流式输出 |
| `onCollect(I inputs, BaseSession session, Object... kwargs)` | `O` | 从流式输入收集结果 |
| `onTransform(I inputs, BaseSession session, Object... kwargs)` | `Iterator<O>` | 流式输入转换为流式输出 |
| `skipTrace()` | `boolean` | 是否跳过追踪（默认 false） |
| `graphInvoker()` | `boolean` | 是否为图调用者（默认 false） |
| `postCommit()` | `boolean` | 是否执行后提交（默认 true） |
| `componentType()` | `String` | 返回组件类型标识 |

### 1.2 ExecutableGraph\<I, O\>

可执行图的抽象基类，封装 invoke/stream/collect/transform 并从输入 Map 中提取配置。

**包路径**：`com.openjiuwen.core.graph`  
**继承**：`Executable<I, O>`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(I inputs, BaseSession session)` | `O` | 执行图（提取 `INPUTS_KEY` 和 `CONFIG_KEY`） |
| `stream(I inputs, BaseSession session)` | `Iterator<O>` | 流式执行 |
| `collect(Iterator<I> inputs, BaseSession session)` | `O` | 收集流式输入 |
| `transform(Iterator<I> inputs, BaseSession session)` | `Iterator<O>` | 转换流式输入 |
| `interrupt(Map<String, Object> message)` | `void` | 处理中断 |
| `doInvoke(I inputs, BaseSession session, Object config)` | `O` | 内部实现（抽象） |

### 1.3 Graph

图定义的抽象类，管理节点/边以及编译。

**包路径**：`com.openjiuwen.core.graph`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `startNode(String nodeId)` | `Graph` | 设置起始节点 |
| `endNode(String nodeId)` | `Graph` | 设置结束节点 |
| `addNode(String nodeId, Executable<?, ?> node, boolean waitForAll)` | `Graph` | 添加节点（含屏障选项，抽象） |
| `addNode(String nodeId, Executable<?, ?> node)` | `Graph` | 添加节点（无屏障） |
| `addEdge(Object sourceNodeId, String targetNodeId)` | `Graph` | 添加边（source 可为 String 或 List<String>，抽象） |
| `addConditionalEdges(String sourceNodeId, Object router)` | `Graph` | 添加条件边（抽象） |
| `compile(BaseSession session)` | `ExecutableGraph<?, ?>` | 编译为可执行形式（抽象） |
| `getNodes()` | `Map<String, Executable<?, ?>>` | 获取所有节点（抽象） |

### 1.4 PregelGraph

基于 Pregel 的图构建器实现。

**包路径**：`com.openjiuwen.core.graph`  
**继承**：`Graph`

**构造方法**：
```java
PregelGraph()
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `startNode(String nodeId)` | `Graph` | 设置起始节点 |
| `endNode(String nodeId)` | `Graph` | 设置结束节点 |
| `addNode(String nodeId, Executable<?, ?> node, boolean waitForAll)` | `Graph` | 添加节点 |
| `getNodes()` | `Map<String, Executable<?, ?>>` | 获取所有节点 |
| `getVertex(String nodeId)` | `Vertex` | 获取内部节点包装 |
| `addEdge(Object sourceNodeId, String targetNodeId)` | `Graph` | 添加边（支持 N→1、1→N、1→1） |
| `addConditionalEdges(String sourceNodeId, Object router)` | `Graph` | 添加条件边 |
| `compile(BaseSession session)` | `ExecutableGraph<?, ?>` | 编译为 CompiledGraph |
| `reset()` | `void` | 重置所有节点以便重用 |

### 1.5 CompiledGraph

编译后的图，封装 Pregel 引擎和 Checkpointer。

**包路径**：`com.openjiuwen.core.graph`  
**继承**：`ExecutableGraph<Object, Map<String, Object>>`

**构造方法**：
```java
CompiledGraph(Pregel pregel, Checkpointer checkpointer)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `doInvoke(Object inputs, BaseSession session, Object config)` | `Map<String, Object>` | 执行 Pregel 计算（含检查点） |
| `stream(Object inputs, BaseSession session)` | `Iterator<Map<String, Object>>` | 流式执行 |
| `interrupt(Map<String, Object> message)` | `void` | 处理中断 |

### 1.6 AtomicNode

原子节点抽象基类，验证会话状态、调用内部逻辑并提交组件状态。

**包路径**：`com.openjiuwen.core.graph`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `atomicInvoke(Map<String, Object> kwargs)` | `Object` | 执行原子节点操作 |
| `doAtomicInvoke(Map<String, Object> kwargs)` | `Object` | 内部实现（抽象受保护） |

### 1.7 Vertex

图节点的执行包装器，管理初始化、执行生命周期、流协调和追踪。

**包路径**：`com.openjiuwen.core.graph`  
**继承**：`AtomicNode`  
**实现**：`StreamConsumer`

**构造方法**：
```java
Vertex(String nodeId, Executable<?, ?> executable)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `init(BaseSession session, Map<String, Object> kwargs)` | `boolean` | 初始化节点 |
| `call(GraphNodeState state, Object config)` | `Map<String, Object>` | Pregel 调用的主入口 |
| `streamCall(CountDownLatch latch, Consumer<Exception> errorCallback)` | `void` | 处理流式输入调用 |
| `isDone()` | `boolean` | 节点是否已完成执行 |
| `shouldHandleMessage()` | `boolean` | 节点是否具有流能力 |
| `reset()` | `void` | 重置以便重用 |
| `getNodeId()` | `String` | 获取节点 ID |
| `getExecutable()` | `Executable<Object, Object>` | 获取底层可执行组件 |
| `getSession()` | `NodeSession` | 获取节点会话 |
| `isEndNode()` / `setEndNode(boolean)` | `boolean` / `void` | 结束节点标记 |

### 1.8 Router

图路由器函数式接口，确定条件边目标。

**包路径**：`com.openjiuwen.core.graph`  
**继承**：`Function<Object, Object>`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `apply(Object input)` | `Object` | 返回单个目标节点 ID 或 List |

### 1.9 GraphNodeState

图级别状态，包含已访问的源节点 ID 列表。

**包路径**：`com.openjiuwen.core.graph`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSourceNodeId()` | `List<String>` | 获取源节点 ID 列表 |
| `setSourceNodeId(List<String>)` | `void` | 设置源节点 ID 列表 |
| `merge(GraphNodeState other)` | `void` | 合并另一个状态（拼接列表） |

---

## 2. Pregel 引擎

### 2.1 Pregel

Pregel 图执行引擎，实现 BSP（Bulk Synchronous Parallel）模型。

**包路径**：`com.openjiuwen.core.graph.pregel`

**构造方法**：
```java
Pregel(Map<String, PregelNode> nodes, List<Channel> channels, Store store, Consumer<PregelLoop> afterStep)
Pregel(Map<String, PregelNode> nodes, List<Channel> channels, String initial, Store store, Consumer<PregelLoop> afterStep)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `run(PregelConfig config)` | `Map<String, Object>` | 执行 Pregel 计算 |
| `getNodes()` | `Map<String, PregelNode>` | 获取所有节点 |
| `getChannels()` | `List<Channel>` | 获取所有通道 |
| `getInitial()` | `String` | 获取初始节点 |
| `getStore()` | `Store` | 获取状态存储 |

### 2.2 PregelBuilder

Pregel 图构建器。

**包路径**：`com.openjiuwen.core.graph.pregel`

**构造方法**：
```java
PregelBuilder()  // 自动初始化 START 和 END 节点
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addNode(String name, Object fn, List<IRouter> routers)` | `PregelBuilder` | 添加节点 |
| `addNode(String name, Object fn)` | `PregelBuilder` | 添加无路由器节点 |
| `addEdge(Object start, Object end)` | `PregelBuilder` | 添加边（支持 N→1、1→N、1→1） |
| `addBranch(String src, Function<Object, Object> selector)` | `PregelBuilder` | 添加条件分支 |
| `build(Store store, Consumer<PregelLoop> afterStepCallback)` | `Pregel` | 构建引擎 |
| `build()` | `Pregel` | 构建引擎（无存储和回调） |

### 2.3 PregelLoop

Pregel 执行循环，实现 BSP 超级步。

**包路径**：`com.openjiuwen.core.graph.pregel`

**构造方法**：
```java
PregelLoop(Pregel graph, PregelConfig config)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `init()` | `void` | 初始化循环（若有则恢复状态） |
| `runStep()` | `boolean` | 执行一个超级步；返回 true 表示还有更多步骤 |
| `getStep()` | `int` | 获取当前步数 |
| `getConfig()` | `PregelConfig` | 获取配置 |
| `getActiveNodes()` | `List<String>` | 获取活跃节点列表 |

### 2.4 PregelConfig

Pregel 图执行配置。

**包路径**：`com.openjiuwen.core.graph.pregel`

| 字段/方法 | 类型 | 说明 |
|-----------|------|------|
| `sessionId` | `String` | 会话 ID |
| `recursionLimit` | `int` | 递归限制 |
| `ns` | `String` | 命名空间 |
| `parentNs` | `String` | 父命名空间 |
| `DEFAULT` | `PregelConfig` | 默认配置（静态常量） |
| `get(String key)` | `Object` | 按键名获取配置值 |
| `toMap()` | `Map<String, Object>` | 转换为 Map |
| `createInnerConfig(PregelConfig config)` | `PregelConfig` | 创建内部配置副本（静态） |

### 2.5 PregelConstants

Pregel 常量定义。

**包路径**：`com.openjiuwen.core.graph.pregel`

| 常量 | 值 | 说明 |
|------|----|------|
| `START` | `"__start__"` | 虚拟起始节点 |
| `END` | `"__end__"` | 虚拟结束节点 |
| `MAX_RECURSIVE_LIMIT` | `10000` | 默认递归限制 |
| `TASK_STATUS_INTERRUPT` | `"__interrupt__"` | 中断状态 |
| `TASK_STATUS_ERROR` | `"__error__"` | 错误状态 |
| `NS_SEPARATOR` | `":"` | 命名空间分隔符 |

### 2.6 PregelNode

Pregel 执行图中的节点。

**包路径**：`com.openjiuwen.core.graph.pregel`

**构造方法**：
```java
PregelNode(String name, Object func, List<IRouter> routers)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getName()` | `String` | 获取节点名称 |
| `getFunc()` | `Object` | 获取节点函数 |
| `getRouters()` | `List<IRouter>` | 获取路由器列表 |

### 2.7 通道系统

#### Channel（抽象类）

Pregel 节点间消息传递通道的抽象基类。

**包路径**：`com.openjiuwen.core.graph.pregel`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getKey()` | `String` | 获取路由键（默认为名称） |
| `getNodeName()` | `String` | 获取节点名称 |
| `isReady()` | `boolean` | 是否有足够消息（抽象） |
| `accept(Message msg)` | `boolean` | 接受传入消息（抽象） |
| `consume()` | `void` | 重置通道（抽象） |
| `snapshot()` | `Object` | 创建状态快照（抽象） |
| `restore(Object snapshotData)` | `void` | 从快照恢复（抽象） |

#### TriggerChannel

触发通道，接收到任意消息即就绪。

**构造方法**：`TriggerChannel(String name)`

#### BarrierChannel

屏障通道，N→1 扇入同步。

**构造方法**：`BarrierChannel(String nodeName, Set<String> expected)`

#### ChannelManager

通道管理器，管理所有通道和消息路由。

**构造方法**：`ChannelManager(List<Channel> channels)`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `bufferMessage(Message msg)` | `void` | 将消息加入缓冲区 |
| `isEmpty()` | `boolean` | 缓冲区是否为空 |
| `flush()` | `void` | 将缓冲消息刷入通道 |
| `getReadyNodes()` | `List<String>` | 获取就绪节点 |
| `consume(String nodeName)` | `void` | 消费指定节点的就绪通道 |
| `snapshot()` | `Map<String, Object>` | 创建完整通道状态快照 |
| `restore(Map<String, Object> snapshotMap)` | `void` | 从快照恢复 |

### 2.8 消息系统

#### Message

Pregel 节点间传递的基础消息。

**构造方法**：
```java
Message(String sender, String target)
Message(String sender, String target, Object payload)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSender()` | `String` | 获取发送者 |
| `getTarget()` | `String` | 获取目标 |
| `getPayload()` | `Object` | 获取负载 |

#### TriggerMessage

触发消息，在下一超级步激活目标节点。继承 `Message`。

#### BarrierMessage

屏障消息，用于 N→1 扇入同步。继承 `Message`。

### 2.9 路由器系统

#### IRouter（接口）

路由器接口，在节点执行后分发消息。

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `dispatch(String sourceNode)` | `List<Message>` | 从源节点分发消息 |

#### StaticRouter

静态路由器，向固定目标发送触发消息（1→N）。

**构造方法**：`StaticRouter(List<String> targets)`

#### ConditionalRouter

条件路由器，通过选择器函数动态确定目标。

**构造方法**：`ConditionalRouter(Function<Object, Object> selector)`

#### BarrierRouter

屏障路由器，发送屏障消息进行 N→1 扇入同步。

**构造方法**：`BarrierRouter(List<String> targets)`

### 2.10 TaskExecutorPool

Pregel 节点任务并发执行池，使用虚拟线程。

**包路径**：`com.openjiuwen.core.graph.pregel`

**构造方法**：
```java
TaskExecutorPool(PregelConfig config)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `submit(PregelNode node, int version)` | `void` | 提交节点执行任务 |
| `waitAll()` | `void` | 等待所有任务完成（FIRST_EXCEPTION 语义） |
| `cancelAll()` | `void` | 取消所有运行中的任务 |
| `clear()` | `void` | 清空结果集合 |
| `getSucceedMessages()` | `List<Message>` | 获取成功消息列表 |
| `getFailed()` | `Map<String, PendingNode>` | 获取失败节点映射 |

### 2.11 GraphInterrupt

图执行中断异常。

**包路径**：`com.openjiuwen.core.graph.pregel`  
**继承**：`Exception`

**构造方法**：
```java
GraphInterrupt()
GraphInterrupt(Interrupt value)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getValue()` | `Interrupt` | 获取中断值 |

---

## 3. 状态存储（store）

### 3.1 Store（接口）

图状态持久化的抽象接口。

**包路径**：`com.openjiuwen.core.graph.store`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `get(String sessionId, String ns)` | `Optional<GraphStoreState>` | 获取存储状态 |
| `save(String sessionId, String ns, GraphStoreState state)` | `void` | 保存状态 |
| `delete(String sessionId, String ns)` | `void` | 删除状态（ns=null 删除全部） |

### 3.2 InMemoryStore

内存实现的图状态存储。

**包路径**：`com.openjiuwen.core.graph.store`  
**实现**：`Store`

**构造方法**：`InMemoryStore()`

### 3.3 GraphStore

Store 的装饰器，添加日志记录。

**包路径**：`com.openjiuwen.core.graph.store`  
**实现**：`Store`

**构造方法**：`GraphStore(Store delegate)`

### 3.4 GraphStoreState

Pregel 图执行的持久化状态，用于恢复/续接。

**包路径**：`com.openjiuwen.core.graph.store`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getNs()` | `String` | 获取命名空间 |
| `getStep()` | `int` | 获取步数 |
| `getChannelValues()` | `Map<String, Object>` | 获取通道值 |
| `getPendingBuffer()` | `List<Message>` | 获取待处理缓冲区 |
| `getPendingNode()` | `Map<String, PendingNode>` | 获取待处理节点 |
| `getNodeVersion()` | `Map<String, Integer>` | 获取节点版本 |
| `create(...)` | `GraphStoreState` | 工厂方法（静态） |

### 3.5 PendingNode

挂起（失败或中断）的节点。

**包路径**：`com.openjiuwen.core.graph.store`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getNodeName()` | `String` | 获取节点名称 |
| `getStatus()` | `String` | 获取状态 |
| `getExceptions()` | `List<Exception>` | 获取异常列表 |

### 3.6 Serializer

图状态持久化的抽象序列化器。

**包路径**：`com.openjiuwen.core.graph.store`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `dumpsTyped(Object obj)` | `TypedBytes` | 序列化为类型化字节（抽象） |
| `loadsTyped(TypedBytes data)` | `Object` | 从类型化字节反序列化（抽象） |
| `create(String typeName)` | `Serializer` | 工厂方法（静态） |

**内部类**：
- `record TypedBytes(String type, byte[] data)` — 类型化字节
- `JsonSerializer` — 基于 Jackson 的 JSON 序列化器

---

## 4. 流式通信（stream_actor）

### 4.1 StreamConsumer（接口）

可消费流式数据的图节点接口。

**包路径**：`com.openjiuwen.core.graph.stream_actor`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `streamCall(CountDownLatch latch, Consumer<Exception> errorCallback)` | `void` | 处理流式输入调用 |
| `shouldHandleMessage()` | `boolean` | 是否具有流能力 |
| `isDone()` | `boolean` | 执行周期是否完成 |

### 4.2 ActorManager

图中节点间流式通信的管理器。

**包路径**：`com.openjiuwen.core.graph.stream_actor`

**构造方法**：
```java
ActorManager(Map<String, List<String>> streamEdges, StreamGraph graph, boolean subGraph,
             BaseSession session, Function<String, List<ComponentAbility>> compAbilitiesProvider)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `subWorkflowStream()` | `BlockingQueue<Object>` | 获取子工作流流队列 |
| `getStreamTransform()` | `StreamTransform` | 获取流转换器 |
| `produce(String producerId, Object messageContent, ComponentAbility ability, boolean firstFrame)` | `void` | 生产流消息 |
| `endMessage(String producerId, ComponentAbility ability)` | `void` | 发送结束消息 |
| `consume(String consumerId, ComponentAbility ability, Object schema, Consumer<Object> streamCallback)` | `Map<String, Object>` | 消费流数据 |
| `shutdown()` | `void` | 关闭所有 Actor |

### 4.3 StreamActor

单个节点的流生命周期管理器。

**包路径**：`com.openjiuwen.core.graph.stream_actor`

**构造方法**：
```java
StreamActor(String nodeId, StreamConsumer vertex, List<ComponentAbility> abilities,
            List<String> sources, long streamGeneratorTimeoutSeconds)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `send(Object message, ComponentAbility sourceAbility, boolean firstFrame, String producerId)` | `void` | 发送流消息 |
| `generator(ComponentAbility ability, Map<String, Object> schema, Consumer<Object> streamCallback)` | `Map<String, Object>` | 获取生成器 Map |
| `shutdown()` | `void` | 关闭所有任务 |

### 4.4 StreamProcessor

单个节点的流消息处理器。

**包路径**：`com.openjiuwen.core.graph.stream_actor`

**常量**：`END_SENTINEL` — 流结束标记

**构造方法**：
```java
StreamProcessor(String nodeId, List<String> sources, long streamGeneratorTimeoutSeconds)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `run(ComponentAbility ability)` | `void` | 主处理循环（在虚拟线程上运行） |
| `receive(StreamPayload payload)` | `void` | 接收流消息 |
| `generator(Map<String, Object> schema, Consumer<Object> streamCallback)` | `Map<String, Object>` | 创建生成器 Map |

### 4.5 StreamGraph

图的流消费者管理器。

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addStreamConsumer(StreamConsumer consumer, String nodeId)` | `void` | 注册消费者 |
| `getNode(String nodeId)` | `StreamConsumer` | 获取节点的消费者 |

### 4.6 StreamPayload

节点间流消息的负载。

**构造方法**：`StreamPayload(Object message, ComponentAbility sourceAbility)`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getMessage()` | `Object` | 获取消息内容 |
| `getSourceAbility()` | `ComponentAbility` | 获取源能力 |

### 4.7 StreamTransform

流数据转换工具。

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getByDefinedTransformer(Object originMessage, Object transformer)` | `Object` | 使用自定义转换器转换 |
| `getByDefaultTransformer(Object originMessage, Object streamInputsSchema)` | `Object` | 使用 Schema 转换 |

---

## 5. 可视化（visualization）

### 5.1 Drawable

可绘制图表示的构建器，用于工作流可视化。

**包路径**：`com.openjiuwen.core.graph.visualization`

**构造方法**：`Drawable()`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addNode(String nodeId, ComponentComposable component)` | `void` | 将组件转换为 DrawableNode 添加 |
| `addSimpleNode(String nodeId)` | `void` | 添加简单节点 |
| `setStartNode(String nodeId)` | `void` | 标记为起始节点 |
| `setEndNode(String nodeId)` | `void` | 标记为结束节点 |
| `setBreakNode(String nodeId)` | `void` | 标记为中断节点 |
| `addEdge(String source, String target, boolean conditional, boolean streaming, Object data)` | `void` | 添加边 |
| `addEdge(String source, String target)` | `void` | 添加简单边 |
| `toMermaid(String title, int expandSubgraph, boolean enableAnimation)` | `String` | 转换为 Mermaid 语法 |
| `toMermaid()` | `String` | 使用默认参数转换 |
| `getGraph()` | `DrawableGraph` | 获取底层图 |

### 5.2 DrawableGraph

可绘制图的容器。

**包路径**：`com.openjiuwen.core.graph.visualization`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getNodes()` | `Map<String, DrawableNode>` | 获取所有节点 |
| `getEdges()` | `List<DrawableEdge>` | 获取所有边 |
| `getStartNodes()` | `List<DrawableNode>` | 获取起始节点 |
| `getEndNodes()` | `List<DrawableNode>` | 获取结束节点 |
| `getBreakNodes()` | `List<DrawableNode>` | 获取中断节点 |

### 5.3 DrawableNode

可绘制图中的节点。

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getId()` | `String` | 获取节点 ID |
| `getName()` | `String` | 获取节点名称 |
| `getMetadata()` | `Map<String, Object>` | 获取元数据 |

### 5.4 DrawableSubgraphNode

包含子图的可绘制节点，用于嵌套可视化（循环、子工作流）。

**包路径**：`com.openjiuwen.core.graph.visualization`  
**继承**：`DrawableNode`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSubgraph()` | `DrawableGraph` | 获取子图 |
| `setSubgraph(DrawableGraph)` | `void` | 设置子图 |

### 5.5 DrawableEdge

可绘制图中的边。

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSource()` | `String` | 获取源节点 |
| `getTarget()` | `String` | 获取目标节点 |
| `getData()` | `Object` | 获取边数据 |
| `isConditional()` | `boolean` | 是否为条件边 |
| `isStreaming()` | `boolean` | 是否为流式边 |

### 5.6 DrawableBranchRouter

分支路由器的可绘制信息。

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getTargets()` | `List<String>` | 获取目标列表 |
| `getDatas()` | `List<String>` | 获取数据列表 |

### 5.7 MermaidDiagram

从 DrawableGraph 生成 Mermaid 流程图语法。

**包路径**：`com.openjiuwen.core.graph.visualization`

**构造方法**：`MermaidDiagram()`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toMermaid(DrawableGraph graph, String title, int expandSubgraph, boolean enableAnimation)` | `String` | 转换为 Mermaid 语法 |

**支持特性**：节点形状（普通、圆角）、链接样式（普通、虚线、粗线）、子图展开、动画属性。
