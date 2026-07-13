---
name: workflow-guide
description: 工作流应用构建指南。基于 com.openjiuwen.core.workflow 包，指导用户从 0 构建、编排、执行单条工作流图，以及用 WorkflowAgent 托管多工作流。在用户构建工作流、用 Workflow/WorkflowCard/WorkflowSessions、注册 Start/End/LLMComponent/QuestionerComponent、用 addConnection/addConditionalConnection/addStreamConnection 连边、调用 invoke/stream、处理 INPUT_REQUIRED 交互输入、做 WorkflowAgent 多工作流跳转时主动应用。涉及关键词：workflow、工作流、WorkflowCard、WorkflowSessions、WorkflowOutput、invoke、stream、QuestionerComponent、LLMComponent、addConnection、INPUT_REQUIRED、WorkflowAgent、SubWorkflowComponentImpl。不适用于：agent team 装配（用 agent-team-guide）、单 ReAct agent、与 workflow 无关的讨论。
---

# 工作流应用构建指南

本 skill 指导基于 `com.openjiuwen.core.workflow` 包构建工作流应用。核心心智模型：**工作流 = 一张由节点和边组成的可执行图**，用 `invoke(...)` 批量执行或 `stream(...)` 流式执行。

## 核心心智模型

- `Workflow` 是"一张可执行的图"，不是 workflow 列表，不是意图路由器。
- `WorkflowAgent` 是应用层入口，托管多条 workflow 并做选择；`Workflow` 是单条图。
- 注册组件解决"节点是什么"；连边解决"节点按什么路径执行"。**两者缺一不可**。
- `invoke(...)` 看重完整结果；`stream(...)` 看重增量过程。
- `INPUT_REQUIRED` 不等于失败，是"等待补充输入"的中间态，复用同一 session 恢复执行。

## 关键概念速查

| 术语 | Java 类型 | 含义 |
| --- | --- | --- |
| 工作流 | `Workflow` | 面向用户的主工作流类，一张有向图 |
| 工作流卡片 | `WorkflowCard` | 元信息：id/name/version/description/inputParams |
| 起止节点 | `Start` / `End` | 入口透传输入 / 出口产出最终结果 |
| 业务节点 | `QuestionerComponent` / `LLMComponent` / `BranchComponent` / `IntentDetectionComponent` | 真正的业务逻辑 |
| 子工作流 | `SubWorkflowComponentImpl` | 把另一条 Workflow 作为节点调用 |
| 普通边 | `addConnection(...)` | batch 先后依赖 |
| 条件边 | `addConditionalConnection(...)` | 运行时决定下一节点（`BranchRouter` 或 `Function`） |
| 流式边 | `addStreamConnection(...)` | 传递增量数据 |
| 工作流会话 | `WorkflowSessions` / `WorkflowSessionApi` | 保存执行状态、共享中间结果、承接交互恢复 |
| 工作流输出 | `WorkflowOutput` | `invoke(...)` 返回容器，含 `result` + `state` |
| 执行状态 | `WorkflowExecutionState` | `COMPLETED` / `INPUT_REQUIRED` / `ERROR` |
| 流式块 | `WorkflowChunk` / `OutputSchema` | `stream(...)` 返回的增量数据块 |
| 交互输入 | `InteractiveInput` | `INPUT_REQUIRED` 后恢复执行时提交的输入 |
| 应用层入口 | `WorkflowAgent` | 托管多条 workflow，做意图识别和跳转 |

## 场景速查表

按任务场景直接定位本 skill 的对应小节：

| 场景 | 跳转 |
| --- | --- |
| 从 0 搭一条最小 workflow | "快速开始：最小工作流" |
| 搭一条流式 workflow | "快速开始：流式工作流" |
| 处理补问 / 交互输入 | "INPUT_REQUIRED 与交互恢复" |
| 选 invoke 还是 stream | "invoke vs stream" |
| 做条件分支 / 循环 | "条件边与路由" |
| 嵌套子工作流 | "子工作流" |
| 托管多条 workflow | "WorkflowAgent 多工作流跳转" |
| 选组件能力类型 | "组件能力类型（INVOKE/STREAM/COLLECT/TRANSFORM）" |
| 理解 schema 引用表达式 | "schema 引用语法" |
| 排查 workflow 不执行 | "踩坑 FAQ" |

## 快速开始：最小工作流

最小示例：`Start -> QuestionerComponent -> End`，演示补问 + 恢复执行。

```java
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.*;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;

// 1. 定义卡片
WorkflowCard card = WorkflowCard.builder()
        .id("transfer_flow")
        .name("转账服务")
        .version("1.0")
        .description("补齐转账金额并返回最终结果")
        .inputParams(Map.of("type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")))
        .build();

// 2. 创建 Workflow 并注册节点
Workflow workflow = new Workflow(card);
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);

QuestionerConfig qConfig = new QuestionerConfig();
qConfig.setModelClientConfig(clientConfig);
qConfig.setModelConfig(requestConfig);
qConfig.setQuestionContent("请补充转账金额");
qConfig.setExtractFieldsFromResponse(true);
qConfig.setFieldNames(List.of(FieldInfo.builder()
        .fieldName("amount").description("转账金额").required(true).build()));
qConfig.setWithChatHistory(false);
qConfig.setMaxResponse(10);
workflow.addWorkflowComp("questioner", new QuestionerComponent(qConfig),
        Map.of("query", "${start.query}"), null);

workflow.setEndComp("end",
        new End(Map.of("responseTemplate", "转账金额为 {{amount}}")),
        Map.of("amount", "${questioner.amount}"), null);

// 3. 连边（缺一不可）
workflow.addConnection("start", "questioner");
workflow.addConnection("questioner", "end");

// 4. 创建 session 并执行
WorkflowSessionApi session = WorkflowSessions.createWorkflowSession("conversation-001");
WorkflowOutput output = workflow.invoke(Map.of("query", "我要转账"), session, null);

// 5. 处理 INPUT_REQUIRED
if (WorkflowExecutionState.INPUT_REQUIRED.equals(output.getState())) {
    InteractiveInput reply = new InteractiveInput();
    reply.update("questioner", "2000元");
    output = workflow.invoke(reply, session, null);
}

if (WorkflowExecutionState.COMPLETED.equals(output.getState())) {
    System.out.println(output.getResult());
}
```

**关键提醒**：
- `${query}` 从 workflow 顶层输入读；`${start.query}` 从 Start 输出读；`${questioner.amount}` 从 questioner 输出读。
- `addWorkflowComp(...)` 只放节点，**不形成执行顺序**；顺序靠 `addConnection(...)`。
- 恢复执行必须**复用同一 session**，换 session 会丢失之前的状态。

## 快速开始：流式工作流

把 `LLMComponent` 的增量输出通过 `addStreamConnection(...)` 接到 `End`。

```java
LLMCompConfig llmConfig = new LLMCompConfig();
llmConfig.setModelClientConfig(clientConfig);
llmConfig.setModelConfig(requestConfig);
llmConfig.setTemplateContent(List.of(
        Map.of("role", "system", "content", "你是一个简洁的助手。"),
        Map.of("role", "user", "content", "{{query}}")));
llmConfig.setResponseFormat(Map.of("type", "text"));
llmConfig.setOutputConfig(Map.of("answer", Map.of("type", "string", "required", true)));

Workflow streamWorkflow = new Workflow();
streamWorkflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
streamWorkflow.addWorkflowComp("llm", new LLMComponent(llmConfig),
        Map.of("query", "${start.query}"), null);

// End 用 streaming 模式，重点是 streamInputsSchema
streamWorkflow.setEndComp("end",
        new End(Map.of("responseTemplate", "{{answer}}")),
        null, null,
        Map.of("answer", "${llm.answer}"), null,
        "streaming");

streamWorkflow.addConnection("start", "llm");
streamWorkflow.addStreamConnection("llm", "end");  // 流式边

// 执行：返回同步 Iterator
Iterator<WorkflowChunk> chunks = streamWorkflow.stream(
        Map.of("query", "介绍一下 Java 工作流"),
        WorkflowSessions.createWorkflowSession(),
        null,
        List.of(StreamMode.OUTPUT));

while (chunks.hasNext()) {
    WorkflowChunk chunk = chunks.next();
    if (chunk instanceof OutputSchema output) {
        System.out.println(output.getPayload());
    }
}
```

**关键提醒**：
- `End` 在流式模式下要声明 `streamInputsSchema`（第 5 个参数），而非 `inputsSchema`。
- `stream(...)` 返回的是**同步 Iterator**，不是异步流。
- `StreamMode.OUTPUT` 是最常用的订阅模式；还有 `TRACE` / `CUSTOM`。

## invoke vs stream

| 调用方式 | 适合场景 | 返回 |
| --- | --- | --- |
| `invoke(...)` | 一次拿到完整结果；含交互暂停和恢复 | `WorkflowOutput`（result + state） |
| `stream(...)` | 边执行边消费输出 | `Iterator<WorkflowChunk>` |

## INPUT_REQUIRED 与交互恢复

当 `QuestionerComponent` 需要补问时，workflow 返回 `INPUT_REQUIRED`：

1. 读取本次交互提示
2. 构造 `InteractiveInput`，`reply.update("组件id", "用户回答")`
3. **复用同一 session** 再次调用 `workflow.invoke(reply, session, null)`

```java
InteractiveInput reply = new InteractiveInput();
reply.update("questioner", "2000元");  // "questioner" 必须与注册时的组件 id 一致
WorkflowOutput resumed = workflow.invoke(reply, session, null);
```

**关键**：`reply.update` 的第一个参数是**组件 id**（如 `"questioner"`），不是节点类型名。换 session 会丢失之前积累的状态。

## 条件边与路由

用 `addConditionalConnection(...)` 实现运行时分支：

```java
// 方式 1：BranchRouter
workflow.addConditionalConnection("branch_node", new BranchRouter(...));

// 方式 2：Function 路由函数
workflow.addConditionalConnection("branch_node", (Function<Object, Object>) input -> {
    String intent = ((Map<String, String>) input).get("intent");
    return switch (intent) {
        case "transfer" -> "transfer_node";
        case "query" -> "query_node";
        default -> "default_node";
    };
});
```

适合：条件分支、动态跳转、循环或回跳场景。

## 子工作流

用 `SubWorkflowComponentImpl` 把另一条 `Workflow` 作为当前图中的一个节点：

```java
Workflow subWorkflow = new Workflow(subCard);
// ... 搭建子工作流

workflow.addWorkflowComp("sub", new SubWorkflowComponentImpl(subWorkflow),
        Map.of("input", "${start.input}"), null);
workflow.addConnection("start", "sub");
workflow.addConnection("sub", "end");
```

好处：复杂流程分层组织、公共子流程复用、可视化结构清晰。

## WorkflowAgent 多工作流跳转

`WorkflowAgent` 在应用层托管多条 workflow，按用户意图选择执行哪一条。

```java
WorkflowAgentConfig config = WorkflowAgentConfig.builder()
        .id("workflow_agent_java_example")
        .description("Java 多工作流金融助手示例")
        .model(modelConfig)
        .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
        .defaultResponse(DefaultResponse.builder().text(defaultText).build())
        .build();

WorkflowAgent agent = new WorkflowAgent(config);
agent.addWorkflows(List.of(transferWorkflow, investWorkflow, balanceWorkflow));
```

**`WorkflowEventHandler` 的意图识别优先级**：
1. 如果输入是带 node id 的 `InteractiveInput` → 直接恢复被打断的 workflow（不做 LLM 意图识别）
2. 如果只配置了一条 workflow → 直接到那条
3. 多条 workflow → LLM 意图识别选择
4. 都没命中 → 返回 `defaultResponse`

`defaultResponse` 很重要：意图识别没命中时返回默认回复，而不是强行承诺选对业务流。

## 组件能力类型（INVOKE/STREAM/COLLECT/TRANSFORM）

| 能力 | 输入 | 输出 | 对应方法 | 适合场景 |
| --- | --- | --- | --- | --- |
| `INVOKE` | batch | batch | `invoke(...)` | 普通节点：Start、Questioner、Tool |
| `STREAM` | batch | stream | `stream(...)` | LLM 逐块产出、ProducerNode |
| `COLLECT` | stream | batch | `collect(...)` | 汇总上游多帧、End 接流后返回 batch |
| `TRANSFORM` | stream | stream | `transform(...)` | 逐帧改写、过滤、补充字段 |

**边的搭配**：
- 上游 INVOKE → 下游 INVOKE：用 `addConnection`
- 上游 STREAM/TRANSFORM → 下游 TRANSFORM/COLLECT：用 `addStreamConnection`

## schema 引用语法

节点配置时用 `${...}` 引用其他节点的输出：

| 写法 | 含义 |
| --- | --- |
| `${query}` | 从 workflow 顶层输入读 `query` |
| `${start.query}` | 从 Start 节点输出读 `query` |
| `${questioner.amount}` | 从 questioner 节点输出读 `amount` |
| `{{amount}}` | End 的 `responseTemplate` 里渲染变量 |

**注意**：`inputsSchema` 只描述"从哪里取值"，**不形成执行顺序**；执行顺序靠 `addConnection(...)`。

## 踩坑 FAQ

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| workflow 跑不起来，节点没执行 | 只 `addWorkflowComp` 没 `addConnection` | 补 `addConnection(srcId, targetId)` |
| `INPUT_REQUIRED` 后恢复失败，状态丢失 | 换了新 session | 复用同一 `WorkflowSessionApi` |
| `reply.update("Questioner", ...)` 无效 | 组件 id 写错（大小写） | id 必须与 `addWorkflowComp("questioner", ...)` 一致 |
| 流式 `End` 收不到数据 | 用了 `inputsSchema` 而非 `streamInputsSchema` | 流式模式用第 5 个参数 `streamInputsSchema` |
| `stream(...)` 卡住不出数据 | 没传 `StreamMode.OUTPUT` | `stream(inputs, session, null, List.of(StreamMode.OUTPUT))` |
| 条件边路由返回的 target id 不存在 | router 返回值与注册 id 不匹配 | 检查 router 返回的字符串是否是已注册的组件 id |
| `End` 输出模板不渲染 | `responseTemplate` 用了未定义的变量 | 模板变量必须在 `inputsSchema` 里声明来源 |
| 多 workflow 跳转总走默认回复 | 意图识别没命中 | 检查 workflow 的 `description` 是否清晰；调 system prompt |

## 使用方式

1. **场景定位**：先看"场景速查表"按当前任务跳到对应小节。
2. **复制模板**：快速开始小节的代码块可直接复制修改。
3. **理解概念**：术语不清楚查"关键概念速查"或 Read `references/key_concepts.md`。
4. **组件细节**：需要组件配置细节时 Read `references/components_guide.md`。
5. **真实案例**：需要端到端参照时 Read `references/workflow_cases.md`（4 个案例：多工作流金融助手/流式问答/多字段补问/条件分支）。
6. **多工作流**：需要多 workflow 跳转时看"WorkflowAgent"小节。
7. **排查问题**：先查"踩坑 FAQ"，再查组件能力类型和 schema 语法。
8. **不确定不要编造**：API 细节以源码为准，本 skill 不替代正式 API 文档。

## 参考入口

- **关键概念详解**：`references/key_concepts.md`（Workflow/WorkflowCard/WorkflowSessions/WorkflowOutput/执行状态/流式块/InteractiveInput 深入说明）
- **组件配置指南**：`references/components_guide.md`（Start/End/QuestionerComponent/LLMComponent/BranchComponent 配置细节 + 4 种能力类型）
- **真实案例**：`references/workflow_cases.md`（4 个端到端案例：多工作流金融助手/流式问答/多字段补问/条件分支）
- **最小可运行示例**：`references/MinimalWorkflowExample.java`（自包含，含模型配置，复制即可跑）
- 完整文档：`documents/zh/2.开发指南/工作流/`（概述/关键概念/构建工作流/使用组件）
- 示例代码：`examples/workflow_agent/WorkflowAgentExampleSupport.java` + `examples/interact/WeatherAssistantInteractExampleSupport.java`
- API 文档：`documents/zh/API文档/com.openjiuwen.core/workflow/`
- agent team 指南：`resources/skills/agent-team-guide/SKILL.md`
