多工作流跳转是openJiuwen框架中`WorkflowAgent`的核心能力，允许智能体在同一会话中管理多个工作流，支持工作流间的智能路由、并发中断和恢复。它解决了用户在同一对话中切换不同任务场景的需求，提供了灵活的多任务管理能力。

多工作流跳转的核心价值在于：

- 智能路由：根据用户意图自动选择合适的工作流
- 并发管理：支持多个工作流同时处于中断状态，互不干扰
- 无缝切换：用户可以在不同工作流间自由切换，系统自动处理状态管理
- 状态恢复：中断的工作流可以随时恢复，保持上下文连续性


# 多工作流跳转流程

`WorkflowAgent`通过`WorkflowController`实现工作流的执行和管理，能够根据用户查询自动选择合适的工作流，并支持工作流间的切换和恢复。

`WorkflowAgent`的开发流程分为以下两步：

- 创建`WorkflowAgent`：通过`WorkflowAgentConfig`创建配置，并动态添加多个工作流实例。
- 运行`WorkflowAgent`：通过`Runner.runAgent`或`Runner.runAgentStreaming`方法执行查询，支持多工作流场景下的意图识别、跳转和恢复。

## 创建WorkflowAgent

用户可根据需求创建`WorkflowAgent`实例，并动态绑定多个工作流。

### 创建WorkflowAgent配置

首先使用`WorkflowAgentConfig`创建智能体配置。该配置支持多工作流场景，可以在创建时传入空的工作流列表，后续通过`addWorkflows`方法动态添加。

```java
import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;

// 模型配置（从环境变量或配置文件读取）
String apiBase = "your api base";
String apiKey = "your api key";
String modelName = "your model name";
String modelProvider = "your model provider";

BaseModelInfo modelInfo = BaseModelInfo.builder()
        .modelName(modelName)
        .apiBase(apiBase)
        .apiKey(apiKey)
        .temperature(0.7)
        .topP(0.9)
        .timeout(120)
        .build();
ModelConfig modelConfig = new ModelConfig(modelProvider, modelInfo);

// 创建最小化配置（workflows 为空列表）
WorkflowAgentConfig config = WorkflowAgentConfig.builder()
        .id("test_multi_workflow_jump_agent")
        .version("0.1.0")
        .description("多工作流跳转恢复测试")
        .model(modelConfig)
        .promptTemplate(List.of(Map.of("role", "system", "content", "你是一个智能助手")))
        .defaultResponse(DefaultResponse.builder()
                .text("我目前只支持特定的业务流程，请明确说明你的需求。")
                .build())
        .build();

WorkflowAgent agent = new WorkflowAgent(config);
```

### 动态添加多个工作流

创建`WorkflowAgent`后，可以使用`addWorkflows`方法动态添加多个工作流实例。该方法会自动从工作流实例中提取`schema`信息并更新配置。

#### 创建工作流实例

以下示例创建两个工作流：天气查询工作流和股票查询工作流。

```java
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * 构建包含提问器的简单工作流。
 *
 * @param workflowId        工作流ID
 * @param workflowName      工作流名称
 * @param workflowDescription 工作流描述
 * @param fieldName         提问字段名
 * @param fieldDescription  提问字段描述
 * @return 包含 start -> questioner -> end 的工作流
 */
private Workflow buildQuestionerWorkflow(
        String workflowId,
        String workflowName,
        String workflowDescription,
        String fieldName,
        String fieldDescription) {

    WorkflowCard card = WorkflowCard.builder()
            .id(workflowId)
            .name(workflowName)
            .version("1.0")
            .description(workflowDescription)
            .inputParams(Map.of(
                    "type", "object",
                    "properties", Map.of("query", Map.of("type", "string", "description", "用户输入")),
                    "required", List.of("query")
            ))
            .build();

    Workflow workflow = new Workflow(card);

    // 创建 Start 组件
    Start start = new Start();

    // 创建提问器配置
    QuestionerConfig questionerConfig = new QuestionerConfig();
    questionerConfig.setModelClientConfig(ModelClientConfig.builder()
            .clientProvider(modelProvider)
            .apiKey(apiKey)
            .apiBase(apiBase)
            .timeout(30.0)
            .verifySsl(false)
            .build());
    questionerConfig.setModelConfig(ModelRequestConfig.builder()
            .modelName(modelName)
            .temperature(0.8)
            .topP(0.9)
            .build());
    questionerConfig.setQuestionContent("请补充" + fieldDescription);
    questionerConfig.setExtractFieldsFromResponse(true);
    questionerConfig.setFieldNames(List.of(FieldInfo.builder()
            .fieldName(fieldName)
            .description(fieldDescription)
            .required(true)
            .build()));
    questionerConfig.setWithChatHistory(false);
    questionerConfig.setMaxResponse(10);

    QuestionerComponent questioner = new QuestionerComponent(questionerConfig);

    // 创建 End 组件
    String responseTemplate = "{{" + fieldName + "}}";
    End end = new End(Map.of("responseTemplate", responseTemplate));

    // 注册组件
    workflow.setStartComp("start", start, Map.of("query", "${query}"), null);
    workflow.addWorkflowComp("questioner", questioner, Map.of("query", "${start.query}"), null);
    workflow.setEndComp("end", end, Map.of(fieldName, "${questioner." + fieldName + "}"), null);

    // 连接拓扑
    workflow.addConnection("start", "questioner");
    workflow.addConnection("questioner", "end");

    return workflow;
}

// 创建两个工作流
Workflow weatherWorkflow = buildQuestionerWorkflow(
        "weather_flow",
        "天气查询",
        "查询某地的天气情况、温度、气象信息",
        "location",
        "地点"
);
Workflow stockWorkflow = buildQuestionerWorkflow(
        "stock_flow",
        "股票查询",
        "查询股票价格、股市行情、股票走势等金融信息",
        "stock_code",
        "股票代码"
);

// 使用 addWorkflows 动态添加（自动提取 schema）
agent.addWorkflows(List.of(weatherWorkflow, stockWorkflow));
```

## 运行WorkflowAgent

`WorkflowAgent`支持多工作流场景下的跳转和恢复功能。以下示例演示了完整的多工作流跳转和恢复流程：

场景描述

1. 用户发起天气查询请求，但未提供地点信息，工作流中断等待输入
2. 用户转而查询股票信息，但未提供股票代码，另一个工作流也中断
3. 用户提供地点信息，恢复天气查询工作流并完成
4. 用户提供股票代码，恢复股票查询工作流并完成

### 步骤1：用户查询"查询天气"

- 意图识别：识别为天气查询工作流
- 执行工作流：工作流执行到提问器组件
- 中断：提问器询问地点

```java
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.StreamMode;
import java.util.Iterator;

String conversationId = "test-jump-recovery-001";

Iterator<Object> stream = Runner.runAgentStreaming(
        agent,
        Map.of("query", "查询天气", "conversation_id", conversationId),
        null,
        null,
        List.of(StreamMode.OUTPUT)
);

// 处理流式输出
while (stream.hasNext()) {
    Object item = stream.next();
    System.out.println(item);
}
```

预期输出

```text
assistant> 请补充地点
```

输出中包含`InteractionOutput`，表示工作流中断等待用户输入。

### 步骤2：用户查询"查看股票"

- 意图识别：识别为股票查询工作流（不同的工作流）
- 检查中断：股票工作流无中断任务
- 执行工作流：执行股票查询工作流
- 中断：提问器询问股票代码

```java
Iterator<Object> stream2 = Runner.runAgentStreaming(
        agent,
        Map.of("query", "查看股票", "conversation_id", conversationId),
        null,
        null,
        List.of(StreamMode.OUTPUT)
);

while (stream2.hasNext()) {
    Object item = stream2.next();
    System.out.println(item);
}
```

预期输出

```text
assistant> 请补充股票代码
```

### 步骤3：用户查询"查询北京天气"

- 意图识别：识别为天气查询工作流
- 检查中断：发现天气工作流有中断任务
- 恢复任务：创建`InteractiveInput`，恢复天气工作流
- 完成：工作流从提问器继续执行，返回结果

```java
import com.openjiuwen.core.session.interaction.InteractiveInput;

// 构造 InteractiveInput 恢复中断的工作流
InteractiveInput reply = new InteractiveInput();
reply.update("questioner", "北京");

Iterator<Object> stream3 = Runner.runAgentStreaming(
        agent,
        Map.of("query", reply, "conversation_id", conversationId),
        null,
        null,
        List.of(StreamMode.OUTPUT)
);

while (stream3.hasNext()) {
    Object item = stream3.next();
    System.out.println(item);
}
```

预期输出（完成）

```text
assistant> 北京
```

### 步骤4：用户查询"查看AAPL股票"

- 意图识别：识别为股票查询工作流
- 检查中断：发现股票工作流有中断任务
- 恢复任务：创建`InteractiveInput`，恢复股票工作流
- 完成：工作流从提问器继续执行，返回结果

```java
InteractiveInput reply2 = new InteractiveInput();
reply2.update("questioner", "AAPL");

Iterator<Object> stream4 = Runner.runAgentStreaming(
        agent,
        Map.of("query", reply2, "conversation_id", conversationId),
        null,
        null,
        List.of(StreamMode.OUTPUT)
);

while (stream4.hasNext()) {
    Object item = stream4.next();
    System.out.println(item);
}

// 清理资源
Runner.release(conversationId);
Runner.stop();
```

预期输出（完成）

```text
assistant> AAPL
```

# 完整示例代码

以下完整示例展示了多工作流跳转和恢复的全流程：

```java
import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MultiWorkflowJumpExample {

    // 从环境变量或配置文件读取模型配置
    private static final String API_BASE = "your api url";
    private static final String API_KEY = "your model token";
    private static final String MODEL_NAME = "your model name";
    private static final String MODEL_PROVIDER = "your model provider";

    private static final String SYSTEM_PROMPT = "你是一个智能助手，能够根据用户意图选择合适的工作流处理请求。";

    public static void main(String[] args) throws Exception {
        // 创建模型配置
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .modelName(MODEL_NAME)
                .apiBase(API_BASE)
                .apiKey(API_KEY)
                .temperature(0.7)
                .topP(0.9)
                .timeout(120)
                .build();
        ModelConfig modelConfig = new ModelConfig(MODEL_PROVIDER, modelInfo);

        // 创建 WorkflowAgent 配置
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("test_multi_workflow_jump_agent")
                .version("0.1.0")
                .description("多工作流跳转恢复测试")
                .model(modelConfig)
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .defaultResponse(DefaultResponse.builder()
                        .text("我目前只支持天气和股票查询，请明确说明你的需求。")
                        .build())
                .build();

        WorkflowAgent agent = new WorkflowAgent(config);

        // 创建两个带提问器的工作流
        Workflow weatherWorkflow = buildQuestionerWorkflow(
                "weather_flow",
                "天气查询",
                "查询某地的天气情况、温度、气象信息",
                "location",
                "地点"
        );
        Workflow stockWorkflow = buildQuestionerWorkflow(
                "stock_flow",
                "股票查询",
                "查询股票价格、股市行情、股票走势等金融信息",
                "stock_code",
                "股票代码"
        );

        // 使用 addWorkflows 动态添加（自动提取 schema）
        agent.addWorkflows(List.of(weatherWorkflow, stockWorkflow));

        String conversationId = UUID.randomUUID().toString();

        try {
            // ========== 步骤1: query1 -> workflow1 -> 中断 ==========
            System.out.println("\n【步骤1】发送 query1: 查询天气");
            runAndPrintOutput(agent, "查询天气", conversationId, null);

            // ========== 步骤2: query2 -> workflow2 -> 中断 ==========
            System.out.println("\n【步骤2】发送 query2: 查看股票");
            runAndPrintOutput(agent, "查看股票", conversationId, null);

            // ========== 步骤3: query3 -> 恢复 workflow1 ==========
            System.out.println("\n【步骤3】发送 query3: 提供地点信息，恢复 workflow1");
            InteractiveInput reply1 = new InteractiveInput();
            reply1.update("questioner", "北京");
            runAndPrintOutput(agent, null, conversationId, reply1);

            // ========== 步骤4: query4 -> 恢复 workflow2 ==========
            System.out.println("\n【步骤4】发送 query4: 提供股票代码，恢复 workflow2");
            InteractiveInput reply2 = new InteractiveInput();
            reply2.update("questioner", "AAPL");
            runAndPrintOutput(agent, null, conversationId, reply2);

        } finally {
            Runner.release(conversationId);
            Runner.stop();
        }
    }

    private static Workflow buildQuestionerWorkflow(
            String workflowId,
            String workflowName,
            String workflowDescription,
            String fieldName,
            String fieldDescription) {

        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(workflowName)
                .version("1.0")
                .description(workflowDescription)
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "用户输入")),
                        "required", List.of("query")
                ))
                .build();

        Workflow workflow = new Workflow(card);

        Start start = new Start();

        QuestionerConfig questionerConfig = new QuestionerConfig();
        questionerConfig.setModelClientConfig(ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER)
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .timeout(30.0)
                .verifySsl(false)
                .build());
        questionerConfig.setModelConfig(ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .temperature(0.8)
                .topP(0.9)
                .build());
        questionerConfig.setQuestionContent("请补充" + fieldDescription);
        questionerConfig.setExtractFieldsFromResponse(true);
        questionerConfig.setFieldNames(List.of(FieldInfo.builder()
                .fieldName(fieldName)
                .description(fieldDescription)
                .required(true)
                .build()));
        questionerConfig.setWithChatHistory(false);
        questionerConfig.setMaxResponse(10);

        QuestionerComponent questioner = new QuestionerComponent(questionerConfig);

        String responseTemplate = "{{" + fieldName + "}}";
        End end = new End(Map.of("responseTemplate", responseTemplate));

        workflow.setStartComp("start", start, Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("questioner", questioner, Map.of("query", "${start.query}"), null);
        workflow.setEndComp("end", end, Map.of(fieldName, "${questioner." + fieldName + "}"), null);

        workflow.addConnection("start", "questioner");
        workflow.addConnection("questioner", "end");

        return workflow;
    }

    private static void runAndPrintOutput(WorkflowAgent agent, String query, 
            String conversationId, InteractiveInput interactiveInput) {
        
        Map<String, Object> inputs;
        if (interactiveInput != null) {
            inputs = Map.of("query", interactiveInput, "conversation_id", conversationId);
        } else {
            inputs = Map.of("query", query, "conversation_id", conversationId);
        }

        Iterator<Object> stream = Runner.runAgentStreaming(
                agent,
                inputs,
                null,
                null,
                List.of(StreamMode.OUTPUT)
        );

        while (stream.hasNext()) {
            Object item = stream.next();
            if (item instanceof OutputSchema output) {
                String type = output.getType();
                if ("interaction".equals(type) || "__interaction__".equals(type)) {
                    Object payload = output.getPayload();
                    if (payload instanceof InteractionOutput interaction) {
                        System.out.println("assistant> " + interaction.getValue());
                    } else {
                        System.out.println("assistant> " + payload);
                    }
                } else {
                    System.out.println("assistant> " + output.getPayload());
                }
            }
        }
    }
}
```

# conversation_id的重要性

对`WorkflowAgent`来说，`conversation_id`有两层作用：

1. `Runner`会优先用它作为agent session id
2. `WorkflowAgent.createManagedSession(...)`也会优先从输入里取它，缺失时才回退到默认session

所以如果你想保住：

- workflow中断任务
- agent多轮上下文
- `workflow_controller.interrupted_tasks`里的恢复信息

就必须继续复用同一个`conversation_id`。

# 意图识别优先级

`WorkflowEventHandler`在进行意图识别时，遵循以下优先级：

1. **如果输入是带node id的`InteractiveInput`**：直接找回之前被打断的workflow，无需再次做LLM意图识别

2. **如果只配置了一条workflow**：直接使用这条workflow，不做分类

3. **如果配置了多条workflow**：进入LLM意图识别，根据workflow描述选择最合适的业务流

4. **如果没识别到明确结果**：若配置了`defaultResponse`就返回默认回复，否则退回第一条workflow

这也是为什么workflow描述文本要写清楚业务边界，不能只写一个很空泛的名字。

# 参考入口

- [示例：workflow_agent](../../../../examples/workflow_agent/README.md)
- [示例：workflow_agent/multi_workflow_agent_demo](../../../../examples/workflow_agent/multi_workflow_agent_demo/README.md)
- [API文档：WorkflowAgent](../API文档/com.openjiuwen.core/application/workflow/WorkflowAgent.md)
- [API文档：WorkflowController](../API文档/com.openjiuwen.core/application/workflow/WorkflowController.md)
- [高阶用法：人机交互](人机交互.md)
- [高阶用法：Checkpointer检查点机制](Checkpointer检查点机制.md)