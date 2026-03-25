# Graph 模块 API 文档

> 包路径：`com.openjiuwen.core.graph`

图执行引擎、Pregel 运行时、可视化与图存储实现。基于 `graph` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `54` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.graph` | 11 |
| `com.openjiuwen.core.graph.pregel` | 21 |
| `com.openjiuwen.core.graph.store` | 8 |
| `com.openjiuwen.core.graph.stream_actor` | 7 |
| `com.openjiuwen.core.graph.visualization` | 7 |

## `com.openjiuwen.core.graph`

公开类型：`11`

### `AtomicNode`

- 类型：`class`
- 声明：`public abstract class AtomicNode`
- 说明：Abstract atomic node that validates the session, invokes the inner logic, and commits component state.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object atomicInvoke(Map<String, Object> kwargs)` | `Object` | Execute the atomic node operation with session validation and state commit. |
| `protected abstract Object doAtomicInvoke(Map<String, Object> kwargs)` | `Object` | Internal atomic invoke logic to be implemented by subclasses. |

### `CompiledGraph`

- 类型：`class`
- 声明：`public class CompiledGraph extends ExecutableGraph<Object, Map<String, Object>>`
- 说明：A compiled graph that wraps a Pregel engine and a Checkpointer for execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CompiledGraph(Pregel pregel, Checkpointer checkpointer)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected Map<String, Object> doInvoke(Object inputs, BaseSession session, Object config)` | `Map<String, Object>` | - |
| `public Iterator<Map<String, Object>> stream(Object inputs, BaseSession session)` | `Iterator<Map<String, Object>>` | - |
| `public void interrupt(Map<String, Object> message)` | `void` | - |

### `Executable`

- 类型：`class`
- 声明：`public abstract class Executable<I, O>`
- 说明：Generic executable component base class with invoke/stream/collect/transform abilities.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public O onInvoke(I inputs, BaseSession session, Object... kwargs)` | `O` | Invoke the component with the given inputs and session. |
| `public Iterator<O> onStream(I inputs, BaseSession session, Object... kwargs)` | `Iterator<O>` | Stream the component output. |
| `public O onCollect(I inputs, BaseSession session, Object... kwargs)` | `O` | Collect from streaming inputs and produce a single output. |
| `public Iterator<O> onTransform(I inputs, BaseSession session, Object... kwargs)` | `Iterator<O>` | Transform streaming inputs to streaming outputs. |
| `public boolean skipTrace()` | `boolean` | Whether tracing should be skipped for this component. |
| `public boolean graphInvoker()` | `boolean` | Whether this component is a graph invoker. |
| `public boolean postCommit()` | `boolean` | Whether post-commit should be performed after execution. |
| `public String componentType()` | `String` | The component type identifier. |

### `ExecutableGraph`

- 类型：`class`
- 声明：`public abstract class ExecutableGraph<I, O> extends Executable<I, O>`
- 说明：An executable graph that wraps the standard invoke/stream/collect/transform with config extraction from the input map.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public O invoke(I inputs, BaseSession session)` | `O` | Invoke the graph. |
| `public Iterator<O> stream(I inputs, BaseSession session)` | `Iterator<O>` | Stream the graph output. |
| `public O collect(Iterator<I> inputs, BaseSession session)` | `O` | Collect from streaming inputs and produce a single output. |
| `public Iterator<O> transform(Iterator<I> inputs, BaseSession session)` | `Iterator<O>` | Transform streaming inputs to streaming outputs. |
| `public void interrupt(Map<String, Object> message)` | `void` | Handle interrupt messages. |
| `protected abstract O doInvoke(I inputs, BaseSession session, Object config)` | `O` | Internal invoke implementation to be provided by subclasses. |

### `Graph`

- 类型：`class`
- 声明：`public abstract class Graph`
- 说明：Abstract graph definition with node/edge management and compilation.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Graph startNode(String nodeId)` | `Graph` | Set the start node for the graph. |
| `public Graph endNode(String nodeId)` | `Graph` | Set the end node for the graph. |
| `public abstract Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll)` | `Graph` | Add a node to the graph. |
| `public Graph addNode(String nodeId, Executable<?, ?> node)` | `Graph` | Add a node to the graph (no barrier, default). |
| `public abstract Graph addEdge(Object sourceNodeId, String targetNodeId)` | `Graph` | Add an edge from one or more source nodes to a target node. |
| `public abstract Graph addConditionalEdges(String sourceNodeId, Object router)` | `Graph` | Add conditional edges from a source node using a router. |
| `public abstract ExecutableGraph<?, ?> compile(BaseSession session)` | `ExecutableGraph<?, ?>` | Compile the graph into an executable form. |
| `public abstract Map<String, Executable<?, ?>> getNodes()` | `Map<String, Executable<?, ?>>` | Get the nodes in this graph. |

### `GraphNodeState`

- 类型：`class`
- 声明：`public class GraphNodeState`
- 说明：Graph-level state containing the list of source node IDs that have been visited.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sourceNodeId` | `List<String>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphNodeState()` | - |
| `public GraphNodeState(List<String> sourceNodeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> getSourceNodeId()` | `List<String>` | - |
| `public void setSourceNodeId(List<String> sourceNodeId)` | `void` | - |
| `public void merge(GraphNodeState other)` | `void` | Merge another state into this one by concatenating source node IDs. |

### `PregelGraph`

- 类型：`class`
- 声明：`public class PregelGraph extends Graph`
- 说明：PregelGraph is the main graph builder for constructing a Pregel-based workflow graph.
- 嵌套公开类型：`PregelGraph.Branch`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PregelGraph()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Graph startNode(String nodeId)` | `Graph` | - |
| `public Graph endNode(String nodeId)` | `Graph` | - |
| `public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll)` | `Graph` | - |
| `public Map<String, Executable<?, ?>> getNodes()` | `Map<String, Executable<?, ?>>` | - |
| `public Vertex getVertex(String nodeId)` | `Vertex` | Get the internal vertex wrapper for a node. |
| `public Graph addEdge(Object sourceNodeId, String targetNodeId)` | `Graph` | - |
| `public Graph addConditionalEdges(String sourceNodeId, Object router)` | `Graph` | - |
| `public ExecutableGraph<?, ?> compile(BaseSession session)` | `ExecutableGraph<?, ?>` | - |
| `public void reset()` | `void` | Reset all vertices for reuse. |

### `PregelGraph.Branch`

- 类型：`class`
- 声明：`public static class Branch`
- 说明：A conditional branch definition.
- 宿主类型：`PregelGraph`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Branch(Object condition)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Function<Object, Object> getCondition()` | `Function<Object, Object>` | - |

### `Router`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface Router extends Function<Object, Object>`
- 说明：Functional interface for a graph router that determines conditional edge targets.
- 注解：`@FunctionalInterface`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `Vertex`

- 类型：`class`
- 声明：`public class Vertex extends AtomicNode implements StreamConsumer`
- 说明：Vertex is the execution wrapper for a graph node.
- 嵌套公开类型：`Vertex.MixModeAware`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Vertex(String nodeId, Executable<?, ?> executable)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean init(BaseSession session, Map<String, Object> kwargs)` | `boolean` | Initialize the vertex with a session and optional context. |
| `public Map<String, Object> call(GraphNodeState state, Object config) throws Exception` | `Map<String, Object>` | Main entry point - called by the Pregel engine. |
| `protected Object doAtomicInvoke(Map<String, Object> kwargs)` | `Object` | - |
| `public void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback)` | `void` | Handle stream-in call for stream abilities (COLLECT, TRANSFORM). |
| `public boolean isDone()` | `boolean` | - |
| `public boolean shouldHandleMessage()` | `boolean` | - |
| `public void reset()` | `void` | Reset the vertex for reuse. |
| `public String getNodeId()` | `String` | - |
| `public Executable<Object, Object> getExecutable()` | `Executable<Object, Object>` | - |
| `public NodeSession getSession()` | `NodeSession` | - |
| `public boolean isEndNode()` | `boolean` | - |
| `public void setEndNode(boolean endNode)` | `void` | - |

### `Vertex.MixModeAware`

- 类型：`interface`
- 声明：`public interface MixModeAware`
- 说明：Marker interface for executables that support mixed mode (stream + batch).
- 宿主类型：`Vertex`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void setMix()` | `void` | - |

## `com.openjiuwen.core.graph.pregel`

公开类型：`21`

### `BarrierChannel`

- 类型：`class`
- 声明：`public class BarrierChannel extends Channel`
- 说明：Channel for N\u21921 fan-in barrier synchronization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BarrierChannel(String nodeName, Set<String> expected)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getKey()` | `String` | - |
| `public String getNodeName()` | `String` | - |
| `public boolean isReady()` | `boolean` | - |
| `public boolean accept(Message msg)` | `boolean` | - |
| `public void consume()` | `void` | - |
| `public Object snapshot()` | `Object` | - |
| `public void restore(Object snapshotData)` | `void` | - |

### `BarrierMessage`

- 类型：`class`
- 声明：`public class BarrierMessage extends Message`
- 说明：Barrier message for N\u21921 fan-in synchronization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BarrierMessage(String sender, String target)` | - |
| `public BarrierMessage(String sender, String target, Object payload)` | - |

### `BarrierRouter`

- 类型：`class`
- 声明：`public class BarrierRouter implements IRouter`
- 说明：Barrier router that sends barrier messages for N\u21921 fan-in synchronization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BarrierRouter(List<String> targets)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Message> dispatch(String sourceNode)` | `List<Message>` | - |

### `Channel`

- 类型：`class`
- 声明：`public abstract class Channel`
- 说明：Abstract channel for message passing between Pregel nodes.

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected Channel(String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getKey()` | `String` | Get the routing key for this channel. |
| `public String getNodeName()` | `String` | Get the node name this channel belongs to. |
| `public abstract boolean isReady()` | `boolean` | Check whether the channel has received enough messages to trigger execution. |
| `public abstract boolean accept(Message msg)` | `boolean` | Accept an incoming message. |
| `public abstract void consume()` | `void` | Consume the buffered messages and reset the channel. |
| `public abstract Object snapshot()` | `Object` | Create a snapshot of the current channel state for persistence. |
| `public abstract void restore(Object snapshotData)` | `void` | Restore channel state from a snapshot. |

### `ChannelManager`

- 类型：`class`
- 声明：`public class ChannelManager`
- 说明：Manages all channels and message routing between Pregel nodes.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ChannelManager(List<Channel> channels)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void bufferMessage(Message msg)` | `void` | Add a message to the buffer for the next flush. |
| `public boolean isEmpty()` | `boolean` | Check if the message buffer is empty. |
| `public void flush()` | `void` | Flush buffered messages into channels and update ready nodes. |
| `public List<String> getReadyNodes()` | `List<String>` | Get names of all nodes that are ready to execute. |
| `public void consume(String nodeName)` | `void` | Consume (clear) all ready channels for the given node. |
| `public Map<String, Object> snapshot()` | `Map<String, Object>` | Create a snapshot of all channel states for persistence. |
| `public void restore(Map<String, Object> snapshotMap)` | `void` | Restore channel states from a snapshot. |
| `public List<Message> getBuffer()` | `List<Message>` | Get the raw buffer (for error state persistence). |

### `ConditionalRouter`

- 类型：`class`
- 声明：`public class ConditionalRouter implements IRouter`
- 说明：Conditional router that determines targets dynamically via a selector function.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ConditionalRouter(Function<Object, Object> selector)` | Create a conditional router. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Message> dispatch(String sourceNode)` | `List<Message>` | - |

### `GraphInterrupt`

- 类型：`class`
- 声明：`public class GraphInterrupt extends Exception`
- 说明：Exception thrown when a graph execution is interrupted.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphInterrupt()` | - |
| `public GraphInterrupt(Interrupt value)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Interrupt getValue()` | `Interrupt` | - |

### `IRouter`

- 类型：`interface`
- 声明：`public interface IRouter`
- 说明：Router interface for dispatching messages after a node executes.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `List<Message> dispatch(String sourceNode)` | `List<Message>` | Dispatch messages from the given source node. |

### `Interrupt`

- 类型：`class`
- 声明：`public class Interrupt`
- 说明：Represents an interrupt value during graph execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Interrupt(Object value)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getValue()` | `Object` | - |
| `public String toString()` | `String` | - |

### `Message`

- 类型：`class`
- 声明：`public class Message`
- 说明：Base message passed between Pregel nodes via channels.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sender` | `String` | `private final` | `-` | - |
| `target` | `String` | `private final` | `-` | - |
| `payload` | `Object` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Message(String sender, String target)` | - |
| `public Message(String sender, String target, Object payload)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getSender()` | `String` | - |
| `public String getTarget()` | `String` | - |
| `public Object getPayload()` | `Object` | - |
| `public String toString()` | `String` | - |

### `NodeTask`

- 类型：`class`
- 声明：`public class NodeTask implements Callable<Object>`
- 说明：Executes a single Pregel node and produces routing messages.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public NodeTask(PregelNode node, PregelConfig config, int version)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object call() throws Exception` | `Object` | Execute the node function and dispatch routing messages. |

### `Pregel`

- 类型：`class`
- 声明：`public class Pregel`
- 说明：Pregel graph execution engine implementing the BSP (Bulk Synchronous Parallel) model.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Pregel(Map<String, PregelNode> nodes, List<Channel> channels, Store store, Consumer<PregelLoop> afterStep)` | - |
| `public Pregel(Map<String, PregelNode> nodes, List<Channel> channels, String initial, Store store, Consumer<PregelLoop> afterStep)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> run(PregelConfig config) throws Exception` | `Map<String, Object>` | Execute the Pregel graph computation. |
| `public Map<String, PregelNode> getNodes()` | `Map<String, PregelNode>` | - |
| `public List<Channel> getChannels()` | `List<Channel>` | - |
| `public String getInitial()` | `String` | - |
| `public Store getStore()` | `Store` | - |
| `public Consumer<PregelLoop> getAfterStep()` | `Consumer<PregelLoop>` | - |

### `PregelBuilder`

- 类型：`class`
- 声明：`public class PregelBuilder`
- 说明：Builder for constructing a Pregel graph engine.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PregelBuilder()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public PregelBuilder addNode(String name, Object fn, List<IRouter> routers)` | `PregelBuilder` | Add a node to the graph. |
| `public PregelBuilder addNode(String name, Object fn)` | `PregelBuilder` | Add a node with no initial routers. |
| `public PregelBuilder addEdge(Object start, Object end)` | `PregelBuilder` | Add an edge between nodes. |
| `public PregelBuilder addBranch(String src, java.util.function.Function<Object, Object> selector)` | `PregelBuilder` | Add a conditional branch from a source node using a selector function. |
| `public Pregel build(Store store, Consumer<PregelLoop> afterStepCallback)` | `Pregel` | Build the Pregel engine. |
| `public Pregel build()` | `Pregel` | Build the Pregel engine with no store or callback. |

### `PregelConfig`

- 类型：`class`
- 声明：`public class PregelConfig`
- 说明：Configuration for Pregel graph execution.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sessionId` | `String` | `private` | `-` | - |
| `recursionLimit` | `int` | `private` | `-` | - |
| `ns` | `String` | `private` | `-` | - |
| `parentNs` | `String` | `private` | `-` | - |
| `DEFAULT` | `PregelConfig` | `public static final` | `new PregelConfig(null, null, PregelConstants.MAX_RECURSIVE_LIMIT)` | Default Pregel configuration. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PregelConfig()` | - |
| `public PregelConfig(String sessionId, String ns, int recursionLimit)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getSessionId()` | `String` | - |
| `public void setSessionId(String sessionId)` | `void` | - |
| `public int getRecursionLimit()` | `int` | - |
| `public void setRecursionLimit(int recursionLimit)` | `void` | - |
| `public String getNs()` | `String` | - |
| `public void setNs(String ns)` | `void` | - |
| `public String getParentNs()` | `String` | - |
| `public void setParentNs(String parentNs)` | `void` | - |
| `public Object get(String key)` | `Object` | Get a config value by key name (for compatibility with dict-style access). |
| `public Map<String, Object> toMap()` | `Map<String, Object>` | Convert to a map representation. |
| `public static PregelConfig createInnerConfig(PregelConfig config)` | `PregelConfig` | Create an inner config copy with defaults applied. |

### `PregelConstants`

- 类型：`class`
- 声明：`public final class PregelConstants`
- 说明：Constants for the Pregel graph execution engine.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `START` | `String` | `public static final` | `"__start__"` | Virtual start node identifier. |
| `END` | `String` | `public static final` | `"__end__"` | Virtual end node identifier. |
| `MAX_RECURSIVE_LIMIT` | `int` | `public static final` | `10000` | Default maximum recursion (super-step) limit. |
| `TASK_STATUS_INTERRUPT` | `String` | `public static final` | `"__interrupt__"` | Task status for interrupted execution. |
| `TASK_STATUS_ERROR` | `String` | `public static final` | `"__error__"` | Task status for failed execution. |
| `NS_SEPARATOR` | `String` | `public static final` | `":"` | Namespace separator used in config paths. |
| `NS_REPLACE_CHAR` | `String` | `public static final` | `"#"` | Replacement character for namespace separator in keys. |
| `NS` | `String` | `public static final` | `"ns"` | Config key for namespace. |
| `PARENT_NS` | `String` | `public static final` | `"parent_ns"` | Config key for parent namespace. |
| `SESSION_ID` | `String` | `public static final` | `"session_id"` | Config key for session ID. |
| `RECURSION_LIMIT` | `String` | `public static final` | `"recursion_limit"` | Config key for recursion limit. |

### `PregelLoop`

- 类型：`class`
- 声明：`public class PregelLoop`
- 说明：Pregel execution loop implementing the BSP (Bulk Synchronous Parallel) model.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PregelLoop(Pregel graph, PregelConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void init()` | `void` | Initialize the Pregel loop, restoring state if available. |
| `public boolean runStep() throws Exception` | `boolean` | Execute one super-step of the Pregel computation. |
| `public int getStep()` | `int` | - |
| `public PregelConfig getConfig()` | `PregelConfig` | - |
| `public List<String> getActiveNodes()` | `List<String>` | - |

### `PregelNode`

- 类型：`class`
- 声明：`public class PregelNode`
- 说明：Represents a node in the Pregel execution graph.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PregelNode(String name, Object func, List<IRouter> routers)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | - |
| `public Object getFunc()` | `Object` | - |
| `public List<IRouter> getRouters()` | `List<IRouter>` | - |

### `StaticRouter`

- 类型：`class`
- 声明：`public class StaticRouter implements IRouter`
- 说明：Static router that sends trigger messages to fixed targets (1\u2192N).

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StaticRouter(List<String> targets)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Message> dispatch(String sourceNode)` | `List<Message>` | - |

### `TaskExecutorPool`

- 类型：`class`
- 声明：`public class TaskExecutorPool`
- 说明：Pool for executing Pregel node tasks concurrently using virtual threads.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskExecutorPool(PregelConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void submit(PregelNode node, int version)` | `void` | Submit a node for execution. |
| `public void waitAll() throws Exception` | `void` | Wait for all submitted tasks to complete. |
| `public void cancelAll()` | `void` | Cancel all running tasks. |
| `public void clear()` | `void` | Clear all result collections. |
| `public List<Message> getSucceedMessages()` | `List<Message>` | - |
| `public Map<String, PendingNode> getFailed()` | `Map<String, PendingNode>` | - |

### `TriggerChannel`

- 类型：`class`
- 声明：`public class TriggerChannel extends Channel`
- 说明：Channel that triggers when any message is received.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TriggerChannel(String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean isReady()` | `boolean` | - |
| `public boolean accept(Message msg)` | `boolean` | - |
| `public void consume()` | `void` | - |
| `public Object snapshot()` | `Object` | - |
| `public void restore(Object snapshotData)` | `void` | - |

### `TriggerMessage`

- 类型：`class`
- 声明：`public class TriggerMessage extends Message`
- 说明：Trigger message that activates a target node in the next super-step.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TriggerMessage(String sender, String target)` | - |
| `public TriggerMessage(String sender, String target, Object payload)` | - |

## `com.openjiuwen.core.graph.store`

公开类型：`8`

### `GraphStore`

- 类型：`class`
- 声明：`public class GraphStore implements Store`
- 说明：Decorator around Store that adds logging for graph state operations.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphStore(Store delegate)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Optional<GraphStoreState> get(String sessionId, String ns)` | `Optional<GraphStoreState>` | - |
| `public void save(String sessionId, String ns, GraphStoreState state)` | `void` | - |
| `public void delete(String sessionId, String ns)` | `void` | - |

### `GraphStoreState`

- 类型：`class`
- 声明：`public class GraphStoreState`
- 说明：Persisted state of a Pregel graph execution for recovery/resume.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `ns` | `String` | `private final` | `-` | - |
| `step` | `int` | `private final` | `-` | - |
| `channelValues` | `Map<String, Object>` | `private final` | `-` | - |
| `pendingBuffer` | `List<Message>` | `private final` | `-` | - |
| `pendingNode` | `Map<String, PendingNode>` | `private final` | `-` | - |
| `nodeVersion` | `Map<String, Integer>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphStoreState(String ns, int step, Map<String, Object> channelValues, List<Message> pendingBuffer, Map<String, PendingNode> pendingNode, Map<String, Integer> nodeVersion)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getNs()` | `String` | - |
| `public int getStep()` | `int` | - |
| `public Map<String, Object> getChannelValues()` | `Map<String, Object>` | - |
| `public List<Message> getPendingBuffer()` | `List<Message>` | - |
| `public Map<String, PendingNode> getPendingNode()` | `Map<String, PendingNode>` | - |
| `public Map<String, Integer> getNodeVersion()` | `Map<String, Integer>` | - |
| `public static GraphStoreState create(String ns, int step, Map<String, Object> channelSnapshot, List<Message> pendingBuffer, Map<String, PendingNode> pendingNode, Map<String, Integer> nodeVersion)` | `GraphStoreState` | Factory method to create a new GraphStoreState. |

### `InMemoryStore`

- 类型：`class`
- 声明：`public class InMemoryStore implements Store`
- 说明：In-memory implementation of the graph state store.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Optional<GraphStoreState> get(String sessionId, String ns)` | `Optional<GraphStoreState>` | - |
| `public void save(String sessionId, String ns, GraphStoreState state)` | `void` | - |
| `public void delete(String sessionId, String ns)` | `void` | - |

### `PendingNode`

- 类型：`class`
- 声明：`public class PendingNode`
- 说明：Represents a pending (failed or interrupted) node in the graph execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PendingNode(String nodeName, String status)` | - |
| `public PendingNode(String nodeName, String status, List<Exception> exceptions)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getNodeName()` | `String` | - |
| `public String getStatus()` | `String` | - |
| `public List<Exception> getExceptions()` | `List<Exception>` | - |

### `Serializer`

- 类型：`class`
- 声明：`public abstract class Serializer`
- 说明：Abstract serializer for graph state persistence.
- 嵌套公开类型：`Serializer.TypedBytes`、`Serializer.JsonSerializer`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract TypedBytes dumpsTyped(Object obj)` | `TypedBytes` | Serialize an object to a typed byte representation. |
| `public abstract Object loadsTyped(TypedBytes data)` | `Object` | Deserialize a typed byte representation back to an object. |
| `public static Serializer create(String typeName)` | `Serializer` | Create a serializer of the given type. |

### `Serializer.JsonSerializer`

- 类型：`class`
- 声明：`public static class JsonSerializer extends Serializer`
- 说明：JSON-based serializer implementation using Jackson.
- 宿主类型：`Serializer`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public TypedBytes dumpsTyped(Object obj)` | `TypedBytes` | - |
| `public Object loadsTyped(TypedBytes data)` | `Object` | - |

### `Serializer.TypedBytes`

- 类型：`record`
- 声明：`public record TypedBytes(String type, byte[] data)`
- 说明：Container for typed serialized data.
- 宿主类型：`Serializer`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private final` | `-` | - |
| `data` | `byte[]` | `private final` | `-` | - |

### `Store`

- 类型：`interface`
- 声明：`public interface Store`
- 说明：Abstract interface for graph state persistence.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Optional<GraphStoreState> get(String sessionId, String ns)` | `Optional<GraphStoreState>` | Get the stored graph state for a given session and namespace. |
| `void save(String sessionId, String ns, GraphStoreState state)` | `void` | Save graph state. |
| `void delete(String sessionId, String ns)` | `void` | Delete graph state. |

## `com.openjiuwen.core.graph.stream_actor`

公开类型：`7`

### `ActorManager`

- 类型：`class`
- 声明：`public class ActorManager`
- 说明：Manages stream actors for inter-node stream communication in a graph.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ActorManager(Map<String, List<String>> streamEdges, StreamGraph graph, boolean subGraph, BaseSession session, java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider)` | Create an ActorManager. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BlockingQueue<Object> subWorkflowStream()` | `BlockingQueue<Object>` | Get the sub-workflow stream queue. |
| `public StreamTransform getStreamTransform()` | `StreamTransform` | - |
| `public void produce(String producerId, Object messageContent, ComponentAbility ability, boolean firstFrame)` | `void` | Produce a stream message from a producer node to its consumers. |
| `public void endMessage(String producerId, ComponentAbility ability)` | `void` | Send an end message from a producer node. |
| `public Map<String, Object> consume(String consumerId, ComponentAbility ability, Object schema, Consumer<Object> streamCallback)` | `Map<String, Object>` | Consume stream data for a consumer node. |
| `public void shutdown()` | `void` | Shutdown all stream actors. |

### `StreamActor`

- 类型：`class`
- 声明：`public class StreamActor`
- 说明：Manages the stream lifecycle for a single graph vertex, coordinating stream-in abilities (COLLECT/TRANSFORM) with message producers.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamActor(String nodeId, StreamConsumer vertex, List<ComponentAbility> abilities, List<String> sources, long streamGeneratorTimeoutSeconds)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void send(Object message, ComponentAbility sourceAbility, boolean firstFrame, String producerId)` | `void` | Send a stream message to this actor. |
| `public Map<String, Object> generator(ComponentAbility ability, Map<String, Object> schema, Consumer<Object> streamCallback)` | `Map<String, Object>` | Get a generator (iterator) for consuming stream data for a specific ability. |
| `public void shutdown()` | `void` | Shutdown the stream actor, cancelling all running tasks. |

### `StreamConsumer`

- 类型：`interface`
- 声明：`public interface StreamConsumer`
- 说明：Interface for graph nodes that can consume streaming data.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback)` | `void` | Handle stream-in call for stream abilities (COLLECT, TRANSFORM). |
| `boolean shouldHandleMessage()` | `boolean` | Whether this consumer should handle stream messages. |
| `boolean isDone()` | `boolean` | Whether this consumer has completed its execution cycle. |

### `StreamGraph`

- 类型：`class`
- 声明：`public class StreamGraph`
- 说明：Manages stream consumers for a graph.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addStreamConsumer(StreamConsumer consumer, String nodeId)` | `void` | Register a stream consumer for a node. |
| `public StreamConsumer getNode(String nodeId)` | `StreamConsumer` | Get the stream consumer for a node. |

### `StreamPayload`

- 类型：`class`
- 声明：`public class StreamPayload`
- 说明：Payload for stream messages between graph nodes.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamPayload(Object message, ComponentAbility sourceAbility)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getMessage()` | `Object` | - |
| `public ComponentAbility getSourceAbility()` | `ComponentAbility` | - |

### `StreamProcessor`

- 类型：`class`
- 声明：`public class StreamProcessor`
- 说明：Processes stream messages for a single node by managing message routing and generating iterators for consuming stream data.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `END_SENTINEL` | `Object` | `public static final` | `new Object()` | Sentinel object to mark end of stream |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamProcessor(String nodeId, List<String> sources, long streamGeneratorTimeoutSeconds)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void run(ComponentAbility ability)` | `void` | Main processing loop. |
| `public void receive(StreamPayload payload)` | `void` | Receive a stream message for processing. |
| `public Map<String, Object> generator(Map<String, Object> schema, Consumer<Object> streamCallback)` | `Map<String, Object>` | Create a generator (iterator) map based on the schema. |

### `StreamTransform`

- 类型：`class`
- 声明：`public class StreamTransform`
- 说明：Utility class for transforming stream data using schemas or transformers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getByDefinedTransformer(Object originMessage, Object transformer)` | `Object` | Transform a message using a user-defined transformer function. |
| `public Object getByDefaultTransformer(Object originMessage, Object streamInputsSchema)` | `Object` | Transform a message using a default schema-based approach. |

## `com.openjiuwen.core.graph.visualization`

公开类型：`7`

### `Drawable`

- 类型：`class`
- 声明：`public class Drawable`
- 说明：Builds a drawable graph representation of a workflow for visualization purposes.
- 嵌套公开类型：`Drawable.TargetProvider`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Drawable()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addNode(String nodeId, ComponentComposable component)` | `void` | Convert a component to a DrawableNode and save it to the graph. |
| `public void addSimpleNode(String nodeId)` | `void` | Adds a plain (non-component) node to the graph. |
| `public void setStartNode(String nodeId)` | `void` | Sets the specified node as a start node. |
| `public void setEndNode(String nodeId)` | `void` | Sets the specified node as an end node. |
| `public void setBreakNode(String nodeId)` | `void` | Sets the specified node as a break node. |
| `public void addEdge(String source, String target, boolean conditional, boolean streaming, Object data)` | `void` | Adds an edge to the graph. |
| `public void addEdge(String source, String target)` | `void` | Convenience method: add a simple edge from source to target. |
| `public String toMermaid(String title, int expandSubgraph, boolean enableAnimation)` | `String` | Convert the graph to Mermaid flowchart syntax. |
| `public String toMermaid()` | `String` | Convert the graph to Mermaid flowchart syntax with default settings. |
| `public DrawableGraph getGraph()` | `DrawableGraph` | Gets the underlying drawable graph. |

### `Drawable.TargetProvider`

- 类型：`interface`
- 声明：`public interface TargetProvider`
- 说明：Optional interface for callables that can provide their target node names.
- 宿主类型：`Drawable`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `List<String> getTargets()` | `List<String>` | Gets the list of possible target node names. |

### `DrawableBranchRouter`

- 类型：`class`
- 声明：`public class DrawableBranchRouter`
- 说明：Represents a branch router's drawable information for visualization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DrawableBranchRouter(List<String> targets, List<String> datas)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> getTargets()` | `List<String>` | - |
| `public List<String> getDatas()` | `List<String>` | - |

### `DrawableEdge`

- 类型：`class`
- 声明：`public class DrawableEdge`
- 说明：Represents an edge in a drawable graph for visualization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DrawableEdge(String source, String target)` | - |
| `public DrawableEdge(String source, String target, Object data, boolean conditional, boolean streaming)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getSource()` | `String` | - |
| `public String getTarget()` | `String` | - |
| `public Object getData()` | `Object` | - |
| `public void setData(Object data)` | `void` | - |
| `public boolean isConditional()` | `boolean` | - |
| `public void setConditional(boolean conditional)` | `void` | - |
| `public boolean isStreaming()` | `boolean` | - |
| `public void setStreaming(boolean streaming)` | `void` | - |

### `DrawableGraph`

- 类型：`class`
- 声明：`public class DrawableGraph`
- 说明：Container for a drawable graph representation used in visualization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DrawableGraph()` | - |
| `public DrawableGraph(Map<String, DrawableNode> nodes, List<DrawableEdge> edges, List<DrawableNode> startNodes, List<DrawableNode> endNodes, List<DrawableNode> breakNodes)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, DrawableNode> getNodes()` | `Map<String, DrawableNode>` | - |
| `public List<DrawableEdge> getEdges()` | `List<DrawableEdge>` | - |
| `public List<DrawableNode> getStartNodes()` | `List<DrawableNode>` | - |
| `public List<DrawableNode> getEndNodes()` | `List<DrawableNode>` | - |
| `public List<DrawableNode> getBreakNodes()` | `List<DrawableNode>` | - |
| `public void setBreakNodes(List<DrawableNode> breakNodes)` | `void` | - |

### `DrawableNode`

- 类型：`class`
- 声明：`public class DrawableNode`
- 说明：Represents a node in a drawable graph for visualization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DrawableNode(String id)` | - |
| `public DrawableNode(String id, String name, Map<String, Object> metadata)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getId()` | `String` | - |
| `public String getName()` | `String` | - |
| `public void setName(String name)` | `void` | - |
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |

### `DrawableSubgraphNode`

- 类型：`class`
- 声明：`public class DrawableSubgraphNode extends DrawableNode`
- 说明：A drawable node that contains a subgraph for nested visualization.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DrawableSubgraphNode(String id)` | - |
| `public DrawableSubgraphNode(String id, DrawableGraph subgraph)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public DrawableGraph getSubgraph()` | `DrawableGraph` | - |
| `public void setSubgraph(DrawableGraph subgraph)` | `void` | - |

