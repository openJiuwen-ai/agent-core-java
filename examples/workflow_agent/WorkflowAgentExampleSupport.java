/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.workflow_agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.common.constants.Constant;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import examples.utils.SharedExampleApiConfigLoader;

/**
 * Shared implementation for the Java multi_workflow_agent_demo entry points.
 */
public final class WorkflowAgentExampleSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AGENT_ID = "workflow_agent_java_example";
    private static final String WORKFLOW_VERSION = "1.0";
    private static final String DEFAULT_RESPONSE = "我目前只支持转账、理财和余额查询三类金融流程，请明确说明你的需求。";
    private static final String SYSTEM_PROMPT = "你是一个金融业务助手。"
            + "当用户提出转账、理财或余额查询需求时，必须选择最合适的工作流处理。"
            + "如果信息不完整，就通过工作流里的提问节点补齐缺失字段。"
            + "如果用户需求不属于这三类业务，就直接返回默认回复。";

    private WorkflowAgentExampleSupport() {
    }

    public static void run(String[] args) throws Exception {
        String conversationId = UUID.randomUUID().toString().substring(0, 8);
        WorkflowAgent agent = createAgent();
        agent.addWorkflows(List.of(
                buildFinancialWorkflow(
                        "transfer_flow_multi",
                        "转账服务",
                        "处理用户转账、汇款、打款、transfer、remittance 请求。用户提到转账、汇款、打钱给别人或 transfer money 时应选择这个流程。",
                        "amount",
                        "转账金额，必须是数字或带货币单位的金额描述。",
                        "转账服务完成，记录的转账金额为 {{amount}}。"
                ),
                buildFinancialWorkflow(
                        "invest_flow_multi",
                        "理财服务",
                        "处理理财、投资、购买理财产品、investment、wealth management 请求。用户提到理财、投资、基金、稳健产品或 investment 时应选择这个流程。",
                        "product",
                        "理财产品名称，例如稳健理财、现金管理类产品。",
                        "理财服务完成，选择的理财产品为 {{product}}。"
                ),
                buildFinancialWorkflow(
                        "balance_flow",
                        "余额查询",
                        "处理账户余额、银行卡余额、账户剩余金额、balance inquiry 请求。用户提到查余额、账户余额或 check balance 时应选择这个流程。",
                        "account",
                        "需要查询余额的账户号码。",
                        "余额查询完成，登记的账户号码为 {{account}}。"
                )
        ));

        try {
            runConsole(agent, conversationId, args);
        } finally {
            Runner.release(conversationId);
            Runner.stop();
        }
    }

    private static WorkflowAgent createAgent() {
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id(AGENT_ID)
                .description("Java 多工作流金融助手示例")
                .model(createSharedModelConfig())
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .defaultResponse(DefaultResponse.builder().text(DEFAULT_RESPONSE).build())
                .build();
        return new WorkflowAgent(config);
    }

    private static Workflow buildFinancialWorkflow(
            String workflowId,
            String workflowName,
            String workflowDescription,
            String fieldName,
            String fieldDescription,
            String responseTemplate) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(workflowName)
                .version(WORKFLOW_VERSION)
                .description(workflowDescription)
                .inputParams(defaultInputSchema())
                .build();

        QuestionerConfig questionerConfig = new QuestionerConfig();
        questionerConfig.setModelClientConfig(createQuestionerClientConfig());
        questionerConfig.setModelConfig(createQuestionerRequestConfig());
        questionerConfig.setQuestionContent("请补充" + fieldDescription);
        questionerConfig.setExtractFieldsFromResponse(true);
        questionerConfig.setFieldNames(List.of(FieldInfo.builder()
                .fieldName(fieldName)
                .description(fieldDescription)
                .required(true)
                .build()));
        questionerConfig.setWithChatHistory(false);
        questionerConfig.setMaxResponse(10);

        Workflow workflow = new Workflow(card);
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.addWorkflowComp(
                "questioner",
                new QuestionerComponent(questionerConfig),
                Map.of("query", "${start.query}"),
                null
        );
        workflow.setEndComp(
                "end",
                new End(Map.of("responseTemplate", responseTemplate)),
                Map.of(fieldName, "${questioner." + fieldName + "}"),
                null
        );
        workflow.addConnection("start", "questioner");
        workflow.addConnection("questioner", "end");
        return workflow;
    }

    private static void runConsole(WorkflowAgent agent, String conversationId, String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PendingInteraction pendingInteraction = null;
        String initialQuery = args.length == 0 ? null : String.join(" ", args);

        printBanner(conversationId);

        while (true) {
            String prompt = pendingInteraction == null ? "user> " : "reply> ";
            String userInput = readNextInput(reader, prompt, initialQuery);
            initialQuery = null;

            if (userInput == null) {
                System.out.println();
                System.out.println("输入流结束，示例退出。");
                return;
            }

            userInput = userInput.trim();
            if (userInput.isEmpty()) {
                if (pendingInteraction != null) {
                    System.out.println("assistant> 请输入对上一个问题的回答，或输入 exit 结束示例。");
                }
                continue;
            }

            if (isExitCommand(userInput)) {
                System.out.println("示例结束。\n");
                return;
            }

            Iterator<Object> stream = pendingInteraction == null
                    ? Runner.runAgentStreaming(
                            agent,
                            Map.of("query", userInput, "conversation_id", conversationId),
                            null,
                            null,
                            List.of(StreamMode.OUTPUT)
                    )
                    : Runner.runAgentStreaming(
                            agent,
                            Map.of(
                                    "query", toInteractiveInput(pendingInteraction.nodeId(), userInput),
                                    "conversation_id", conversationId
                            ),
                            null,
                            null,
                            List.of(StreamMode.OUTPUT)
                    );

            pendingInteraction = consumeStream(stream);
        }
    }

    private static PendingInteraction consumeStream(Iterator<Object> stream) {
        PendingInteraction nextInteraction = null;
        boolean printed = false;

        while (stream.hasNext()) {
            Object item = stream.next();
            if (!(item instanceof OutputSchema output)) {
                continue;
            }

            String type = output.getType();
            if (Constant.INTERACTION.equals(type) || "interaction".equals(type)) {
                InteractionOutput interaction = toInteractionOutput(output.getPayload());
                String nodeId = interaction != null && interaction.getId() != null
                        ? interaction.getId()
                        : "questioner";
                String promptText = interaction != null
                        ? stringify(interaction.getValue())
                        : stringify(output.getPayload());
                System.out.println("assistant> " + promptText);
                nextInteraction = new PendingInteraction(nodeId, promptText);
                printed = true;
                continue;
            }

            String text = extractDisplayText(output.getPayload());
            if (!text.isBlank()) {
                System.out.println("assistant> " + text);
                printed = true;
            }
        }

        if (!printed) {
            System.out.println("assistant> [没有返回可显示的输出]");
        }
        return nextInteraction;
    }

    private static String readNextInput(BufferedReader reader, String prompt, String scriptedInput) throws Exception {
        if (scriptedInput != null) {
            System.out.println(prompt + scriptedInput);
            return scriptedInput;
        }
        System.out.print(prompt);
        System.out.flush();
        return reader.readLine();
    }

    private static boolean isExitCommand(String input) {
        return "quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input);
    }

    private static InteractiveInput toInteractiveInput(String nodeId, String userInput) {
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(nodeId, userInput);
        return interactiveInput;
    }

    private static InteractionOutput toInteractionOutput(Object payload) {
        if (payload instanceof InteractionOutput interactionOutput) {
            return interactionOutput;
        }
        if (payload instanceof Map<?, ?> map) {
            Object nodeId = map.get("id");
            Object value = map.get("value");
            return new InteractionOutput(nodeId == null ? "questioner" : String.valueOf(nodeId), value);
        }
        return null;
    }

    private static String extractDisplayText(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String text) {
            return text;
        }
        if (payload instanceof Map<?, ?> map) {
            Object response = map.get("response");
            if (response instanceof String text && !text.isBlank()) {
                return text;
            }
            Object answer = map.get("answer");
            if (answer instanceof String text && !text.isBlank()) {
                return text;
            }
            Object output = map.get("output");
            if (output instanceof Map<?, ?> outputMap) {
                Object nestedAnswer = outputMap.get("answer");
                if (nestedAnswer instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
        }
        return stringify(payload);
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static ModelConfig createSharedModelConfig() {
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .apiKey(SharedExampleApiConfigLoader.getApiKey())
                .apiBase(SharedExampleApiConfigLoader.getApiBase())
                .modelName(SharedExampleApiConfigLoader.getModelName())
                .temperature(0.2)
                .topP(0.8)
                .timeout(120)
                .build();
        return new ModelConfig(SharedExampleApiConfigLoader.getModelProvider(), modelInfo);
    }

    private static ModelClientConfig createQuestionerClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(SharedExampleApiConfigLoader.getModelProvider())
                .apiKey(SharedExampleApiConfigLoader.getApiKey())
                .apiBase(SharedExampleApiConfigLoader.getApiBase())
                .verifySsl(SharedExampleApiConfigLoader.getSslVerify())
                .timeout(120.0)
                .build();
    }

    private static ModelRequestConfig createQuestionerRequestConfig() {
        return ModelRequestConfig.builder()
                .modelName(SharedExampleApiConfigLoader.getModelName())
                .temperature(0.2)
                .topP(0.8)
                .maxTokens(256)
                .build();
    }

    private static Map<String, Object> defaultInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "用户输入"
                        )
                ),
                "required", List.of("query")
        );
    }

    private static void printBanner(String conversationId) {
        System.out.println();
        System.out.println("========== Workflow Agent Java Example ==========");
        System.out.println("会话 ID: " + conversationId);
        System.out.println("支持场景: 转账、理财、余额查询");
        System.out.println("输入 quit 或 exit 退出示例。");
        System.out.println();
        System.out.println("示例输入:");
        System.out.println("  - 我要转账");
        System.out.println("  - 我想买理财产品");
        System.out.println("  - 帮我查一下余额");
        System.out.println();
    }

    private record PendingInteraction(String nodeId, String promptText) {
    }
}