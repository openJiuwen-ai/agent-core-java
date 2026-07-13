# 工作流真实案例

本文件给 4 个端到端案例，每个含"场景 → 工作流图 → 关键代码 → 执行流程 → 注意点"。用户问"给我一个工作流案例"或"某类工作流怎么搭"时按需读取。

## 案例 1：金融助手多工作流（WorkflowAgent + 意图跳转）

**对应**：`examples/workflow_agent/WorkflowAgentExampleSupport.java`

### 场景

金融助手托管 3 条 workflow：转账 / 理财 / 余额查询。用户说"我要转账"进转账流程，说"查余额"进余额流程。每条 workflow 都有补问环节（金额/产品/账号缺失时追问）。

### 工作流图

```
用户输入
   │
   ▼
WorkflowAgent（意图识别）
   ├── "转账" ──► transfer_flow_multi: Start → Questioner(amount) → End
   ├── "理财" ──► invest_flow_multi:    Start → Questioner(product) → End
   └── "查余额" ─► balance_flow:        Start → Questioner(account) → End
   └── 其他 ──► defaultResponse
```

### 关键代码

**创建 agent + 注册 3 条 workflow**：

```java
WorkflowAgentConfig config = WorkflowAgentConfig.builder()
        .id("workflow_agent_java_example")
        .description("Java 多工作流金融助手示例")
        .model(modelConfig)
        .promptTemplate(List.of(Map.of("role", "system", "content",
            "你是一个金融业务助手。当用户提出转账、理财或余额查询需求时，"
            + "必须选择最合适的工作流处理。如果信息不完整，就通过工作流里的"
            + "提问节点补齐缺失字段。如果用户需求不属于这三类业务，"
            + "就直接返回默认回复。"))
        .defaultResponse(DefaultResponse.builder()
            .text("我目前只支持转账、理财和余额查询三类金融流程，请明确说明你的需求。")
            .build())
        .build();

WorkflowAgent agent = new WorkflowAgent(config);
agent.addWorkflows(List.of(
        buildFinancialWorkflow("transfer_flow_multi", "转账服务",
                "处理用户转账、汇款、打款请求...", "amount",
                "转账金额", "转账服务完成，金额为 {{amount}}。"),
        buildFinancialWorkflow("invest_flow_multi", "理财服务",
                "处理理财、投资请求...", "product",
                "理财产品名称", "理财服务完成，产品为 {{product}}。"),
        buildFinancialWorkflow("balance_flow", "余额查询",
                "处理账户余额查询请求...", "account",
                "账户号码", "余额查询完成，账号为 {{account}}。")
));
```

**每条 workflow 的构建**（用辅助方法复用）：

```java
private static Workflow buildFinancialWorkflow(String id, String name,
        String desc, String fieldName, String fieldDesc, String template) {
    WorkflowCard card = WorkflowCard.builder()
            .id(id).name(name).version("1.0").description(desc)
            .inputParams(defaultInputSchema()).build();

    QuestionerConfig qConfig = new QuestionerConfig();
    qConfig.setModelClientConfig(clientConfig);
    qConfig.setModelConfig(requestConfig);
    qConfig.setQuestionContent("请补充" + fieldDesc);
    qConfig.setExtractFieldsFromResponse(true);
    qConfig.setFieldNames(List.of(FieldInfo.builder()
            .fieldName(fieldName).description(fieldDesc).required(true).build()));
    qConfig.setWithChatHistory(false);
    qConfig.setMaxResponse(10);

    Workflow wf = new Workflow(card);
    wf.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
    wf.addWorkflowComp("questioner", new QuestionerComponent(qConfig),
            Map.of("query", "${start.query}"), null);
    wf.setEndComp("end", new End(Map.of("responseTemplate", template)),
            Map.of(fieldName, "${questioner." + fieldName + "}"), null);
    wf.addConnection("start", "questioner");
    wf.addConnection("questioner", "end");
    return wf;
}
```

### 执行流程

1. 用户输入"我要转账 2000 元"
2. `WorkflowEventHandler.intentDetection(...)` 做意图识别 → 命中 `transfer_flow_multi`
3. 执行 transfer workflow：Start → Questioner（amount 已在输入里，无需补问）→ End
4. 返回 `COMPLETED`，输出"转账服务完成，金额为 2000 元"

如果用户只说"我要转账"（没金额）：
1. 意图识别命中 transfer workflow
2. Start → Questioner（amount 缺失）→ 返回 `INPUT_REQUIRED`
3. 用户回答"2000 元" → `InteractiveInput.update("questioner", "2000元")`
4. 同一 session 恢复执行 → Questioner 提取 amount → End → `COMPLETED`

### 注意点

- **workflow 的 description 要写清楚**：意图识别靠 description 匹配，描述模糊会走 defaultResponse
- **defaultResponse 必须设置**：意图没命中时返回默认回复，而不是强行选错 workflow
- **恢复时复用同一 session**：换 session 会丢失补问上下文
- **`reply.update` 的组件 id 必须是 `"questioner"`**：与注册时的 id 一致

---

## 案例 2：流式问答助手（LLMComponent + End streaming）

**对应**：`examples/interact/WeatherAssistantInteractExampleSupport.java` 的简化版

### 场景

用户问问题，LLM 流式输出回答，前端边输出边显示。

### 工作流图

```
Start ──batch──► LLMComponent ──stream──► End(streaming)
                    │                          │
                    │ LLM 逐块产出              │ End 逐块转发
                    ▼                          ▼
               Iterator<WorkflowChunk> ← 调用方消费
```

### 关键代码

```java
LLMCompConfig llmConfig = new LLMCompConfig();
llmConfig.setModelClientConfig(clientConfig);
llmConfig.setModelConfig(requestConfig);
llmConfig.setTemplateContent(List.of(
        Map.of("role", "system", "content", "你是一个简洁的助手。"),
        Map.of("role", "user", "content", "{{query}}")  // {{}} 渲染
));
llmConfig.setResponseFormat(Map.of("type", "text"));
llmConfig.setOutputConfig(Map.of(
        "answer", Map.of("type", "string", "description", "模型回答", "required", true)
));

Workflow workflow = new Workflow();
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
workflow.addWorkflowComp("llm", new LLMComponent(llmConfig),
        Map.of("query", "${start.query}"), null);

// End 用 streaming 模式，第 5 个参数是 streamInputsSchema
workflow.setEndComp("end",
        new End(Map.of("responseTemplate", "{{answer}}")),
        null,                                    // inputsSchema（batch，这里不用）
        null,                                    // outputsSchema
        Map.of("answer", "${llm.answer}"),       // streamInputsSchema（流式）
        null,
        "streaming");                            // responseMode

// 连边：start→llm 用普通边，llm→end 用流式边
workflow.addConnection("start", "llm");
workflow.addStreamConnection("llm", "end");

// 执行
Iterator<WorkflowChunk> chunks = workflow.stream(
        Map.of("query", "介绍一下 Java 工作流"),
        WorkflowSessions.createWorkflowSession(),
        null,
        List.of(StreamMode.OUTPUT));

while (chunks.hasNext()) {
    WorkflowChunk chunk = chunks.next();
    if (chunk instanceof OutputSchema output) {
        System.out.print(output.getPayload());  // 边输出边打印
    }
}
```

### 执行流程

1. `stream(...)` 返回同步 Iterator
2. LLMComponent 逐块产出 token/chunk
3. 流式边把 chunk 传到 End
4. End 以 streaming 模式逐块输出
5. 调用方遍历 Iterator，按块消费

### 注意点

- **End 流式模式必须用 `streamInputsSchema`**（第 5 个参数），不是 `inputsSchema`（第 3 个）
- **`addStreamConnection` 不能少**：只 `addConnection` 不会让流式数据流动
- **`StreamMode.OUTPUT` 必须传**：不传或传错会拿不到数据
- **Iterator 是同步的**：不是异步流，`hasNext()` 会阻塞等下一块

---

## 案例 3：带补问的表单填写（多字段 QuestionerComponent）

### 场景

用户要办信用卡，需要填姓名、身份证、手机号、邮箱 4 个字段。用户可能只说了部分，QuestionerComponent 补问缺失字段。

### 工作流图

```
Start ──► Questioner(多字段) ──► End
              │
              │ 字段缺失 → INPUT_REQUIRED
              │ 字段齐全 → 继续执行
              ▼
         用户补答 → 同 session 恢复
```

### 关键代码

```java
QuestionerConfig config = new QuestionerConfig();
config.setModelClientConfig(clientConfig);
config.setModelConfig(requestConfig);
config.setQuestionContent("请补充以下信息");
config.setExtractFieldsFromResponse(true);
config.setFieldNames(List.of(
    FieldInfo.builder().fieldName("name")
        .description("姓名").required(true).build(),
    FieldInfo.builder().fieldName("idCard")
        .description("身份证号").required(true).build(),
    FieldInfo.builder().fieldName("phone")
        .description("手机号").required(true).build(),
    FieldInfo.builder().fieldName("email")
        .description("邮箱").required(false).build()  // 选填
));
config.setWithChatHistory(true);   // 多轮补问要带历史
config.setMaxResponse(10);         // 最多补问 10 轮

Workflow workflow = new Workflow(card);
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
workflow.addWorkflowComp("questioner", new QuestionerComponent(config),
        Map.of("query", "${start.query}"), null);
workflow.setEndComp("end",
        new End(Map.of("responseTemplate",
            "信用卡申请提交：{{name}} / {{idCard}} / {{phone}} / {{email}}")),
        Map.of(
            "name", "${questioner.name}",
            "idCard", "${questioner.idCard}",
            "phone", "${questioner.phone}",
            "email", "${questioner.email}"
        ),
        null);
workflow.addConnection("start", "questioner");
workflow.addConnection("questioner", "end");
```

### 执行流程

1. 用户："我要办信用卡，我叫张三"
2. Start → Questioner：提取到 name=张三，但 idCard/phone/email 缺失
3. 返回 `INPUT_REQUIRED`，提示用户补身份证号
4. 用户："110101199001011234"
5. 同 session 恢复 → Questioner：提取 idCard，phone/email 还缺
6. 返回 `INPUT_REQUIRED`，提示补手机号
7. 用户："13800138000"
8. 恢复 → 提取 phone，email 是选填 → 字段齐全
9. → End → `COMPLETED`，输出"信用卡申请提交：张三 / 110101... / 138... / null"

### 注意点

- **`setWithChatHistory(true)`**：多轮补问必须带聊天历史，否则 Questioner 不知道之前问过什么
- **`setMaxResponse(10)`**：限制最大补问轮数，防止死循环
- **选填字段（`required=false`）**：缺失不触发补问，但 End 模板里 `{{email}}` 会渲染成 null
- **每次 `InteractiveInput.update` 只答一个问题**：多字段补问也是一次补一个，Questioner 自己决定下一个问什么

---

## 案例 4：条件分支工作流（BranchComponent + 多分支路由）

### 场景

客服机器人：用户说"退款"走退款流程，说"咨询"走咨询流程，说"投诉"走投诉流程。

### 工作流图

```
                Start
                  │
                  ▼
          IntentDetection
                  │
            (条件边路由)
              ┌───┼───┐
              ▼   ▼   ▼
          退款  咨询  投诉
          节点  节点  节点
              └───┼───┘
                  ▼
                 End
```

### 关键代码

```java
Workflow workflow = new Workflow(card);
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);

// 意图识别节点
IntentDetectionConfig intentConfig = new IntentDetectionConfig();
intentConfig.setModelClientConfig(clientConfig);
intentConfig.setModelConfig(requestConfig);
intentConfig.setIntents(List.of(
    IntentInfo.builder().name("refund").description("退款、退货、退钱").build(),
    IntentInfo.builder().name("consult").description("咨询、询问、了解").build(),
    IntentInfo.builder().name("complain").description("投诉、举报、不满").build()
));
workflow.addWorkflowComp("intent", new IntentDetectionComponent(intentConfig),
        Map.of("query", "${start.query}"), null);

// 三个业务节点
workflow.addWorkflowComp("refund", new RefundComponent(),
        Map.of("query", "${start.query}"), null);
workflow.addWorkflowComp("consult", new ConsultComponent(),
        Map.of("query", "${start.query}"), null);
workflow.addWorkflowComp("complain", new ComplainComponent(),
        Map.of("query", "${start.query}"), null);

// End
workflow.setEndComp("end", new End(Map.of("responseTemplate", "{{result}}")),
        Map.of("result", "${refund.result}",  // 三个节点都映射到 result
                      "result", "${consult.result}",
                      "result", "${complain.result}"),
        null);

// 连边：start → intent 用普通边
workflow.addConnection("start", "intent");

// intent → 三个分支用条件边（Function 路由）
workflow.addConditionalConnection("intent", (Function<Object, Object>) input -> {
    String intentName = ((Map<String, String>) input).get("intentName");
    return switch (intentName) {
        case "refund" -> "refund";
        case "consult" -> "consult";
        case "complain" -> "complain";
        default -> "consult";  // 兜底
    };
});

// 三个分支 → end 用普通边
workflow.addConnection("refund", "end");
workflow.addConnection("consult", "end");
workflow.addConnection("complain", "end");
```

### 执行流程

1. 用户："我要退款"
2. Start → IntentDetection：识别 intent=refund
3. 条件边路由：intent=refund → 走 refund 节点
4. RefundComponent 执行退款逻辑
5. → End → `COMPLETED`

### 注意点

- **router 返回值必须是已注册的组件 id**：返回 `"refund"` 必须有 `addWorkflowComp("refund", ...)`
- **兜底分支必须有**：意图识别可能不命中，default 走一个安全分支
- **多个分支汇聚到 End**：End 的 inputsSchema 要把三个节点的 result 都映射到 `result` 字段
- **条件边不对应 `ConnectionType`**：是 `addConditionalConnection` + router 表达，不是单独的边类型

## 案例通用流程

不管哪种工作流，搭建流程大体一致：

1. **定义卡片**：`WorkflowCard` 描述身份和输入 schema
2. **注册节点**：`setStartComp` / `addWorkflowComp` / `setEndComp`
3. **配 schema**：每个节点用 `${...}` 声明从哪里取值
4. **连边**：`addConnection`（普通）/ `addStreamConnection`（流式）/ `addConditionalConnection`（条件）
5. **创建 session**：`WorkflowSessions.createWorkflowSession(...)`
6. **执行**：`invoke(...)` 批量或 `stream(...)` 流式
7. **处理状态**：`COMPLETED` 读结果；`INPUT_REQUIRED` 补答后同 session 恢复；`ERROR` 处理异常

每次 `invoke` 或 `stream` 后都要检查 `WorkflowExecutionState`，决定下一步动作。
