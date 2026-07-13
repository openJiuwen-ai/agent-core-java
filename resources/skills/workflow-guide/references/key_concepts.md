# 工作流关键概念详解

本文件补充 SKILL.md 的"关键概念速查"，给每个概念更深入的解释。用户问"某概念什么意思"时按需读取。

## 能力分层心智模型

理解 Java 工作流按四层建立：

1. **图定义层**：`Workflow` / `WorkflowCard` —— 这张图是什么
2. **组件编排层**：`Start` / `End` / 业务节点 / 边 —— 图里有哪些节点，怎么连
3. **执行与会话层**：`WorkflowSessions` / `WorkflowOutput` / `WorkflowExecutionState` / `WorkflowChunk` —— 这次执行怎么跑、状态怎么带
4. **应用集成层**：`WorkflowAgent` / `Runner` —— 怎么放进更完整的应用

只搭单条 workflow，前三层够了；要做多 workflow 入口才需要第四层。

## Workflow

`Workflow` 是中心对象，同时承担三件事：

1. **构图**：注册节点、声明输入输出 schema、连边
2. **执行**：`invoke(...)` 批量 / `stream(...)` 增量
3. **兼容封装**：多组重载，兼容不同参数顺序、不同 schema 写法、legacy 接入

理解成"一张可执行的图"。不是 workflow 列表，不是意图路由器——这些应用层职责在 `WorkflowAgent`。

## WorkflowCard

工作流的身份卡片，最常见信息：`id` / `name` / `version` / `description` / `inputParams`。

不只是"写给人看"：
- `Workflow` 自身持有这张卡片
- `WorkflowAgent.addWorkflows(...)` 注册时读 `WorkflowCard` 的描述信息
- `inputParams` 是这条 workflow 对外暴露的输入 schema

`WorkflowCard` 代表"这条 workflow 是谁、接收什么输入"，不是执行期动态状态。

## 节点 / 组件

工作流图中的每个节点都是一个"组件"，分三类：

### 起止节点
- `Start`：入口节点，按原样透传输入
- `End`：结束节点，产出最终结果；可返回 batch 结果，也可参与 `stream` / `transform` / `collect`

### 业务节点
- `QuestionerComponent`：补问缺失字段，触发 `INPUT_REQUIRED`
- `LLMComponent`：调用大模型，支持流式输出
- `BranchComponent`：条件分支
- `IntentDetectionComponent`：意图识别
- `SubWorkflowComponentImpl`：子工作流

### 自定义节点
基于 `WorkflowComponent` 或 `ComponentComposable` 扩展。

## 边：普通边 / 条件边 / 流式边

注册组件只是放节点；让图跑起来要靠边定义执行关系。

### 普通边 `addConnection(...)`
前后依赖：前一个执行完，后一个再执行。适合：线性流程、并行分支后汇聚、batch 输入输出传递。

### 条件边 `addConditionalConnection(...)`
下一节点运行时决定。支持两类路由输入：
- `BranchRouter`
- `Function<Object, Object>` 路由函数

适合：条件分支、动态跳转、循环或回跳。

### 流式边 `addStreamConnection(...)`
在支持流能力的节点之间传递增量输出。典型搭配：
- 上游 `STREAM` 或 `TRANSFORM`
- 下游 `TRANSFORM` 或 `COLLECT`

最常见场景：`LLMComponent` 逐块产出 → `End` 以 `responseMode = "streaming"` 输出。

**注意**：条件路由不额外对应一个 `ConnectionType` 枚举值，是通过 `addConditionalConnection(...)` + router 表达。

## WorkflowSessions 与会话

`WorkflowSessions` 是 workflow 包级的会话创建门面，最常用入口：
- `WorkflowSessions.createWorkflowSession()`
- `WorkflowSessions.createWorkflowSession(String sessionId)`

返回 `WorkflowSessionApi`，承担：
- 保存本次执行的状态
- 节点之间共享中间结果
- 承接交互输入恢复
- 管理流式输出、trace 等执行期能力

**关键**：workflow 一次执行被中断后要继续同一条流程，必须复用同一个 session。

## WorkflowOutput

`invoke(...)` 的返回容器，含两个字段：
- `result`：执行结果
- `state`：`WorkflowExecutionState`

读取方式：
```java
WorkflowOutput output = workflow.invoke(inputs, session, null);
Object result = output.getResult();
WorkflowExecutionState state = output.getState();
```

典型返回形态：
- `state == COMPLETED`：`result` 是 `End` 整理后的业务输出
- `state == INPUT_REQUIRED`：`result` 是交互输出块集合，调用方需继续提交 `InteractiveInput`
- `state == ERROR`：执行失败

## WorkflowExecutionState

| 状态 | 含义 | 调用方怎么处理 |
| --- | --- | --- |
| `COMPLETED` | 正常完成 | 读 `WorkflowOutput.getResult()` |
| `INPUT_REQUIRED` | 需要更多用户输入 | 读交互提示，构造 `InteractiveInput` 后继续调 `invoke` |
| `ERROR` | 执行失败 | 记录异常或向上抛 |

`INPUT_REQUIRED` 不是失败，是"等待补充输入"的中间态。

## 流式输出：WorkflowChunk / OutputSchema / StreamMode

Java 工作流的流式执行用**同步 `Iterator<WorkflowChunk>`**。

### WorkflowChunk
流式块的顶层别名接口。调用方最常接收的具体类型是 `OutputSchema`。

### OutputSchema
标准输出块，含：
- `type`
- `index`
- `payload`

调用方可一边遍历 iterator，一边按块刷新界面或日志。

### StreamMode
`workflow.stream(...)` 通过 `List<StreamMode>` 指定订阅哪些流：
- `StreamMode.OUTPUT`（入门最常用）
- `StreamMode.TRACE`
- `StreamMode.CUSTOM`

**注意**：`StreamMode` 表示"想订阅哪类流"；`WorkflowChunkType` 是对块类别的顶层命名。两者相关但不是同一层面。

## InteractiveInput

`QuestionerComponent` 要求补充信息时，workflow 返回 `INPUT_REQUIRED`。调用方：

1. 读取本次交互提示
2. 构造 `InteractiveInput`
3. 用**相同 session** 再次调用 `workflow.invoke(...)`

```java
InteractiveInput reply = new InteractiveInput();
reply.update("questioner", "2000元");  // "questioner" = 组件 id
WorkflowOutput resumed = workflow.invoke(reply, session, null);
```

`"questioner"` 必须与 workflow 中实际注册的组件 id 一致。

## SubWorkflowComponentImpl

把另一条 `Workflow` 包装成当前图中的一个节点：
- 复杂流程分层组织
- 公共子流程复用
- 可视化和结构更清晰

## 概念串联：一次典型执行

1. 用 `WorkflowCard` 描述 workflow 身份和输入
2. 用 `Workflow` 注册 `Start`、业务节点、`End`
3. 用普通边、条件边或流式边把节点连成图
4. 用 `WorkflowSessions` 创建本次执行会话
5. 调 `invoke(...)` 得 `WorkflowOutput`，或 `stream(...)` 得 `Iterator<WorkflowChunk>`
6. 如果 `INPUT_REQUIRED`，用 `InteractiveInput` 在同一会话中恢复执行

## 术语边界提醒

- `Workflow` ≠ `WorkflowAgent`：前者单条图，后者应用层入口
- `WorkflowCard` ≠ session：前者静态描述，后者运行时状态
- `stream(...)` 的块输出 ≠ `invoke(...)` 的最终结果：前者增量过程，后者执行收束后的返回包
