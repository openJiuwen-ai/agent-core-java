/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package references;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;

import java.util.List;
import java.util.Map;

/**
 * 最小可运行的工作流构建示例。
 *
 * 前置条件（缺一不可，否则跑不起来）：
 *   1. 把本文件或整个 examples/ 目录挪到 src/main/java/ 下，让它进入 Maven 编译路径。
 *   2. src/main/resources/apiconfig.json 填真实值（API_BASE / API_KEY / MODEL_PROVIDER / MODEL_NAME）。
 *      也可通过 -Dopenjiuwen.example.config=<path> 或 OPENJIUWEN_API_CONFIG 环境变量覆盖。
 *
 * 启动命令：
 *   mvn exec:java -Dexec.mainClass=references.MinimalWorkflowExample
 *
 * 或在 IDE 里直接右键运行 main 方法。
 *
 * 本示例对齐 examples/workflow_agent/WorkflowAgentExampleSupport.java 的单条 workflow 路径，
 * 但去掉了 WorkflowAgent 托管、多 workflow 跳转、控制台交互循环等干扰逻辑，
 * 只保留"定义卡片 → 注册节点 → 连边 → invoke → 处理 INPUT_REQUIRED → 收尾"主线，
 * 便于新手理解单条 workflow 的最小可运行形态。
 *
 * 工作流图：
 *   Start → QuestionerComponent(补问金额) → End
 *
 * 执行流程：
 *   1. 用户输入"我要转账"（缺金额）
 *   2. Start → Questioner：amount 缺失，返回 INPUT_REQUIRED
 *   3. 代码自动补答"2000元" → 同 session 恢复
 *   4. Questioner 提取 amount → End → COMPLETED
 *   5. 输出"转账服务完成，金额为 2000元。"
 */
public final class MinimalWorkflowExample {

    private MinimalWorkflowExample() {
    }

    public static void main(String[] args) throws Exception {
        // 1. 模型配置（实际项目里通常用 SharedExampleApiConfigLoader 读 apiconfig.json）
        ModelClientConfig clientConfig = createModelClientConfig();
        ModelRequestConfig requestConfig = createModelRequestConfig();

        // 2. 定义 WorkflowCard（工作流身份和输入 schema）
        WorkflowCard card = WorkflowCard.builder()
                .id("transfer_flow")
                .name("转账服务")
                .version("1.0")
                .description("补齐转账金额并返回最终结果")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "用户输入")
                        ),
                        "required", List.of("query")
                ))
                .build();

        // 3. 创建 Workflow 并注册节点
        Workflow workflow = new Workflow(card);

        // 3.1 Start 节点：透传顶层输入
        workflow.setStartComp(
                "start",
                new Start(),
                Map.of("query", "${query}"),   // ${query} 从顶层输入读
                null
        );

        // 3.2 QuestionerComponent：补问缺失的 amount 字段
        QuestionerConfig questionerConfig = new QuestionerConfig();
        questionerConfig.setModelClientConfig(clientConfig);
        questionerConfig.setModelConfig(requestConfig);
        questionerConfig.setQuestionContent("请补充转账金额，必须是数字或带货币单位的金额描述。");
        questionerConfig.setExtractFieldsFromResponse(true);
        questionerConfig.setFieldNames(List.of(
                FieldInfo.builder()
                        .fieldName("amount")
                        .description("转账金额，必须是数字或带货币单位的金额描述。")
                        .required(true)
                        .build()
        ));
        questionerConfig.setWithChatHistory(false);
        questionerConfig.setMaxResponse(10);

        workflow.addWorkflowComp(
                "questioner",                                       // 组件 id，恢复交互时要一致
                new QuestionerComponent(questionerConfig),
                Map.of("query", "${start.query}"),                  // ${start.query} 从 Start 输出读
                null
        );

        // 3.3 End 节点：用 responseTemplate 渲染最终输出
        workflow.setEndComp(
                "end",
                new End(Map.of("responseTemplate", "转账服务完成，记录的转账金额为 {{amount}}。")),
                Map.of("amount", "${questioner.amount}"),           // ${questioner.amount} 从 questioner 输出读
                null
        );

        // 4. 连边（注册节点不等于形成执行顺序，必须连边）
        workflow.addConnection("start", "questioner");
        workflow.addConnection("questioner", "end");

        // 5. 创建 session 并执行
        //    注意：恢复执行时必须复用同一 session，换 session 会丢失状态
        WorkflowSessionApi session = WorkflowSessions.createWorkflowSession("conversation-001");

        // 6. 第一次 invoke：用户输入"我要转账"（缺金额）
        String userQuery = args.length > 0 ? args[0] : "我要转账";
        System.out.println(">>> 用户输入: " + userQuery);

        WorkflowOutput output = workflow.invoke(
                Map.of("query", userQuery),
                session,
                null
        );

        // 7. 处理 INPUT_REQUIRED：Questioner 要求补问金额
        if (WorkflowExecutionState.INPUT_REQUIRED.equals(output.getState())) {
            System.out.println(">>> 工作流需要补充输入（state=INPUT_REQUIRED）");

            String reply = args.length > 1 ? args[1] : "2000元";
            System.out.println(">>> 补答: " + reply);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("questioner", reply);   // "questioner" 必须与注册时的组件 id 一致

            // 同 session 恢复执行
            output = workflow.invoke(interactiveInput, session, null);
        }

        // 8. 检查最终状态
        if (WorkflowExecutionState.COMPLETED.equals(output.getState())) {
            System.out.println(">>> 工作流完成（state=COMPLETED）");
            System.out.println(">>> 结果: " + output.getResult());
        } else if (WorkflowExecutionState.ERROR.equals(output.getState())) {
            System.out.println(">>> 工作流出错（state=ERROR）");
            System.out.println(">>> 结果: " + output.getResult());
        } else {
            System.out.println(">>> 工作流状态: " + output.getState());
            System.out.println(">>> 结果: " + output.getResult());
        }
    }

    /**
     * 模型客户端配置。
     * 实际项目里用 SharedExampleApiConfigLoader 从 apiconfig.json 读取。
     * 这里为了示例自包含，直接传参。
     */
    private static ModelClientConfig createModelClientConfig() {
        // 替换为你的真实值
        return ModelClientConfig.builder()
                .provider("your-provider")           // MODEL_PROVIDER，如 openai / azure / qwen
                .apiKey("your-api-key")              // API_KEY
                .apiBaseUrl("https://your-api-base") // API_BASE
                .build();
    }

    /**
     * 模型请求参数配置。
     */
    private static ModelRequestConfig createModelRequestConfig() {
        ModelRequestConfig config = new ModelRequestConfig();
        config.setModelName("your-model-name");     // MODEL_NAME
        config.setTemperature(0.2);
        config.setTopP(0.9);
        return config;
    }
}
