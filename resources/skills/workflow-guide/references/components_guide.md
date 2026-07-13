# 组件配置指南

本文件补充 SKILL.md 的组件部分，给每个内置组件的配置细节和 4 种能力类型的深入说明。用户问"某组件怎么配置"时按需读取。

## 组件能力类型（ComponentAbility）

四种能力本质上是四种输入输出形态：

| 能力 | 输入 | 输出 | 对应方法 | 适合场景 |
| --- | --- | --- | --- | --- |
| `INVOKE` | batch | batch | `invoke(...)` | 普通节点：Start、Questioner、Tool |
| `STREAM` | batch | stream | `stream(...)` | LLM 逐块产出、ProducerNode |
| `COLLECT` | stream | batch | `collect(...)` | 汇总上游多帧、End 接流后返回 batch |
| `TRANSFORM` | stream | stream | `transform(...)` | 逐帧改写、过滤、补充字段 |

判断标准就是输入和输出是否是流。没有额外的"半流式"或"异步批处理"能力名词。

### INVOKE
最普通的 batch 节点：拿完整输入，执行一次，返回完整输出。
- `Start` 透传顶层输入
- `QuestionerComponent` 读输入返回字段或中断请求
- `ToolComponent` 执行工具调用
- 大多数只需一次性返回结果的自定义节点

### STREAM
接收完整输入，逐块产生输出帧。
- 上游通过 `addConnection(...)` 把 batch 送进来
- 下游通过 `addStreamConnection(...)` 消费流
- 大模型节点持续推 token/chunk

### COLLECT
和 STREAM 相反：吃流，吐一次性结果。
- 把上游多帧汇总成最终值
- 把多个 stream chunk 聚合成字符串、数组或统计
- End 节点接收流后统一返回 batch

### TRANSFORM
消费流，继续输出流。适合逐帧改写、过滤、补充字段或重新包装。

### 边的搭配规则
- 上游 INVOKE → 下游 INVOKE：`addConnection`
- 上游 STREAM/TRANSFORM → 下游 TRANSFORM/COLLECT：`addStreamConnection`
- 条件分支：`addConditionalConnection` + router

## Start 组件

入口节点，按原样透传输入。

```java
workflow.setStartComp(
    "start",                              // 组件 id
    new Start(),                           // 组件实例
    Map.of("query", "${query}"),           // inputsSchema：从顶层输入读
    null                                   // outputsSchema（可选）
);
```

**schema 引用**：
- `${query}` 从 workflow 顶层输入读
- 后续节点读 Start 输出用 `${start.query}`

## End 组件

结束节点，产出最终结果。两种模式：

### batch 模式（默认）
```java
workflow.setEndComp(
    "end",
    new End(Map.of("responseTemplate", "转账金额为 {{amount}}")),
    Map.of("amount", "${questioner.amount}"),  // inputsSchema
    null
);
```

### streaming 模式
```java
workflow.setEndComp(
    "end",
    new End(Map.of("responseTemplate", "{{answer}}")),
    null,                                        // inputsSchema（batch）
    null,                                        // outputsSchema
    Map.of("answer", "${llm.answer}"),           // streamInputsSchema（流式）
    null,
    "streaming"                                  // responseMode
);
```

**关键**：
- batch 模式用第 3 个参数 `inputsSchema`
- streaming 模式用第 5 个参数 `streamInputsSchema`
- `responseTemplate` 用 `{{var}}` 渲染，变量必须在上游 schema 里声明来源

## QuestionerComponent

用于"字段可能缺失，需要运行时补问"的场景。

```java
QuestionerConfig config = new QuestionerConfig();
config.setModelClientConfig(clientConfig);        // 大模型客户端配置
config.setModelConfig(requestConfig);             // 请求参数配置
config.setQuestionContent("请补充转账金额");        // 补问提示文本
config.setExtractFieldsFromResponse(true);        // 从模型回复提取字段
config.setFieldNames(List.of(                     // 要提取的字段
    FieldInfo.builder()
        .fieldName("amount")
        .description("转账金额")
        .required(true)
        .build()
));
config.setWithChatHistory(false);                  // 是否带聊天历史
config.setMaxResponse(10);                         // 最大回复轮数
```

**触发 `INPUT_REQUIRED`**：当字段缺失时，workflow 返回 `INPUT_REQUIRED`，调用方构造 `InteractiveInput` 恢复。

**恢复时**：
```java
InteractiveInput reply = new InteractiveInput();
reply.update("questioner", "2000元");  // 组件 id 必须是 "questioner"
```

## LLMComponent

调用大模型，支持流式输出。

```java
LLMCompConfig config = new LLMCompConfig();
config.setModelClientConfig(clientConfig);
config.setModelConfig(requestConfig);

// 模板内容（支持多轮）
config.setTemplateContent(List.of(
    Map.of("role", "system", "content", "你是一个简洁的助手。"),
    Map.of("role", "user", "content", "{{query}}")  // {{}} 渲染变量
));

// 响应格式
config.setResponseFormat(Map.of("type", "text"));

// 输出 schema：声明 LLM 输出哪些字段
config.setOutputConfig(Map.of(
    "answer", Map.of(
        "type", "string",
        "description", "模型回答",
        "required", true
    )
));
```

**流式输出**：LLMComponent 默认具备 `STREAM` 能力，用 `addStreamConnection(...)` 把增量输出接到下游。

## BranchComponent / 条件路由

条件分支有两种实现方式：

### 方式 1：BranchComponent 组件
专门的分支组件，内部逻辑决定路由。

### 方式 2：addConditionalConnection + router
更轻量，直接在边上定义路由：

```java
// BranchRouter
workflow.addConditionalConnection("src_id", new BranchRouter(...));

// 或 Function 路由
workflow.addConditionalConnection("src_id", (Function<Object, Object>) input -> {
    Map<String, String> map = (Map<String, String>) input;
    return switch (map.get("intent")) {
        case "transfer" -> "transfer_node";
        case "query" -> "query_node";
        default -> "default_node";
    };
});
```

**router 返回值**必须是已注册的组件 id，否则路由失败。

## SubWorkflowComponentImpl

把另一条 Workflow 作为当前图的节点：

```java
// 搭建子工作流
Workflow subWorkflow = new Workflow(subCard);
subWorkflow.setStartComp("sub_start", new Start(), Map.of("input", "${input}"), null);
// ... 子工作流的节点和边

// 包装成父工作流的节点
workflow.addWorkflowComp("sub", new SubWorkflowComponentImpl(subWorkflow),
        Map.of("input", "${start.input}"), null);
workflow.addConnection("start", "sub");
workflow.addConnection("sub", "end");
```

**好处**：
- 复杂流程分层组织
- 公共子流程复用
- 可视化结构更清晰

## 自定义组件

内置组件不够时，基于 `WorkflowComponent` 或 `ComponentComposable` 扩展：

```java
public class MyComponent extends WorkflowComponent {
    public MyComponent() {
        super(ComponentAbility.INVOKE);  // 声明能力类型
    }

    @Override
    public Object invoke(Map<String, Object> inputs, WorkflowSessionApi session) {
        // batch in, batch out
        String query = (String) inputs.get("query");
        return Map.of("result", process(query));
    }
}
```

**实现方法取决于能力类型**：
- `INVOKE` → 实现 `invoke(...)`
- `STREAM` → 实现 `stream(...)`，返回 `Iterator<WorkflowChunk>`
- `COLLECT` → 实现 `collect(...)`，吃流返 batch
- `TRANSFORM` → 实现 `transform(...)`，吃流返流

## schema 引用语法总结

| 写法 | 含义 | 用在哪 |
| --- | --- | --- |
| `${query}` | 从 workflow 顶层输入读 | 任何节点的 inputsSchema |
| `${start.query}` | 从 Start 输出读 | 后续节点的 inputsSchema |
| `${questioner.amount}` | 从 questioner 输出读 | 后续节点的 inputsSchema |
| `{{query}}` | 渲染变量 | LLMComponent 的 templateContent / End 的 responseTemplate |

**关键**：`inputsSchema` 只描述"从哪里取值"，**不形成执行顺序**；执行顺序靠 `addConnection(...)`。

## 编排三步走

写 Java workflow 至少完成三件事：

1. **注册节点**：`setStartComp(...)` / `addWorkflowComp(...)` / `setEndComp(...)`
2. **配 schema**：让节点知道从哪取值、向下游暴露什么字段
3. **建立边关系**：让执行器知道按什么路径调度节点

很多初学者把第 2 步和第 3 步混在一起。判断方法：
- `Map.of("amount", "${questioner.amount}")` 解决"读哪个值"
- `addConnection("questioner", "end")` 解决"何时执行到 end"

**两者缺一不可**。
