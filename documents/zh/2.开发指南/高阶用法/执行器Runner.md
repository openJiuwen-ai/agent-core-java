# 执行器Runner

Runner是openJiuwen执行所有核心组件（包括Workflow，Agent）的统一入口和控制中心。它将复杂的执行逻辑抽象化，为开发者提供了一个简洁、一致且强大的编程接口。

**重要说明**：Runner是一个单例类，所有方法调用和属性访问都会自动代理到全局的Runner实例。无需实例化Runner，直接通过类名调用即可，例如：`Runner.start()`、`Runner.resourceMgr()`。

Runner的主要功能包括：

- 提供Agent标准的异步调用（invoke）和异步流式调用（stream）两种执行入口。
- 提供Workflow标准的异步调用（invoke）和异步流式调用（stream）两种执行入口。

## Agent执行

Runner支持所有Agent的单次输出执行和流式输出执行，包括ReActAgent、WorkflowAgent等内置Agent的执行，也包括用户自定义的Agent的执行。

下面以一个`WorkflowAgent`为例，介绍通过`Runner`执行`Agent`的过程。

首先，创建一个WorkflowAgent实例：

```java
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.runner.Runner;

import java.util.List;
import java.util.Map;

public class WorkflowAgentExample {

    private static WorkflowAgent createAgent() {
        // 创建工作流
        WorkflowCard card = WorkflowCard.builder()
                .id("workflow_id")
                .name("简单工作流")
                .version("1")
                .description("这是一个演示工作流")
                .build();

        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        flow.setEndComp("end", new End(Map.of("responseTemplate", "{{result}}")),
                Map.of("result", "${start.query}"), null);
        flow.addConnection("start", "end");

        // 注册工作流到资源管理器
        String workflowKey = card.getId() + "_" + card.getVersion();
        Runner.resourceMgr().addWorkflow(workflowKey, flow);

        // 创建Agent
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .modelName("gpt-4")
                .apiKey("sk-xxxxx")
                .apiBase("https://api.openai.com/v1")
                .temperature(0.2)
                .build();

        WorkflowAgentConfig agentConfig = WorkflowAgentConfig.builder()
                .id("agent_id")
                .version("1")
                .description("这是一个演示Agent")
                .model(new ModelConfig("OpenAI", modelInfo))
                .workflows(List.of(workflowKey))
                .build();

        return new WorkflowAgent(agentConfig);
    }

    public static void main(String[] args) throws Exception {
        WorkflowAgent agent = createAgent();
        
        // 执行Agent
        Object result = Runner.runAgent(
                agent,
                Map.of("query", "哈哈", "conversation_id", "test_conv"),
                null,
                null
        );
        
        System.out.println(result);
    }
}
```

执行结果：

```java
// 输出类似：Map.of("output", Map.of("result", "哈哈"), "state", "COMPLETED")
```

## Workflow执行

Runner支持Workflow的单次输出执行和流式输出执行。

下面通过构建一个简单工作流为例，介绍`Runner`执行`Workflow`的过程。

首先，创建一个Workflow:

```java
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

public class WorkflowExample {

    private static Workflow buildWorkflow(String name, String workflowId, String version) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(name)
                .version(version)
                .description("这是一个演示工作流")
                .build();

        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        flow.setEndComp("end", new End(Map.of("responseTemplate", "{{result}}")),
                Map.of("result", "${start.query}"), null);
        flow.addConnection("start", "end");
        return flow;
    }

    public static void main(String[] args) throws Exception {
        Workflow workflow = buildWorkflow("test_workflow", "test_workflow", "1");
        
        // 执行Workflow
        Object result = Runner.runWorkflow(
                workflow,
                Map.of("query", "query workflow"),
                null,
                null
        );
        
        System.out.println(result);
    }
}
```

执行结果：

```java
// 输出类似：Map.of("output", Map.of("result", "query workflow"), "state", "COMPLETED")
```

## 流式执行

Runner也支持流式执行：

```java
import com.openjiuwen.core.session.stream.StreamMode;
import java.util.Iterator;
import java.util.List;

public class StreamingExample {

    public static void runStreaming(WorkflowAgent agent) {
        Iterator<Object> stream = Runner.runAgentStreaming(
                agent,
                Map.of("query", "我要转账", "conversation_id", "conversation-001"),
                null,
                null,
                List.of(StreamMode.OUTPUT)
        );

        while (stream.hasNext()) {
            Object chunk = stream.next();
            // 处理流式输出块
            System.out.println("receive chunk: " + chunk);
        }
    }
}
```

## Runner配置

Runner支持通过`RunnerConfig`进行配置：

```java
import com.openjiuwen.core.runner.RunnerConfig;
import java.util.Map;

public class RunnerConfigExample {

    public static void configureRunner() {
        RunnerConfig config = RunnerConfig.builder()
                .distributedMode(false)
                .checkpointerConfig(Map.of(
                        "type", "in_memory",
                        "conf", Map.of()
                ))
                .build();

        Runner.setConfig(config);
        Runner.start();
    }
}
```

## 资源管理器

Runner内置资源管理器`ResourceMgr`，用于统一管理Workflow、Agent、Tool等资源：

```java
import com.openjiuwen.core.runner.Runner;

// 注册工作流
Runner.resourceMgr().addWorkflow("workflow_key", workflow);

// 获取已注册的工作流
Workflow workflow = Runner.resourceMgr().getWorkflow("workflow_key");

// 注册Agent
Runner.resourceMgr().addAgent("agent_key", agent);

// 获取已注册的Agent
Agent agent = Runner.resourceMgr().getAgent("agent_key");
```

## 会话清理

执行完成后，可以通过`Runner.release`清理会话状态：

```java
// 清理指定会话的资源
Runner.release("conversation-001");

// 停止Runner
Runner.stop();
```

## conversation_id的重要性

对Agent来说，`Runner`会优先从输入里读取`conversation_id`作为session ID。如果没有，就回退到`default_session`。

这会直接影响：
- 多轮上下文是否延续；
- 交互恢复是否能回到同一条执行链；
- `WorkflowAgent`这类应用层Agent是否能复用之前的中断任务。

因此，只要需要多轮或恢复，最好显式传`conversation_id`。

## 完整示例

```java
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;

public class RunnerFullExample {

    public static void main(String[] args) throws Exception {
        // 配置Runner
        Runner.start();

        // 创建工作流
        WorkflowCard card = WorkflowCard.builder()
                .id("transfer_flow")
                .name("转账服务")
                .version("1.0")
                .build();

        Workflow workflow = new Workflow(card);
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        
        QuestionerConfig questionerConfig = new QuestionerConfig();
        questionerConfig.setQuestionContent("请补充转账金额，必须是数字或带货币单位的金额描述。");
        questionerConfig.setExtractFieldsFromResponse(true);
        questionerConfig.setFieldNames(List.of(FieldInfo.builder()
                .fieldName("amount")
                .description("转账金额")
                .required(true)
                .build()));
        
        workflow.addWorkflowComp("questioner", new QuestionerComponent(questionerConfig),
                Map.of("query", "${start.query}"), null);
        workflow.setEndComp("end", new End(Map.of("responseTemplate", "转账完成，金额为{{amount}}")),
                Map.of("amount", "${questioner.amount}"), null);
        workflow.addConnection("start", "questioner");
        workflow.addConnection("questioner", "end");

        // 注册工作流
        Runner.resourceMgr().addWorkflow("transfer_flow_1.0", workflow);

        // 创建Agent
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .modelName("gpt-4")
                .apiKey("sk-xxxxx")
                .apiBase("https://api.openai.com/v1")
                .temperature(0.2)
                .build();

        WorkflowAgentConfig agentConfig = WorkflowAgentConfig.builder()
                .id("workflow_agent")
                .version("1.0")
                .model(new ModelConfig("OpenAI", modelInfo))
                .workflows(List.of("transfer_flow_1.0"))
                .build();

        WorkflowAgent agent = new WorkflowAgent(agentConfig);

        // 执行Agent（流式）
        String conversationId = UUID.randomUUID().toString().substring(0, 8);
        Iterator<Object> stream = Runner.runAgentStreaming(
                agent,
                Map.of("query", "我要转账", "conversation_id", conversationId),
                null,
                null,
                List.of(StreamMode.OUTPUT)
        );

        // 消费流式输出
        String lastNodeId = null;
        while (stream.hasNext()) {
            Object chunk = stream.next();
            if (chunk instanceof OutputSchema output) {
                if ("__interaction__".equals(output.getType())) {
                    // 捕获交互节点ID
                    lastNodeId = "questioner";
                    System.out.println("assistant> " + output.getPayload());
                } else {
                    System.out.println("assistant> " + output.getPayload());
                }
            }
        }

        // 如果有交互，继续执行
        if (lastNodeId != null) {
            InteractiveInput reply = new InteractiveInput();
            reply.update(lastNodeId, "2000元");

            Iterator<Object> continueStream = Runner.runAgentStreaming(
                    agent,
                    Map.of("query", reply, "conversation_id", conversationId),
                    null,
                    null,
                    List.of(StreamMode.OUTPUT)
            );

            while (continueStream.hasNext()) {
                Object chunk = continueStream.next();
                System.out.println("assistant> " + chunk);
            }
        }

        // 清理
        Runner.release(conversationId);
        Runner.stop();

        System.out.println("Runner示例完成");
    }
}
```

输出示例：

```
assistant> 请补充转账金额，必须是数字或带货币单位的金额描述。
assistant> 转账完成，金额为2000元
Runner示例完成
```