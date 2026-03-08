# Workflow 模块 API 文档

> 包路径：`com.openjiuwen.core.workflow`

Workflow 模块提供工作流编排引擎，支持组件组合、分支路由、条件判断、循环控制、子工作流嵌套以及流式处理，可以将多个组件按照 DAG 结构编排为完整的工作流。

---

## 目录

- [1. 核心类](#1-核心类)
- [2. 组件基础（component）](#2-组件基础component)
- [3. 条件判断（condition）](#3-条件判断condition)
- [4. 循环控制（loop）](#4-循环控制loop)
- [5. 循环回调（loop/callback）](#5-循环回调loopcallback)

---

## 1. 核心类

### 1.1 Workflow

工作流编排的主入口类，提供流式 API 构建和执行工作流。

**包路径**：`com.openjiuwen.core.workflow`

**构造方法**：
```java
Workflow(WorkflowCard card)
Workflow()
```

**组件添加方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `setStartComp(String startCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema)` | `Workflow` | 设置起始组件 |
| `addWorkflowComp(String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | `Workflow` | 添加工作流组件（完整参数） |
| `addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema)` | `Workflow` | 添加工作流组件（简化） |
| `addWorkflowComp(String compId, ComponentComposable workflowComp)` | `Workflow` | 添加工作流组件（最简） |
| `setEndComp(String endCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, String responseMode)` | `Workflow` | 设置结束组件（完整参数） |
| `setEndComp(String endCompId, ComponentComposable component, Object inputsSchema, Object outputsSchema)` | `Workflow` | 设置结束组件（简化） |

**连接方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addConnection(Object srcCompId, String targetCompId)` | `Workflow` | 添加普通连接（srcCompId 可为 String 或 List<String>） |
| `addStreamConnection(String srcCompId, String targetCompId)` | `Workflow` | 添加流式连接 |
| `addConditionalConnection(String srcCompId, Object router)` | `Workflow` | 添加条件连接 |

**执行方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object inputs, Object session, ModelContext context, boolean isSub)` | `WorkflowOutput` | 同步执行工作流 |
| `invoke(Object inputs, Object session, ModelContext context)` | `WorkflowOutput` | 同步执行工作流（非子工作流） |
| `stream(Object inputs, Object session, ModelContext context, boolean isSub)` | `Iterator<Object>` | 流式执行工作流 |
| `stream(Object inputs, Object session, ModelContext context)` | `Iterator<Object>` | 流式执行工作流（非子工作流） |
| `invokeSubWorkflow(Object inputs, Object session, ModelContext context)` | `Object` | 执行子工作流 |

**可视化方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `draw(String title, String outputFormat, Object expandSubgraph)` | `String` | 生成流程图 |

**其他方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getCard()` | `WorkflowCard` | 获取工作流卡片 |

### 1.2 BaseWorkflow

工作流内部实现类，管理图构建、连接验证和编译。

**包路径**：`com.openjiuwen.core.workflow`

**常量**：

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `COMP_ID_PATTERN` | `^[A-Za-z0-9_-]+$` | 组件 ID 格式要求 |
| `WORKFLOW_DRAWABLE` | `"WORKFLOW_DRAWABLE"` | 可绘制对象键 |

**构造方法**：
```java
BaseWorkflow()
BaseWorkflow(WorkflowConfig workflowConfig, Graph newGraph)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getConfig()` | `WorkflowConfig` | 获取配置 |
| `getGraph()` | `Graph` | 获取图 |
| `getStreamActor()` | `StreamGraph` | 获取流 Actor 图 |
| `addWorkflowComp(String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | `BaseWorkflow` | 添加组件 |
| `startComp(String startCompId)` | `BaseWorkflow` | 设置起始组件 |
| `endComp(String endCompId)` | `BaseWorkflow` | 设置结束组件 |
| `addConnection(Object srcCompId, String targetCompId)` | `BaseWorkflow` | 添加连接 |
| `addStreamConnection(String srcCompId, String targetCompId)` | `BaseWorkflow` | 添加流式连接 |
| `addConditionalConnection(String srcCompId, Object router)` | `BaseWorkflow` | 添加条件连接 |
| `compile(BaseSession sessionArg, Object context)` | `ExecutableGraph<?, ?>` | 编译为可执行图 |
| `autoCompleteAbilities()` | `void` | 自动推断组件能力 |

### 1.3 WorkflowCard

工作流卡片配置。

**包路径**：`com.openjiuwen.core.workflow`  
**继承**：`BaseCard`  
**注解**：`@Data`, `@SuperBuilder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@EqualsAndHashCode(callSuper = true)`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `version` | `String` | `""` | 版本 |
| `inputParams` | `Map<String, Object>` | - | 输入参数定义 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toolInfo()` | `Object` | 获取工具信息（覆盖） |
| `str()` | `String` | 字符串表示 |

### 1.4 WorkflowConfig

工作流配置容器。

**包路径**：`com.openjiuwen.core.workflow`

**构造方法**：
```java
WorkflowConfig()
WorkflowConfig(WorkflowCard card)
```

| 字段/方法 | 类型 | 说明 |
|-----------|------|------|
| `card` | `WorkflowCard` | 工作流卡片 |
| `spec` | `WorkflowSpec` | 工作流规格 |
| `workflowMaxNestingDepth` | `int` | 最大嵌套深度（默认 5） |

### 1.5 WorkflowSpec

工作流结构规格，描述节点、边和配置。

**包路径**：`com.openjiuwen.core.workflow`

| 字段 | 类型 | 说明 |
|------|------|------|
| `edges` | `Map<String, List<String>>` | 普通边映射 |
| `streamEdges` | `Map<String, List<String>>` | 流式边映射 |
| `compConfigs` | `Map<String, NodeConfig>` | 组件配置映射 |
| `startNodes` | `List<String>` | 起始节点列表 |

### 1.6 WorkflowOutput

工作流执行输出。

**包路径**：`com.openjiuwen.core.workflow`

**构造方法**：
```java
WorkflowOutput()
WorkflowOutput(Object result, WorkflowExecutionState state)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `result` | `Object` | 执行结果 |
| `state` | `WorkflowExecutionState` | 执行状态 |

### 1.7 WorkflowExecutionState（枚举）

工作流执行状态。

| 枚举值 | 说明 |
|--------|------|
| `COMPLETED` | 执行完成 |
| `INPUT_REQUIRED` | 需要用户输入 |
| `ERROR` | 执行错误 |

### 1.8 WorkflowChunkType（枚举）

工作流流式块类型。

| 枚举值 | 说明 |
|--------|------|
| `INTERACTION` | 交互块 |
| `OUTPUT` | 输出块 |
| `ERROR` | 错误块 |

### 1.9 BranchRouter

分支路由器，实现条件分支逻辑。

**包路径**：`com.openjiuwen.core.workflow`  
**实现**：`Router`

**构造方法**：
```java
BranchRouter(boolean reportTrace)
BranchRouter()
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addBranch(Object condition, Object target, String branchId)` | `void` | 添加分支 |
| `setSession(Object session)` | `void` | 设置会话 |
| `apply(Object input)` | `Object` | 评估条件并返回目标节点（Router 接口） |
| `getDrawableBranchRouter()` | `DrawableBranchRouter` | 获取可绘制路由器信息 |

### 1.10 ComponentComposable（接口）

组件图构建接口，用于将组件添加到图中。

**包路径**：`com.openjiuwen.core.workflow`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addComponent(Graph graph, String nodeId, boolean waitForAll)` | `void` | 将组件添加到图中（默认实现） |
| `toExecutable()` | `Executable<?, ?>` | 转换为可执行对象（默认实现） |

### 1.11 ComponentExecutable（抽象类）

组件可执行基类，桥接 `Executable` 和工作流 Session API。

**包路径**：`com.openjiuwen.core.workflow`  
**继承**：`Executable<Object, Object>`

**覆盖的 Executable 方法**（内部调用下方公共方法）：

| 方法签名 | 说明 |
|----------|------|
| `onInvoke(Object inputs, BaseSession session, Object... kwargs)` | 委托给 invoke |
| `onStream(Object inputs, BaseSession session, Object... kwargs)` | 委托给 stream |
| `onCollect(Object inputs, BaseSession session, Object... kwargs)` | 委托给 collect |
| `onTransform(Object inputs, BaseSession session, Object... kwargs)` | 委托给 transform |

**面向用户的执行方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | 同步调用 |
| `stream(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | 流式输出 |
| `collect(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | 收集流式输入 |
| `transform(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | 转换流式输入 |

### 1.12 WorkflowComponent（抽象类）

组合了执行和图构建能力的工作流组件抽象基类。

**包路径**：`com.openjiuwen.core.workflow`  
**继承**：`ComponentExecutable`  
**实现**：`ComponentComposable`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addComponent(Graph graph, String nodeId, boolean waitForAll)` | `void` | 覆盖默认图构建方式 |

### 1.13 HasDrawable（接口）

标记接口，表示组件支持可视化绘制。

**包路径**：`com.openjiuwen.core.workflow`

---

## 2. 组件基础（component）

### 2.1 ComponentAbility（枚举）

组件执行能力枚举。

**包路径**：`com.openjiuwen.core.workflow.component`

| 枚举值 | 说明 |
|--------|------|
| `INVOKE` | 批量调用：接收完整输入，返回完整输出 |
| `STREAM` | 流式输出：接收完整输入，返回输出块流 |
| `COLLECT` | 收集：消费输入块流，返回完整输出 |
| `TRANSFORM` | 转换：消费输入块流，返回输出块流 |

### 2.2 NodeConfig

节点配置。

**包路径**：`com.openjiuwen.core.workflow.component`

**构造方法**：
```java
NodeConfig()
NodeConfig(List<ComponentAbility> abilities, IOConfig ioConfigs, IOConfig streamIoConfigs)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `abilities` | `List<ComponentAbility>` | 组件能力列表 |
| `ioConfigs` | `IOConfig` | I/O 配置 |
| `streamIoConfigs` | `IOConfig` | 流式 I/O 配置 |

### 2.3 IOConfig

I/O Schema 配置。

**包路径**：`com.openjiuwen.core.workflow.component`

存储组件输入和输出的 Schema 定义。

### 2.4 EndConfig

结束组件配置。

**包路径**：`com.openjiuwen.core.workflow.component`

存储结束组件的模板和响应模式配置。

### 2.5 Start

起始组件，将输入直接传递到工作流。

**包路径**：`com.openjiuwen.core.workflow.component`  
**继承**：`WorkflowComponent`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | 透传输入 |

### 2.6 End

结束组件，支持模板渲染输出。

**包路径**：`com.openjiuwen.core.workflow.component`  
**继承**：`WorkflowComponent`

**模板语法**：使用 `{{变量名}}` 进行变量替换，支持嵌套路径（如 `{{a.b.c}}`）。

**构造方法**：
```java
End(EndConfig conf)
End(Map<String, Object> confMap)
End()
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | 模板渲染并返回输出 |
| `stream(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | 流式模板渲染 |
| `collect(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | 收集流式输入后渲染 |
| `transform(Object inputs, NodeSessionApi session, ModelContext context)` | `Iterator<Object>` | 转换流式输入并逐块渲染 |

**静态工具方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `renderTemplate(String template, Map<String, Object> inputs)` | `String` | 渲染模板（静态） |
| `splitTemplate(String template)` | `List<String>` | 分割模板（静态） |
| `getNestedValue(String path, Map<String, Object> data)` | `Object` | 获取嵌套值（静态） |

### 2.7 Branch

分支定义，包含条件和目标。

**包路径**：`com.openjiuwen.core.workflow.component`

**构造方法**：
```java
Branch(Object conditionObj, List<String> target, String branchId)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `evaluate(BaseSession session)` | `boolean` | 评估条件 |
| `traceInfo(BaseSession session)` | `Object` | 获取追踪信息 |
| `getBranchId()` | `String` | 获取分支 ID |
| `getTarget()` | `List<String>` | 获取目标节点列表 |

### 2.8 BranchComponent

分支组件，管理多个分支并路由到匹配的目标。

**包路径**：`com.openjiuwen.core.workflow.component`  
**继承**：`WorkflowComponent`

**构造方法**：`BranchComponent()`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addBranch(Object condition, Object target, String branchId)` | `void` | 添加分支 |
| `addBranch(Object condition, Object target)` | `void` | 添加分支（自动 ID） |
| `router()` | `BranchRouter` | 获取路由器 |
| `invoke(Object inputs, NodeSessionApi session, ModelContext context)` | `Object` | 透传输入 |
| `addComponent(Graph graph, String nodeId, boolean waitForAll)` | `void` | 添加到图并注入条件边 |
| `skipTrace()` | `boolean` | 返回 true，跳过追踪 |

### 2.9 IntentDetectionComponent（抽象类）

意图检测组件抽象基类。

**包路径**：`com.openjiuwen.core.workflow.component`  
**继承**：`BranchComponent`

### 2.10 SubWorkflowComponent（接口）

子工作流组件接口。

**包路径**：`com.openjiuwen.core.workflow.component`

### 2.11 SubWorkflowComponentImpl

子工作流组件实现。

**包路径**：`com.openjiuwen.core.workflow.component`  
**实现**：`SubWorkflowComponent`

封装外部 `Workflow` 实例作为嵌套子工作流执行。

### 2.12 AdvancedLoopComponent（接口）

高级循环组件接口。

**包路径**：`com.openjiuwen.core.workflow.component`  
**继承**：`ComponentComposable`

---

## 3. 条件判断（condition）

### 3.1 Condition（抽象类）

条件判断基类，使用模板方法模式。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`AtomicNode`

**构造方法**：
```java
Condition()
Condition(Object inputSchema)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `evaluate(BaseSession session)` | `boolean` | 评估条件 |
| `doInvoke(Object inputs, BaseSession session)` | `Object` | 执行条件判断（抽象） |
| `traceInfo(BaseSession session)` | `Object` | 获取追踪信息 |

### 3.2 AlwaysTrue

始终为 true 的条件。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`Condition`

### 3.3 FuncCondition

基于 `BooleanSupplier` 的函数条件。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`Condition`

### 3.4 ExpressionCondition

表达式条件，支持复杂布尔表达式求值。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`Condition`

**构造方法**：
```java
ExpressionCondition(String expression)
```

**变量替换**：使用 `${变量名}` 语法引用会话状态，支持嵌套路径。

**支持的运算符**：

| 类型 | 运算符 |
|------|--------|
| 逻辑运算 | `&&`, `\|\|`, `!` |
| 比较运算 | `==`, `!=`, `<`, `>`, `<=`, `>=` |
| 包含运算 | `in`, `not_in` |
| 算术运算 | `+`, `-`, `*`, `/` |

**支持的函数**：

| 函数 | 说明 |
|------|------|
| `length(value)` | 获取长度 |
| `is_empty(value)` | 判断是否为空 |
| `is_not_empty(value)` | 判断是否非空 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `evaluate(BaseSession session)` | `boolean` | 评估表达式 |
| `doInvoke(Object inputs, BaseSession session)` | `Object` | 执行表达式求值 |
| `traceInfo(BaseSession session)` | `Object` | 获取追踪信息 |

### 3.5 NumberCondition

基于迭代次数的循环条件。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`Condition`

### 3.6 NumberConditionInSession

迭代次数存储在会话中的循环条件。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`Condition`

### 3.7 ArrayCondition

基于数组项遍历的循环条件。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`Condition`

### 3.8 ArrayConditionInSession

数组数据源存储在会话中的循环条件。

**包路径**：`com.openjiuwen.core.workflow.condition`  
**继承**：`Condition`

---

## 4. 循环控制（loop）

### 4.1 LoopController（接口）

循环控制器接口。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `breakLoop()` | `void` | 中断循环 |
| `isBroken()` | `boolean` | 循环是否已中断 |

### 4.2 LoopType（枚举）

循环类型枚举。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

| 枚举值 | 说明 |
|--------|------|
| `ARRAY` | 数组遍历循环 |
| `NUMBER` | 固定次数循环 |
| `ALWAYS_TRUE` | 无限循环（直到 break） |
| `EXPRESSION` | 表达式条件循环 |

### 4.3 LoopInput

循环输入配置模型。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

**构造方法**：`LoopInput()`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `loopType` | `String` | `""` | 循环类型 |
| `loopNumber` | `Integer` | `0` | 循环次数 |
| `loopArray` | `Map<String, Object>` | `new HashMap<>()` | 循环数组 |
| `boolExpression` | `Object` | `""` | 布尔表达式 |
| `intermediateVar` | `Map<String, Object>` | `new HashMap<>()` | 中间变量 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `fromMap(Map<String, Object> map)` | `LoopInput` | 从 Map 构建（静态） |

### 4.4 AdvancedLoopComponentImpl

高级循环组件完整实现。

**包路径**：`com.openjiuwen.core.workflow.component.loop`  
**继承**：`Executable<Object, Object>`  
**实现**：`LoopController, AdvancedLoopComponent`

**常量**：

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `BROKEN` | `"_broken"` | 中断标记 |
| `FIRST_IN_LOOP` | `"_first_in_loop"` | 首次循环标记 |
| `CONDITION_NODE_ID` | `"condition"` | 条件节点 ID |
| `BODY_NODE_ID` | `"body"` | 循环体节点 ID |
| `POST_BODY_NODE_ID` | `"post_body"` | 后处理节点 ID |

**构造方法**：
```java
AdvancedLoopComponentImpl(Object body, Object conditionParam, 
                          List<? extends LoopBreakComponent> breakNodes, 
                          List<LoopCallback> callbacks)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `onInvoke(Object inputs, BaseSession session, Object... kwargs)` | `Object` | 执行循环（覆盖 Executable） |
| `breakLoop()` | `void` | 中断循环（LoopController 接口） |
| `isBroken()` | `boolean` | 是否已中断（LoopController 接口） |
| `graphInvoker()` | `boolean` | 返回 true（图调用者） |
| `skipTrace()` | `boolean` | 返回 true（跳过追踪） |
| `getBody()` | `HasDrawable` | 获取循环体（用于可视化） |
| `getBodyExecutable()` | `Executable<Object, Object>` | 获取循环体可执行对象 |

### 4.5 LoopComponentImpl

从运行时输入创建 AdvancedLoopComponentImpl 的工厂。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

### 4.6 LoopGroup

循环体组件组，包含一组组成循环体的组件。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

### 4.7 LoopBreakComponent

循环中断组件，执行时中断当前循环。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

### 4.8 LoopSetVariableComponent

循环变量设置组件，在循环的父级会话中设置变量。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

### 4.9 PostLoopBody

循环后处理，追踪每轮迭代的完成索引。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

### 4.10 EmptyExecutable

空操作占位节点。

**包路径**：`com.openjiuwen.core.workflow.component.loop`

---

## 5. 循环回调（loop/callback）

### 5.1 LoopCallback（抽象类）

循环回调基类，在循环生命周期不同阶段触发。

**包路径**：`com.openjiuwen.core.workflow.component.loop.callback`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `firstInLoop(BaseSession session)` | `void` | 首次进入循环 |
| `outLoop(BaseSession session)` | `void` | 退出循环 |
| `startRound(BaseSession session)` | `void` | 每轮开始 |
| `endRound(BaseSession session)` | `void` | 每轮结束 |

### 5.2 IntermediateLoopVarCallback

中间循环变量初始化回调。

**包路径**：`com.openjiuwen.core.workflow.component.loop.callback`  
**继承**：`LoopCallback`

在 `firstInLoop` 时初始化中间变量。

### 5.3 OutputCallback

输出收集回调，收集每轮结果并在退出循环时生成最终输出。

**包路径**：`com.openjiuwen.core.workflow.component.loop.callback`  
**继承**：`LoopCallback`

在 `endRound` 时收集结果，在 `outLoop` 时生成最终输出。
