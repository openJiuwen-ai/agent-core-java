# WorkflowAgent支持多工作流跳转

Java 版 `WorkflowAgent` 的“多工作流跳转”不是单条 graph 内部的节点分支，而是：**同一个 agent 在同一会话里托管多条 workflow，并根据用户输入在它们之间选择、打断、恢复和继续执行。**

这项能力主要由两层组成：

1. `WorkflowAgent`：负责把多条 workflow 注册到 agent 与全局资源管理器；
2. `WorkflowEventHandler`：负责意图识别、保存中断任务、根据用户回复恢复对应 workflow。

本页以 `examples/workflow_agent` 为主线，解释 Java 当前已经可验证的多 workflow 选择与恢复路径，不把它写成一个超出仓库实现范围的“通用任务编排平台”。

## 先区分两种“跳转”

| 类型 | Java 落点 | 关注点 |
| --- | --- | --- |
| 多 workflow 跳转 | `WorkflowAgent` + `WorkflowEventHandler` | 在同一个 agent 下选择哪一条 workflow 运行 |
| 单 workflow 内节点跳转 | `Workflow.addConditionalConnection(...)` 等 | 一条 graph 内部节点之间如何分支 / 回跳 |

如果你的问题是“用户这句话应该进入转账流程、理财流程还是余额查询流程”，这是本页讨论的多 workflow 跳转。

如果你的问题是“在一条 workflow 里，满足某个条件后应该走哪个节点”，那应该回到 [工作流 / 构建工作流](../工作流/构建工作流.md) 和条件边文档。

## 示例里实际注册了什么

`examples/workflow_agent/WorkflowAgentExampleSupport.java` 当前注册了三条 workflow：

- `transfer_flow_multi`：转账服务
- `invest_flow_multi`：理财服务
- `balance_flow`：余额查询

三条 workflow 的结构都类似：

```text
start -> questioner -> end
```

差别在于：

- workflow 描述文本不同；
- `QuestionerComponent` 追问的字段不同；
- `End` 输出模板不同。

这正好适合作为多 workflow 路由示例：

- 当用户说“我要转账”，应该进入转账 workflow；
- 当用户说“我想买理财产品”，应该进入理财 workflow；
- 当用户说“帮我查一下余额”，应该进入余额 workflow。

## `WorkflowAgent.addWorkflows(...)` 做了什么

`WorkflowAgent.addWorkflows(...)` 不只是把 workflow 塞进一个列表。Java 当前实现里，它至少做了三件事：

1. 把 workflow card 加进 agent 的 ability manager；
2. 把 workflow 元信息写进 `WorkflowAgentConfig.workflows`；
3. 用 `WorkflowUtils.generateWorkflowKey(card.getId(), card.getVersion())` 生成版本化资源 ID，并把 workflow 注册到 `Runner.resourceMgr()`。

这意味着，后续 `WorkflowEventHandler` 能够：

- 从 agent 配置里拿到所有 workflow 的描述与输入 schema；
- 通过资源 ID 取回真正的 workflow 实例；
- 把“workflow 选择”和“workflow 执行”连接起来。

## 示例主线：先创建 agent，再注册 workflow

示意代码与 example 基本一致：

```java
WorkflowAgentConfig config = WorkflowAgentConfig.builder()
        .id("workflow_agent_java_example")
        .description("Java 多工作流金融助手示例")
        .model(modelConfig)
        .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
        .defaultResponse(DefaultResponse.builder().text(defaultText).build())
        .build();

WorkflowAgent agent = new WorkflowAgent(config);
agent.addWorkflows(List.of(
        transferWorkflow,
        investWorkflow,
        balanceWorkflow
));
```

这里的 `defaultResponse` 也很重要：当多 workflow 意图识别没有命中时，Java 当前实现优先返回默认回复，而不是强行承诺一定能选对业务流。

## 运行时到底怎么选 workflow

这部分由 `WorkflowEventHandler.intentDetection(...)` 负责。Java 当前可验证的优先级是：

### 1. 如果输入是带 node id 的 `InteractiveInput`

`WorkflowEventHandler` 会先尝试根据 `InteractiveInput.userInputs` 里的 node id，直接找回之前被打断的 workflow。

这一步的含义是：

- 用户这次不是在发起一个全新业务；
- 而是在回答上一个 workflow 的补充问题；
- 因此无需再次做 LLM 意图识别，而是应直接恢复那条 workflow。

### 2. 如果只配置了一条 workflow

那就直接使用这条 workflow，不做分类。

### 3. 如果配置了多条 workflow

这时才进入 LLM 意图识别，根据 workflow 描述选择最合适的业务流。

### 4. 如果没识别到明确结果

- 若配置了 `defaultResponse`，就返回默认回复；
- 否则退回第一条 workflow。

这也是为什么页面和示例都要强调：workflow 描述文本要写清楚业务边界，不能只写一个很空泛的名字。

## 中断后怎么保存“待恢复任务”

当某条 workflow 进入 `QuestionerComponent` 等待输入时，`WorkflowEventHandler.execTask(...)` 会调用 `interruptTask(...)`，把中断信息写进 agent session state 的：

```text
workflow_controller.interrupted_tasks
```

当前保存的内容至少包括：

- task 基本信息
- `component_id`
- `last_interaction_value`

这里的 `component_id` 很关键，因为下一次用户回复时，事件处理器就是靠它把回答路由回正确的 workflow。

## 结合示例看完整交互链路

`examples/workflow_agent/WorkflowAgentExampleSupport.java` 的命令行示例使用同一个 `conversationId` 贯穿全程：

### 第一步：用户发起新请求

```java
Runner.runAgentStreaming(
        agent,
        Map.of("query", userInput, "conversation_id", conversationId),
        null,
        null,
        List.of(StreamMode.OUTPUT)
);
```

运行结果可能是：

```text
user> 我要转账
assistant> 请补充转账金额，必须是数字或带货币单位的金额描述。
```

### 第二步：用户回复补充信息

命令行示例会记住这次中断对应的 `nodeId`，然后构造：

```java
InteractiveInput reply = new InteractiveInput();
reply.update(nodeId, "2000元");
```

并继续调用：

```java
Runner.runAgentStreaming(
        agent,
        Map.of("query", reply, "conversation_id", conversationId),
        null,
        null,
        List.of(StreamMode.OUTPUT)
);
```

于是同一 workflow 会继续完成：

```text
reply> 2000元
assistant> 转账服务完成，记录的转账金额为 2000元。
```

### 第三步：继续同一会话处理下一类业务

只要仍然复用同一个 `conversation_id`，agent session 就不会丢失。后续再输入：

```text
user> 我想买理财产品
assistant> 请补充理财产品名称，例如稳健理财、现金管理类产品。
reply> 稳健理财
assistant> 理财服务完成，选择的理财产品为 稳健理财。
```

或：

```text
user> 帮我查一下余额
assistant> 请补充需要查询余额的账户号码。
reply> 62220001
assistant> 余额查询完成，登记的账户号码为 62220001。
```

这正是示例 README 已实际跑通的三条主业务流。

## 为什么这里强调 `conversation_id`

对 `WorkflowAgent` 来说，`conversation_id` 有两层作用：

1. `RunnerImpl` 会优先用它作为 agent session id；
2. `WorkflowAgent.createManagedSession(...)` 也会优先从输入里取它，缺失时才回退到 `default_session`。

所以如果你想保住：

- workflow 中断任务
- agent 多轮上下文
- `workflow_controller.interrupted_tasks` 里的恢复信息

就必须继续复用同一个 `conversation_id`。

## 这和原生 workflow 的恢复有什么关系

可以把多 workflow 跳转理解成在原生 workflow 恢复语义外面又包了一层 agent 控制器：

```text
Runner / WorkflowAgent
  -> WorkflowEventHandler 选择 workflow
  -> 具体 Workflow 执行
  -> workflow 因交互而中断
  -> EventHandler 把中断任务记录到 agent session
  -> 用户回复后再由 EventHandler 找回正确 workflow
```

也就是说：

- workflow 内部仍然使用 `InteractiveInput`、`InteractionOutput`、checkpointer 这些基础机制；
- `WorkflowAgent` 做的是“多条 workflow 之间的选择与恢复调度”。

## 当前 Java 示例真正验证了什么

基于当前 example、源码和 API 文档，可以确认以下事实：

- `WorkflowAgent` 可以注册多条 workflow；
- Java 侧用 workflow 描述文本进行 LLM 意图路由；
- `QuestionerComponent` 中断后，可以在同一会话中继续执行；
- 恢复时通过 `InteractiveInput.update(nodeId, userInput)` 把回答送回对应 workflow；
- 示例命令行会在结束时调用 `Runner.release(conversationId)` 清掉会话状态。

## 当前 Java 能力边界

为了避免写出超出实现范围的承诺，这里明确几点：

- 本页讨论的是 **`WorkflowAgent` 管理多条顶层 workflow**，不是单条 workflow 内部的任意节点跳转。
- `examples/workflow_agent` 的命令行界面重点展示“多 workflow 注册 + 单轮补问恢复”主线；更细粒度的多中断并存行为主要由 `WorkflowEventHandler` 的状态管理逻辑保证。
- Java 当前流式路径里，交互回放存在边界：`stream` 路径只保证当前需要展示的交互能返回给调用方，不应承诺复杂中断集合一定完整重放。
- `QuestionerComponent` 触发时底层可能打印 `GraphInterrupt` 的 `ERROR` 日志；按照示例 README，这属于正常等待输入行为，不代表示例失败。

## 参考入口

- [工作流 / 构建工作流](../工作流/构建工作流.md)
- [高阶用法 / 人机交互](人机交互.md)
- [高阶用法 / Checkpointer检查点机制](Checkpointer检查点机制.md)
- [API 文档：WorkflowAgent](../API文档/com.openjiuwen.core/application/workflow/WorkflowAgent.md)
- [API 文档：WorkflowEventHandler](../API文档/com.openjiuwen.core/application/workflow/WorkflowEventHandler.md)
- [API 文档：WorkflowController](../API文档/com.openjiuwen.core/application/workflow/WorkflowController.md)
- [示例：workflow_agent](../../../../examples/workflow_agent/README.md)
- [示例：workflow_agent/multi_workflow_agent_demo](../../../../examples/workflow_agent/multi_workflow_agent_demo/README.md)
