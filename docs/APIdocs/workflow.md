# Workflow 模块 API 文档

> 包路径：`com.openjiuwen.core.workflow`

工作流编排、组件、条件、循环与可视化能力。基于 `workflow` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `47` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.workflow` | 13 |
| `com.openjiuwen.core.workflow.component` | 13 |
| `com.openjiuwen.core.workflow.component.loop` | 10 |
| `com.openjiuwen.core.workflow.component.loop.callback` | 3 |
| `com.openjiuwen.core.workflow.condition` | 8 |

## `com.openjiuwen.core.workflow`

公开类型：`13`

### `BaseWorkflow`

- 类型：`class`
- 声明：`public class BaseWorkflow implements HasDrawable`
- 说明：Base workflow implementation providing graph construction, edge management, component configuration, and ability inference.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BaseWorkflow()` | - |
| `public BaseWorkflow(WorkflowConfig workflowConfig, Graph newGraph)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public WorkflowConfig getConfig()` | `WorkflowConfig` | - |
| `public Graph getGraph()` | `Graph` | - |
| `public StreamGraph getStreamActor()` | `StreamGraph` | - |
| `public BaseWorkflow addWorkflowComp(String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | `BaseWorkflow` | Add a workflow component with full configuration. |
| `public BaseWorkflow startComp(String startCompId)` | `BaseWorkflow` | - |
| `public BaseWorkflow endComp(String endCompId)` | `BaseWorkflow` | - |
| `public BaseWorkflow addConnection(Object srcCompId, String targetCompId)` | `BaseWorkflow` | - |
| `public BaseWorkflow addStreamConnection(String srcCompId, String targetCompId)` | `BaseWorkflow` | - |
| `public BaseWorkflow addConditionalConnection(String srcCompId, Object router)` | `BaseWorkflow` | - |
| `public ExecutableGraph<?, ?> compile(BaseSession sessionArg, Object context)` | `ExecutableGraph<?, ?>` | - |
| `public String toMermaid(String title, int expandSubgraph, boolean enableAnimation)` | `String` | - |
| `public String toMermaid()` | `String` | - |
| `public Drawable getDrawable()` | `Drawable` | - |
| `public void autoCompleteAbilities()` | `void` | Auto-complete component abilities based on edge topology. |

### `BranchRouter`

- 类型：`class`
- 声明：`public class BranchRouter implements Router`
- 说明：Router that evaluates branch conditions and returns target node paths.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BranchRouter(boolean reportTrace)` | - |
| `public BranchRouter()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addBranch(Object condition, Object target, String branchId)` | `void` | Add a branch with condition and target(s). |
| `public DrawableBranchRouter getDrawableBranchRouter()` | `DrawableBranchRouter` | - |
| `public void setSession(Object session)` | `void` | Set the session for condition evaluation. |
| `public Object apply(Object input)` | `Object` | - |

### `ComponentComposable`

- 类型：`interface`
- 声明：`public interface ComponentComposable`
- 说明：Interface for workflow graph construction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `default void addComponent(Graph graph, String nodeId, boolean waitForAll)` | `void` | Add this component to a workflow graph. |
| `default Executable<?, ?> toExecutable()` | `Executable<?, ?>` | Convert this composable component to an executable instance. |

### `ComponentExecutable`

- 类型：`class`
- 声明：`public abstract class ComponentExecutable extends Executable<Object, Object>`
- 说明：Base executable for workflow components, providing the four fundamental execution patterns: invoke, stream, collect, transform.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | `Object` | - |
| `public Iterator<Object> onStream(Object inputs, BaseSession session, Object... kwargs)` | `Iterator<Object>` | - |
| `public Object onCollect(Object inputs, BaseSession session, Object... kwargs)` | `Object` | - |
| `public Iterator<Object> onTransform(Object inputs, BaseSession session, Object... kwargs)` | `Iterator<Object>` | - |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | Execute component synchronously with batch input and output. |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | Execute component with batch input but streaming output. |
| `public Object collect(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | Execute component with streaming input but batch output. |
| `public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | Execute component with streaming input and streaming output. |

### `HasDrawable`

- 类型：`interface`
- 声明：`public interface HasDrawable`
- 说明：Interface for components that have an associated Drawable graph for visualization.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Drawable getDrawable()` | `Drawable` | Gets the drawable visualization graph for this component. |

### `Workflow`

- 类型：`class`
- 声明：`public class Workflow`
- 说明：Main workflow class representing a directed graph of components.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Workflow(WorkflowCard card)` | - |
| `public Workflow()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public WorkflowCard getCard()` | `WorkflowCard` | - |
| `public Workflow setStartComp(String startCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema)` | `Workflow` | Set the starting component of the workflow. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | `Workflow` | Add a component to the workflow graph. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema)` | `Workflow` | Simplified addWorkflowComp with just ID, component, and schemas. |
| `public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp)` | `Workflow` | Minimal addWorkflowComp with just ID and component. |
| `public Workflow setEndComp(String endCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, String responseMode)` | `Workflow` | Set the ending component of the workflow. |
| `public Workflow setEndComp(String endCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema)` | `Workflow` | Simplified setEndComp. |
| `public Workflow addConnection(Object srcCompId, String targetCompId)` | `Workflow` | Add a data connection between components. |
| `public Workflow addStreamConnection(String srcCompId, String targetCompId)` | `Workflow` | Add a streaming connection between components. |
| `public Workflow addConditionalConnection(String srcCompId, Object router)` | `Workflow` | Add a conditional connection with routing logic. |
| `public WorkflowOutput invoke(Object inputs, Object session, ModelContext context, boolean isSub)` | `WorkflowOutput` | Execute the workflow synchronously. |
| `public WorkflowOutput invoke(Object inputs, Object session, ModelContext context)` | `WorkflowOutput` | Simplified invoke without sub flag. |
| `public Iterator<Object> stream(Object inputs, Object session, ModelContext context, boolean isSub)` | `Iterator<Object>` | Execute the workflow with streaming output. |
| `public Iterator<Object> stream(Object inputs, Object session, ModelContext context)` | `Iterator<Object>` | - |
| `public String draw(String title, String outputFormat, Object expandSubgraph)` | `String` | Generate a Mermaid diagram of the workflow. |
| `public HasDrawable getInternalDrawable()` | `HasDrawable` | - |
| `public Object invokeSubWorkflow(Object inputs, Object session, ModelContext context)` | `Object` | - |
| `public Object invokeSubWorkflow(Object inputs, Object session, ModelContext context, Object config)` | `Object` | - |
| `public Iterator<Object> streamSubWorkflow(Object inputs, Object session, ModelContext context)` | `Iterator<Object>` | - |
| `public Iterator<Object> streamSubWorkflow(Object inputs, Object session, ModelContext context, Object config)` | `Iterator<Object>` | - |

### `WorkflowCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class WorkflowCard extends BaseCard`
- 说明：Metadata card for a workflow.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `version` | `String` | `private` | `""` | - |
| `inputParams` | `Map<String, Object>` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object toolInfo()` | `Object` | - |
| `public String str()` | `String` | - |

### `WorkflowChunkType`

- 类型：`enum`
- 声明：`public enum WorkflowChunkType`
- 说明：Types of data chunks produced during workflow execution.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `INTERACTION` | `new WorkflowChunkType("interaction")` | - |
| `OUTPUT` | `new WorkflowChunkType("output")` | - |
| `ERROR` | `new WorkflowChunkType("error")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `WorkflowComponent`

- 类型：`class`
- 声明：`public abstract class WorkflowComponent extends ComponentExecutable implements ComponentComposable`
- 说明：Standard implementation combining both execution and graph construction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | `void` | - |

### `WorkflowConfig`

- 类型：`class`
- 声明：`public class WorkflowConfig`
- 说明：Configuration for a workflow instance.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `card` | `WorkflowCard` | `private` | `-` | - |
| `spec` | `WorkflowSpec` | `private` | `-` | - |
| `workflowMaxNestingDepth` | `int` | `private` | `5` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowConfig()` | - |
| `public WorkflowConfig(WorkflowCard card)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public WorkflowCard getCard()` | `WorkflowCard` | - |
| `public void setCard(WorkflowCard card)` | `void` | - |
| `public WorkflowSpec getSpec()` | `WorkflowSpec` | - |
| `public void setSpec(WorkflowSpec spec)` | `void` | - |
| `public int getWorkflowMaxNestingDepth()` | `int` | - |
| `public void setWorkflowMaxNestingDepth(int workflowMaxNestingDepth)` | `void` | - |

### `WorkflowExecutionState`

- 类型：`enum`
- 声明：`public enum WorkflowExecutionState`
- 说明：Possible states of workflow execution.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `COMPLETED` | `new WorkflowExecutionState()` | - |
| `INPUT_REQUIRED` | `new WorkflowExecutionState()` | - |
| `ERROR` | `new WorkflowExecutionState()` | - |

### `WorkflowOutput`

- 类型：`class`
- 声明：`public class WorkflowOutput`
- 说明：Final output container for workflow execution.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `result` | `Object` | `private` | `-` | - |
| `state` | `WorkflowExecutionState` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowOutput()` | - |
| `public WorkflowOutput(Object result, WorkflowExecutionState state)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getResult()` | `Object` | - |
| `public void setResult(Object result)` | `void` | - |
| `public WorkflowExecutionState getState()` | `WorkflowExecutionState` | - |
| `public void setState(WorkflowExecutionState state)` | `void` | - |
| `public String toString()` | `String` | - |

### `WorkflowSpec`

- 类型：`class`
- 声明：`public class WorkflowSpec`
- 说明：Complete specification of a workflow structure.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `edges` | `Map<String, List<String>>` | `private` | `new HashMap<>()` | - |
| `streamEdges` | `Map<String, List<String>>` | `private` | `new HashMap<>()` | - |
| `compConfigs` | `Map<String, NodeConfig>` | `private` | `new HashMap<>()` | - |
| `startNodes` | `List<String>` | `private` | `new ArrayList<>()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, List<String>> getEdges()` | `Map<String, List<String>>` | - |
| `public void setEdges(Map<String, List<String>> edges)` | `void` | - |
| `public Map<String, List<String>> getStreamEdges()` | `Map<String, List<String>>` | - |
| `public void setStreamEdges(Map<String, List<String>> streamEdges)` | `void` | - |
| `public Map<String, NodeConfig> getCompConfigs()` | `Map<String, NodeConfig>` | - |
| `public void setCompConfigs(Map<String, NodeConfig> compConfigs)` | `void` | - |
| `public List<String> getStartNodes()` | `List<String>` | - |
| `public void setStartNodes(List<String> startNodes)` | `void` | - |

## `com.openjiuwen.core.workflow.component`

公开类型：`13`

### `AdvancedLoopComponent`

- 类型：`interface`
- 声明：`public interface AdvancedLoopComponent extends ComponentComposable`
- 说明：Interface for advanced loop components that contain a body subgraph.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `HasDrawable getBody()` | `HasDrawable` | Gets the loop body (inner graph). |

### `Branch`

- 类型：`class`
- 声明：`public class Branch`
- 说明：A single branch with condition and target nodes.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Branch(Object conditionObj, List<String> target, String branchId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean evaluate(BaseSession session)` | `boolean` | - |
| `public Object traceInfo(BaseSession session)` | `Object` | - |
| `public String getBranchId()` | `String` | - |
| `public List<String> getTarget()` | `List<String>` | - |

### `BranchComponent`

- 类型：`class`
- 声明：`public class BranchComponent extends WorkflowComponent`
- 说明：Conditional routing component that evaluates branches and routes execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BranchComponent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addBranch(Object condition, Object target, String branchId)` | `void` | Add a branch with condition and target(s). |
| `public void addBranch(Object condition, Object target)` | `void` | - |
| `public BranchRouter router()` | `BranchRouter` | Gets the router associated with this branch component. |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | `void` | - |
| `public boolean skipTrace()` | `boolean` | - |

### `ComponentAbility`

- 类型：`enum`
- 声明：`public enum ComponentAbility`
- 说明：Defines the execution abilities of a workflow component.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `INVOKE` | `new ComponentAbility()` | Batch invoke: takes full input, returns full output. |
| `STREAM` | `new ComponentAbility()` | Streaming output: takes full input, yields chunks. |
| `COLLECT` | `new ComponentAbility()` | Collect: consumes a stream of chunks, returns full output. |
| `TRANSFORM` | `new ComponentAbility()` | Transform: consumes a stream of chunks, yields transformed chunks. |

### `End`

- 类型：`class`
- 声明：`public class End extends WorkflowComponent`
- 说明：Exit point component of the workflow with optional response template rendering.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public End(EndConfig conf)` | - |
| `public End(Map<String, Object> confMap)` | - |
| `public End()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | - |
| `public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | - |
| `public Object collect(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |

### `EndConfig`

- 类型：`class`
- 声明：`public class EndConfig`
- 说明：Configuration for the End component.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `responseTemplate` | `String` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public EndConfig(String responseTemplate)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static EndConfig fromMap(Map<String, Object> map)` | `EndConfig` | - |
| `public String getResponseTemplate()` | `String` | - |

### `IOConfig`

- 类型：`class`
- 声明：`public class IOConfig`
- 说明：Stub for I/O configuration of a workflow component.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `inputsSchema` | `Object` | `private` | `-` | - |
| `outputsSchema` | `Object` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public IOConfig()` | - |
| `public IOConfig(Object inputsSchema, Object outputsSchema)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getInputsSchema()` | `Object` | - |
| `public void setInputsSchema(Object inputsSchema)` | `void` | - |
| `public Object getOutputsSchema()` | `Object` | - |
| `public void setOutputsSchema(Object outputsSchema)` | `void` | - |

### `IntentDetectionComponent`

- 类型：`class`
- 声明：`public abstract class IntentDetectionComponent extends BranchComponent`
- 说明：Intent detection component that routes based on detected intent.

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `LoopComponent`

- 类型：`interface`
- 声明：`public interface LoopComponent extends ComponentComposable`
- 说明：Interface for loop components that contain a repeatable subgraph.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `HasDrawable getLoopGroup()` | `HasDrawable` | Gets the loop group (inner graph) that is iterated. |

### `NodeConfig`

- 类型：`class`
- 声明：`public class NodeConfig`
- 说明：Stub for node configuration in a workflow.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `abilities` | `List<ComponentAbility>` | `private` | `-` | - |
| `ioConfigs` | `IOConfig` | `private` | `-` | - |
| `streamIoConfigs` | `IOConfig` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public NodeConfig()` | - |
| `public NodeConfig(List<ComponentAbility> abilities, IOConfig ioConfigs, IOConfig streamIoConfigs)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<ComponentAbility> getAbilities()` | `List<ComponentAbility>` | - |
| `public void setAbilities(List<ComponentAbility> abilities)` | `void` | - |
| `public IOConfig getIoConfigs()` | `IOConfig` | - |
| `public void setIoConfigs(IOConfig ioConfigs)` | `void` | - |
| `public IOConfig getStreamIoConfigs()` | `IOConfig` | - |
| `public void setStreamIoConfigs(IOConfig streamIoConfigs)` | `void` | - |

### `Start`

- 类型：`class`
- 声明：`public class Start extends WorkflowComponent`
- 说明：Entry point component that passes inputs through as-is.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |

### `SubWorkflowComponent`

- 类型：`interface`
- 声明：`public interface SubWorkflowComponent extends ComponentComposable`
- 说明：Interface for sub-workflow components that wrap an inner workflow graph.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `HasDrawable getSubWorkflowInternal()` | `HasDrawable` | Gets the internal drawable of the sub-workflow. |

### `SubWorkflowComponentImpl`

- 类型：`class`
- 声明：`public class SubWorkflowComponentImpl extends WorkflowComponent implements SubWorkflowComponent`
- 说明：Component that wraps a sub-workflow and delegates execution to it.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SubWorkflowComponentImpl(Workflow subWorkflow)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | - |
| `public boolean graphInvoker()` | `boolean` | - |
| `public String componentType()` | `String` | - |
| `public Workflow getSubWorkflow()` | `Workflow` | - |
| `public HasDrawable getSubWorkflowInternal()` | `HasDrawable` | - |

## `com.openjiuwen.core.workflow.component.loop`

公开类型：`10`

### `AdvancedLoopComponentImpl`

- 类型：`class`
- 声明：`public class AdvancedLoopComponentImpl extends Executable<Object, Object> implements LoopController, AdvancedLoopComponent`
- 说明：Full advanced loop component implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AdvancedLoopComponentImpl(Object body, Object conditionParam, List<? extends LoopBreakComponent> breakNodes, List<LoopCallback> callbacks)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean isBroken()` | `boolean` | - |
| `public void breakLoop()` | `void` | - |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | `Object` | - |
| `public boolean graphInvoker()` | `boolean` | - |
| `public boolean skipTrace()` | `boolean` | - |
| `public HasDrawable getBody()` | `HasDrawable` | - |
| `public Executable<Object, Object> getBodyExecutable()` | `Executable<Object, Object>` | - |

### `EmptyExecutable`

- 类型：`class`
- 声明：`public class EmptyExecutable extends Executable<Object, Object>`
- 说明：No-op executable used as a placeholder node in the loop graph.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | `Object` | - |
| `public boolean skipTrace()` | `boolean` | - |

### `LoopBreakComponent`

- 类型：`class`
- 声明：`public class LoopBreakComponent extends WorkflowComponent`
- 说明：Component that breaks out of the current loop when invoked.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setController(LoopController loopController)` | `void` | - |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |

### `LoopComponentImpl`

- 类型：`class`
- 声明：`public class LoopComponentImpl extends WorkflowComponent implements LoopComponent`
- 说明：Full loop component implementation that creates an AdvancedLoopComponentImpl based on runtime inputs (loop type, condition, etc.).

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LoopComponentImpl(LoopGroup loopGroup, Map<String, Object> outputSchema)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |
| `public boolean graphInvoker()` | `boolean` | - |
| `public LoopGroup getLoop()` | `LoopGroup` | - |
| `public HasDrawable getLoopGroup()` | `HasDrawable` | - |

### `LoopController`

- 类型：`interface`
- 声明：`public interface LoopController`
- 说明：Controller interface for breaking out of loop execution.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void breakLoop()` | `void` | - |
| `boolean isBroken()` | `boolean` | - |

### `LoopGroup`

- 类型：`class`
- 声明：`public class LoopGroup extends BaseWorkflow`
- 说明：A group of components that form the body of a loop.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LoopGroup()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseWorkflow addWorkflowComp(String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | `BaseWorkflow` | - |
| `public LoopGroup startNodes(List<String> nodes)` | `LoopGroup` | Set the start nodes of the loop group. |
| `public BaseWorkflow startComp(String startCompId)` | `BaseWorkflow` | - |
| `public LoopGroup endNodes(Object nodes)` | `LoopGroup` | Set the end nodes of the loop group. |
| `public BaseWorkflow endComp(String endCompId)` | `BaseWorkflow` | - |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | `Object` | Invoke the loop group graph. |
| `public boolean skipTrace()` | `boolean` | - |
| `public boolean graphInvoker()` | `boolean` | - |
| `public List<LoopBreakComponent> getBreakComponents()` | `List<LoopBreakComponent>` | - |
| `public List<String> getStartNodesList()` | `List<String>` | - |
| `public List<String> getEndNodesList()` | `List<String>` | - |
| `public void checkValidate()` | `void` | Validate the loop group configuration. |

### `LoopInput`

- 类型：`class`
- 声明：`public class LoopInput`
- 说明：Input model for loop component configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `loopType` | `String` | `private` | `""` | - |
| `loopNumber` | `Integer` | `private` | `0` | - |
| `loopArray` | `Map<String, Object>` | `private` | `new HashMap<>()` | - |
| `boolExpression` | `Object` | `private` | `""` | - |
| `intermediateVar` | `Map<String, Object>` | `private` | `new HashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LoopInput()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getLoopType()` | `String` | - |
| `public void setLoopType(String loopType)` | `void` | - |
| `public Integer getLoopNumber()` | `Integer` | - |
| `public void setLoopNumber(Integer loopNumber)` | `void` | - |
| `public Map<String, Object> getLoopArray()` | `Map<String, Object>` | - |
| `public void setLoopArray(Map<String, Object> loopArray)` | `void` | - |
| `public Object getBoolExpression()` | `Object` | - |
| `public void setBoolExpression(Object boolExpression)` | `void` | - |
| `public Map<String, Object> getIntermediateVar()` | `Map<String, Object>` | - |
| `public void setIntermediateVar(Map<String, Object> intermediateVar)` | `void` | - |
| `public static LoopInput fromMap(Map<String, Object> map)` | `LoopInput` | Create a LoopInput from a map (similar to pydantic's model_validate). |

### `LoopSetVariableComponent`

- 类型：`class`
- 声明：`public class LoopSetVariableComponent extends WorkflowComponent`
- 说明：Component that sets variables in the loop's parent session scope.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LoopSetVariableComponent(Map<String, Object> variableMapping)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | - |

### `LoopType`

- 类型：`enum`
- 声明：`public enum LoopType`
- 说明：Types of loop conditions.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `ARRAY` | `new LoopType("array")` | - |
| `NUMBER` | `new LoopType("number")` | - |
| `ALWAYS_TRUE` | `new LoopType("always_true")` | - |
| `EXPRESSION` | `new LoopType("expression")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static LoopType fromValue(String value)` | `LoopType` | - |

### `PostLoopBody`

- 类型：`class`
- 声明：`public class PostLoopBody extends Executable<Object, Object>`
- 说明：Post-body executable that tracks the finish index for loop iteration.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | `Object` | - |
| `public boolean skipTrace()` | `boolean` | - |
| `public int getFinishIndex()` | `int` | - |
| `public void setFinishIndex(int finishIndex)` | `void` | - |

## `com.openjiuwen.core.workflow.component.loop.callback`

公开类型：`3`

### `IntermediateLoopVarCallback`

- 类型：`class`
- 声明：`public class IntermediateLoopVarCallback extends LoopCallback`
- 说明：Loop callback that initializes intermediate loop variables from the session state.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `intermediateLoopVar` | `Map<String, Object>` | `private final` | `-` | - |
| `intermediateLoopVarRoot` | `String` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public IntermediateLoopVarCallback(Map<String, Object> intermediateLoopVar, String intermediateLoopVarRoot)` | - |
| `public IntermediateLoopVarCallback(Map<String, Object> intermediateLoopVar)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object firstInLoop(BaseSession session)` | `Object` | - |
| `public Object outLoop(BaseSession session)` | `Object` | - |
| `public Object startRound(BaseSession session)` | `Object` | - |
| `public Object endRound(BaseSession session, int loopTimes)` | `Object` | - |

### `LoopCallback`

- 类型：`class`
- 声明：`public abstract class LoopCallback extends AtomicNode`
- 说明：Abstract loop callback that dispatches to stage-specific methods.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `FIRST_LOOP` | `String` | `public static final` | `"first_in_loop"` | - |
| `START_ROUND` | `String` | `public static final` | `"start_round"` | - |
| `END_ROUND` | `String` | `public static final` | `"end_round"` | - |
| `OUT_LOOP` | `String` | `public static final` | `"out_loop"` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void call(String loopStage, BaseSession session, Integer loopTimes)` | `void` | Call the callback for a given loop stage. |
| `public void call(String loopStage, BaseSession session)` | `void` | - |
| `protected Object doAtomicInvoke(Map<String, Object> kwargs)` | `Object` | - |
| `public abstract Object firstInLoop(BaseSession session)` | `Object` | Called once before the first loop iteration. |
| `public abstract Object outLoop(BaseSession session)` | `Object` | Called when the loop exits normally. |
| `public abstract Object startRound(BaseSession session)` | `Object` | Called at the start of each loop round. |
| `public abstract Object endRound(BaseSession session, int loopTimes)` | `Object` | Called at the end of each loop round. |

### `OutputCallback`

- 类型：`class`
- 声明：`public class OutputCallback extends LoopCallback`
- 说明：Loop callback that collects round results and generates final output.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `outputsFormat` | `Map<String, Object>` | `private final` | `-` | - |
| `resultRoot` | `String` | `private final` | `-` | - |
| `roundResultRoot` | `String` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OutputCallback(Map<String, Object> outputsFormat, String roundResultRoot, String resultRoot)` | - |
| `public OutputCallback(Map<String, Object> outputsFormat)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object firstInLoop(BaseSession session)` | `Object` | - |
| `public Object outLoop(BaseSession session)` | `Object` | - |
| `public Object startRound(BaseSession session)` | `Object` | - |
| `public Object endRound(BaseSession session, int loopTimes)` | `Object` | - |

## `com.openjiuwen.core.workflow.condition`

公开类型：`8`

### `AlwaysTrue`

- 类型：`class`
- 声明：`public class AlwaysTrue extends Condition`
- 说明：Condition that always evaluates to true.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object doInvoke(Object inputs, BaseSession session)` | `Object` | - |
| `public Object traceInfo(BaseSession session)` | `Object` | - |

### `ArrayCondition`

- 类型：`class`
- 声明：`public class ArrayCondition extends Condition`
- 说明：Loop condition over array items, resolving arrays from session state via input schema.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `arrays` | `Map<String, Object>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ArrayCondition(Map<String, Object> arrays)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object doInvoke(Object inputs, BaseSession session)` | `Object` | - |

### `ArrayConditionInSession`

- 类型：`class`
- 声明：`public class ArrayConditionInSession extends Condition`
- 说明：Loop condition over array items already stored in session (not from schema).

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `arrays` | `Map<String, Object>` | `private final` | `-` | - |
| `minLength` | `int` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ArrayConditionInSession(Map<String, Object> arrays)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object doInvoke(Object inputs, BaseSession session)` | `Object` | - |

### `Condition`

- 类型：`class`
- 声明：`public abstract class Condition extends AtomicNode`
- 说明：Abstract condition for workflow branching and loop control.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `inputSchema` | `Object` | `protected` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Condition()` | - |
| `public Condition(Object inputSchema)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean evaluate(BaseSession session)` | `boolean` | Evaluate the condition against the given session. |
| `protected Object doAtomicInvoke(Map<String, Object> kwargs)` | `Object` | - |
| `public abstract Object doInvoke(Object inputs, BaseSession session)` | `Object` | Perform the condition check. |
| `public Object traceInfo(BaseSession session)` | `Object` | Get trace info for this condition. |

### `ExpressionCondition`

- 类型：`class`
- 声明：`public class ExpressionCondition extends Condition`
- 说明：Condition that evaluates string expressions with variable substitution.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `expression` | `String` | `private final` | `-` | - |
| `matches` | `List<String>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExpressionCondition(String expression)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object traceInfo(BaseSession session)` | `Object` | - |
| `public Object doInvoke(Object inputs, BaseSession session)` | `Object` | - |
| `public boolean evaluate(BaseSession session)` | `boolean` | - |

### `FuncCondition`

- 类型：`class`
- 声明：`public class FuncCondition extends Condition`
- 说明：Condition that wraps a callable predicate.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `func` | `BooleanSupplier` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public FuncCondition(BooleanSupplier func)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object doInvoke(Object inputs, BaseSession session)` | `Object` | - |
| `public Object traceInfo(BaseSession session)` | `Object` | - |

### `NumberCondition`

- 类型：`class`
- 声明：`public class NumberCondition extends Condition`
- 说明：Loop condition based on iteration count, resolving limit from input schema.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `limit` | `Object` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public NumberCondition(Object limit)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object doInvoke(Object inputs, BaseSession session)` | `Object` | - |

### `NumberConditionInSession`

- 类型：`class`
- 声明：`public class NumberConditionInSession extends Condition`
- 说明：Loop condition based on iteration count with limit stored directly (not from schema).

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `limit` | `int` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public NumberConditionInSession(int limit)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object doInvoke(Object inputs, BaseSession session)` | `Object` | - |

