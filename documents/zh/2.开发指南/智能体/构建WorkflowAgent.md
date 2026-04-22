本章节示例演示了如何基于openJiuwen平台，从创建预置组件到编排工作流，最终构建并执行一个完整的天气查询WorkflowAgent。通过本示例，可以了解到如下信息：

- 如何创建组件，具体包括以下openJiuwen的预置组件：开始组件、结束组件、大模型组件、插件组件、意图识别组件、提问器组件。
- 如何创建Workflow流程图。
- 如何创建和执行`WorkflowAgent`。

# 应用设计流程

WorkflowAgent是一种专注于多步骤、任务导向的流程自动化Agent，通过严格遵循用户预定义的任务流程高效地执行复杂任务。用户可预先设定清晰的任务步骤、执行条件及角色分工，将任务拆解为多个可执行的子任务或工具，并通过组件间的拓扑连接与数据传递，逐步推进整个工作流，最终输出预期结果。其侧重于基于预设流程实现任务的规范化与高效化执行，适用于任务结构清晰、可分解为多个步骤的场景。

在本样例中，创建了一个天气查询工作流，确保能够按照预设的流程准确高效的查询到天气信息：

- 开始组件定义了工作流的输入参数规范。
- 意图识别组件判断用户请求的意图是否为查天气，如果是查天气意图，则路由到天气查询分支进行处理，否则路由到默认分支结束流程。
- 大模型组件用于对用户原始输入的query进行改写，自动补充当前日期信息和把地名转换为英文，方便提问器组件提取日期和地名信息。
- 提问器组件用于从用户输入信息中提取日期和地名信息。
- 插件组件用于使用提问器提取的日期和地名作为入参，调用天气插件进行天气查询。
- 结束组件定义了工作流的输出结果格式。
  ![WorkflowAgent](../../images/WorkflowAgent.png)
# 前提条件

- **Java版本**: Java 21或更高版本
- **构建工具**: Maven 3.9+

# 安装openJiuwen

通过Maven将agent-core-java添加为依赖：

```xml
<dependency>
    <groupId>com.openjiuwen</groupId>
    <artifactId>agent-core-java</artifactId>
    <version>0.1.7</version>
</dependency>
```

# 创建Workflow流程

创建Workflow的整体流程如下：首先通过`Workflow`初始化工作流，指定`WorkflowCard`配置参数。接着定义各组件并将组件注册到工作流，并设置组件间的连接，从而完成整个工作流的创建。

## 初始化工作流

初始化工作流，指定`WorkflowCard`配置参数：

```java
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

private static Workflow createWorkflow() {
    WorkflowCard card = WorkflowCard.builder()
            .id("weather_workflow")
            .name("天气查询工作流")
            .version("1.0")
            .description("根据用户输入查询天气信息")
            .build();

    return new Workflow(card);
}
```

## 注册组件到工作流

根据任务需求创建核心组件实例，配置各组件的输入/输出规则，并将组件注册到工作流。

### 开始组件

通过`Start`创建开始组件对象。开始组件作为工作流的开端，定义了工作流的输入参数规范：

```java
import com.openjiuwen.core.workflow.component.Start;

private static Start createStartComponent() {
    return new Start(Map.of(
        "inputs", List.of(Map.of(
            "id", "query",
            "type", "String",
            "required", "true",
            "sourceType", "ref"
        ))
    ));
}
```

### 结束组件

通过`End`创建结束组件对象。结束组件作为工作流的终止，定义了工作流的输出结果格式：

```java
import com.openjiuwen.core.workflow.component.End;

private static End createEndComponent() {
    return new End(Map.of("responseTemplate", "最终结果为：{{output}}"));
}
```

### 意图识别组件

通过`IntentDetectionComponent`构造意图识别组件，用于判断用户意图：

```java
import com.openjiuwen.core.workflow.component.llm.IntentDetectionComponent;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

private static IntentDetectionComponent createIntentDetectionComponent(
        ModelClientConfig clientConfig, ModelRequestConfig requestConfig) {
    IntentDetectionCompConfig config = new IntentDetectionCompConfig();
    config.setUserPrompt("请识别意图");
    config.setCategoryNameList(List.of("查询某地天气"));
    config.setModelClientConfig(clientConfig);
    config.setModelConfig(requestConfig);

    IntentDetectionComponent component = new IntentDetectionComponent(config);
    component.addBranch("${intent.classification_id} == 0", List.of("end"), "默认分支");
    component.addBranch("${intent.classification_id} == 1", List.of("llm"), "查询天气分支");
    return component;
}
```

### 大模型组件

通过`LLMComponent`构造大模型组件对象，用于改写用户输入：

```java
import com.openjiuwen.core.workflow.component.llm.LLMComponent;
import com.openjiuwen.core.workflow.component.llm.LLMCompConfig;
import java.time.LocalDate;

private static LLMComponent createLLMComponent(
        ModelClientConfig clientConfig, ModelRequestConfig requestConfig) {
    String currentDate = LocalDate.now().toString();
    String userPromptPrefix = "你是一个query改写的AI助手。今天的日期是" + currentDate + "。";
    String userPrompt = "\n原始query为：{{query}}\n\n帮我改写原始query，要求：\n"
            + "1. 只把地名改为英文，其他信息保留中文；\n"
            + "2. 默认日期为今天；\n"
            + "3. 时间为YYYY-MM-DD格式。";

    LLMCompConfig config = new LLMCompConfig();
    config.setModelClientConfig(clientConfig);
    config.setModelConfig(requestConfig);
    config.setTemplateContent(List.of(Map.of(
        "role", "user",
        "content", userPromptPrefix + userPrompt
    )));
    config.setResponseFormat(Map.of("type", "text"));
    config.setOutputConfig(Map.of(
        "query", Map.of("type", "string", "description", "改写后的query", "required", true)
    ));

    return new LLMComponent(config);
}
```

### 提问器组件

通过`QuestionerComponent`构造提问器组件对象，用于从用户输入信息中提取指定参数：

```java
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;

private static QuestionerComponent createQuestionerComponent(
        ModelClientConfig clientConfig, ModelRequestConfig requestConfig) {
    List<FieldInfo> keyFields = List.of(
        FieldInfo.builder()
            .fieldName("location")
            .description("地点")
            .required(true)
            .build(),
        FieldInfo.builder()
            .fieldName("date")
            .description("时间")
            .required(true)
            .defaultValue("today")
            .build()
    );

    QuestionerConfig config = new QuestionerConfig();
    config.setModelClientConfig(clientConfig);
    config.setModelConfig(requestConfig);
    config.setQuestionContent("");
    config.setExtractFieldsFromResponse(true);
    config.setFieldNames(keyFields);
    config.setWithChatHistory(false);

    return new QuestionerComponent(config);
}
```

### 插件组件

通过`ToolComponent`构造插件组件节点，用于调用天气插件：

```java
import com.openjiuwen.core.workflow.component.tool.ToolComponent;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;

private static ToolComponent createPluginComponent(String weatherUrl) {
    ToolComponentConfig toolConfig = new ToolComponentConfig();

    RestfulApiCard card = RestfulApiCard.builder()
            .id("weather_tool_workflow")
            .name("WeatherReporter")
            .description("天气查询插件")
            .url(weatherUrl)
            .method("GET")
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of(
                    "location", Map.of(
                        "type", "string",
                        "description", "天气查询的地点，必须为英文"
                    ),
                    "date", Map.of(
                        "type", "string",
                        "description", "天气查询的时间，格式为YYYY-MM-DD"
                    )
                ),
                "required", List.of("location", "date")
            ))
            .build();

    RestfulApi weatherPlugin = new RestfulApi(card);
    return new ToolComponent(toolConfig).bindTool(weatherPlugin);
}
```

## 连接组件

通过`workflow.addConnection`方法设置组件间的连接关系：

```java
// 注册组件到工作流
workflow.setStartComp("start", start, Map.of("query", "${query}"), null);
workflow.addWorkflowComp("intent", intent, Map.of("query", "${start.query}"), null);
workflow.addWorkflowComp("llm", llm, Map.of("query", "${start.query}"), null);
workflow.addWorkflowComp("questioner", questioner, Map.of("query", "${llm.query}"), null);
workflow.addWorkflowComp("plugin", plugin, Map.of(
    "location", "${questioner.location}",
    "date", "${questioner.date}",
    "validated", true
), null);
workflow.setEndComp("end", end, Map.of("output", "${plugin.data}"), null);

// 连接组件
workflow.addConnection("start", "intent");
workflow.addConnection("llm", "questioner");
workflow.addConnection("questioner", "plugin");
workflow.addConnection("plugin", "end");
```

# 创建WorkflowAgent

通过`WorkflowAgentConfig`创建WorkflowAgentConfig对象：

```java
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.DefaultResponse;

private static WorkflowAgent createAgent(ModelConfig modelConfig) {
    WorkflowAgentConfig config = WorkflowAgentConfig.builder()
            .id("weather_agent")
            .version("0.1.0")
            .description("天气查询agent")
            .model(modelConfig)
            .promptTemplate(List.of(Map.of(
                "role", "system",
                "content", "你是一个金融业务助手。"
                    + "当用户提出转账、理财或余额查询需求时，必须选择最合适的工作流处理。"
            )))
            .defaultResponse(DefaultResponse.builder()
                .text("我目前只支持天气查询功能，请明确说明你的需求。")
                .build())
            .build();

    return new WorkflowAgent(config);
}
```

将工作流添加到Agent：

```java
workflowAgent.addWorkflows(List.of(workflow));
```

# 运行WorkflowAgent

调用`Runner.runAgent`方法执行WorkflowAgent：

```java
import com.openjiuwen.core.runner.Runner;
import java.util.Map;

Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
    workflowAgent,
    Map.of("conversation_id", "12345", "query", "上海天气如何"),
    null,
    null
);

System.out.println(result);
```

查询成功后，会得到如下的结果：

```text
{"responseContent":"最终结果为：{'city': 'Shanghai', 'temperature': 29.21, 'weather': '多云'}"}
```

# 人机交互处理

当工作流需要补充信息时，会返回`INPUT_REQUIRED`状态：

```java
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.StreamMode;
import java.util.Iterator;

// 流式执行
Iterator<Object> stream = Runner.runAgentStreaming(
    workflowAgent,
    Map.of("query", userInput, "conversation_id", conversationId),
    null,
    null,
    List.of(StreamMode.OUTPUT)
);

// 处理交互输出
for (Object item : stream) {
    if (item instanceof OutputSchema output) {
        if ("interaction".equals(output.getType())) {
            // 提示用户输入
            String question = extractQuestion(output);
            System.out.println("assistant> " + question);
            
            // 获取用户回答后继续
            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update(nodeId, userReply);
            
            // 继续执行
            Runner.runAgent(
                workflowAgent,
                Map.of("query", interactiveInput, "conversation_id", conversationId),
                null, null
            );
        }
    }
}
```

人机交互流程图：

```
用户输入 -> 意图识别 -> 选择工作流 -> 执行工作流
                                            |
                                     需要补充信息?
                                            |
                                     是 -> 暂停 -> 提问用户
                                            |         |
                                            |    用户回答
                                            |         |
                                     继续执行 <-------+
                                            |
                                     完成 -> 输出结果
```

# 完整示例代码

```java
package examples.workflow_agent;

import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionComponent;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig;
import com.openjiuwen.core.workflow.component.llm.LLMComponent;
import com.openjiuwen.core.workflow.component.llm.LLMCompConfig;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.component.tool.ToolComponent;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorkflowAgentExample {
    private static final String SYSTEM_PROMPT = "你是一个天气查询助手。"
            + "当用户询问天气时，必须选择天气查询工作流处理。"
            + "如果信息不完整，就通过工作流里的提问节点补齐缺失字段。";

    public static void main(String[] args) throws Exception {
        String conversationId = UUID.randomUUID().toString().substring(0, 8);

        try {
            WorkflowAgent agent = createAgent();
            agent.addWorkflows(List.of(createWeatherWorkflow()));

            Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                agent,
                Map.of("query", "上海今天天气如何", "conversation_id", conversationId),
                null, null
            );

            System.out.println("结果: " + result);
        } finally {
            Runner.release(conversationId);
            Runner.stop();
        }
    }

    private static WorkflowAgent createAgent() {
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("weather_agent")
                .description("天气查询助手")
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .defaultResponse(DefaultResponse.builder()
                        .text("我目前只支持天气查询功能")
                        .build())
                .build();

        return new WorkflowAgent(config);
    }

    private static Workflow createWeatherWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id("weather_flow")
                .name("天气查询")
                .version("1.0")
                .description("处理天气查询请求")
                .build();

        Workflow workflow = new Workflow(card);

        // 创建组件实例
        Start start = new Start();
        End end = new End(Map.of("responseTemplate", "天气查询完成：{{output}}"));
        
        // 注：实际使用时需要配置正确的模型参数
        IntentDetectionComponent intent = createIntentDetectionComponent();
        LLMComponent llm = createLLMComponent();
        QuestionerComponent questioner = createQuestionerComponent();
        ToolComponent plugin = createPluginComponent();

        // 注册组件
        workflow.setStartComp("start", start, Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("intent", intent, Map.of("query", "${start.query}"), null);
        workflow.addWorkflowComp("llm", llm, Map.of("query", "${start.query}"), null);
        workflow.addWorkflowComp("questioner", questioner, Map.of("query", "${llm.query}"), null);
        workflow.addWorkflowComp("plugin", plugin, Map.of(
            "location", "${questioner.location}",
            "date", "${questioner.date}"
        ), null);
        workflow.setEndComp("end", end, Map.of("output", "${plugin.data}"), null);

        // 连接组件
        workflow.addConnection("start", "intent");
        workflow.addConnection("llm", "questioner");
        workflow.addConnection("questioner", "plugin");
        workflow.addConnection("plugin", "end");

        return workflow;
    }

    // 其他组件创建方法...
    private static IntentDetectionComponent createIntentDetectionComponent() {
        IntentDetectionCompConfig config = new IntentDetectionCompConfig();
        config.setUserPrompt("请识别意图");
        config.setCategoryNameList(List.of("查询某地天气"));
        // 需要配置模型参数
        IntentDetectionComponent component = new IntentDetectionComponent(config);
        component.addBranch("${intent.classification_id} == 0", List.of("end"), "默认分支");
        component.addBranch("${intent.classification_id} == 1", List.of("llm"), "查询天气分支");
        return component;
    }

    private static LLMComponent createLLMComponent() {
        String currentDate = LocalDate.now().toString();
        LLMCompConfig config = new LLMCompConfig();
        config.setTemplateContent(List.of(Map.of(
            "role", "user",
            "content", "今天是" + currentDate + "。请将query中的地名转为英文：{{query}}"
        )));
        config.setResponseFormat(Map.of("type", "text"));
        config.setOutputConfig(Map.of(
            "query", Map.of("type", "string", "required", true)
        ));
        return new LLMComponent(config);
    }

    private static QuestionerComponent createQuestionerComponent() {
        QuestionerConfig config = new QuestionerConfig();
        config.setFieldNames(List.of(
            FieldInfo.builder().fieldName("location").description("地点").required(true).build(),
            FieldInfo.builder().fieldName("date").description("日期").required(true).defaultValue("today").build()
        ));
        config.setExtractFieldsFromResponse(true);
        config.setWithChatHistory(false);
        return new QuestionerComponent(config);
    }

    private static ToolComponent createPluginComponent() {
        ToolComponentConfig toolConfig = new ToolComponentConfig();
        RestfulApiCard card = RestfulApiCard.builder()
                .id("weather_tool")
                .name("WeatherReporter")
                .description("天气查询")
                .url("https://uapis.cn/api/v1/misc/weather")
                .method("GET")
                .inputParams(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "location", Map.of("type", "string"),
                        "date", Map.of("type", "string")
                    ),
                    "required", List.of("location", "date")
                ))
                .build();
        return new ToolComponent(toolConfig).bindTool(new RestfulApi(card));
    }
}
```

# 关键概念

## WorkflowAgent工作原理

1. **意图识别**: WorkflowAgent使用LLM分析用户输入，选择最合适的工作流
2. **工作流执行**: 按照预定义的组件顺序执行
3. **状态管理**: 支持中断和恢复，通过conversation_id保持会话状态
4. **人机交互**: Questioner组件可以在执行过程中暂停并等待用户输入

## 组件类型

| 组件 | 说明 |
|------|------|
| Start | 工作流入口，接收初始输入 |
| End | 工作流出口，输出最终结果 |
| IntentDetectionComponent | 意图识别，根据用户意图路由到不同分支 |
| LLMComponent | LLM调用组件，用于改写/生成内容 |
| QuestionerComponent | 提问组件，用于信息补充和参数提取 |
| ToolComponent | 工具调用组件，用于调用外部API |
| BranchComponent | 条件分支组件，用于流程控制 |

# 相关资源

- 示例代码: `examples/workflow_agent/WorkflowAgentExampleSupport.java`
- API文档: `documents/zh/2.开发指南/API文档/com.openjiuwen.core/workflow.README.md`
- Python版对照: `docs/zh/2.开发指南/智能体/构建WorkflowAgent.md`