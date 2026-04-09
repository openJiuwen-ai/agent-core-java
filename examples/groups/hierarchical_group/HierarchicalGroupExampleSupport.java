  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package examples.groups.hierarchical_group;

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
import com.openjiuwen.core.multiagent.legacy.AgentGroupConfig;
import com.openjiuwen.core.multiagent.legacy.ControllerGroup;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import examples.utils.SharedExampleApiConfigLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared implementation for the Java hierarchical group example.
 */
@SuppressWarnings("deprecation")
final class HierarchicalGroupExampleSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GROUP_ID = "hierarchical_group_java_example";
    private static final String LEADER_AGENT_ID = "main_controller";
    private static final String TRANSFER_WORKER_ID = "transfer_worker";
    private static final String INVEST_WORKER_ID = "invest_worker";
    private static final String BALANCE_WORKER_ID = "balance_worker";

    private static final String DEFAULT_RESPONSE =
            "我目前只支持转账、理财和余额查询三类 worker，请明确说明你的需求。";

    private HierarchicalGroupExampleSupport() {
    }

    static void run(String[] args) throws Exception {
        String conversationId = UUID.randomUUID().toString().substring(0, 8);
        DemoGroup demoGroup = createDemoGroup();
        AgentGroupSessionApi session = AgentGroupSessionApi.create(
                conversationId,
                Map.of("example", "hierarchical_group")
        );

        try {
            runConsole(demoGroup.group(), session, args);
        } finally {
            demoGroup.controller().stop();
            Runner.release(conversationId);
            Runner.stop();
        }
    }

    private static DemoGroup createDemoGroup() {
        HierarchicalGroupController controller = new HierarchicalGroupController(LEADER_AGENT_ID);
        ControllerGroup group = new ControllerGroup(
                new AgentGroupConfig(GROUP_ID),
                controller
        );

        HierarchicalLeaderAgent leaderAgent = new HierarchicalLeaderAgent(
                LEADER_AGENT_ID,
                "Leader worker 路由总控，负责把用户请求分配给对应的业务 worker",
                Map.of(
                        TRANSFER_WORKER_ID, List.of("转账", "汇款", "remittance", "transfer", "打钱"),
                        INVEST_WORKER_ID, List.of("理财", "投资", "基金", "investment", "wealth"),
                        BALANCE_WORKER_ID, List.of("余额", "查余额", "balance", "账户余额")
                ),
                DEFAULT_RESPONSE
        );
        leaderAgent.setGroupController(controller);

        group.addAgent(LEADER_AGENT_ID, leaderAgent);
        group.addAgent(TRANSFER_WORKER_ID, createWorkerAgent(
                TRANSFER_WORKER_ID,
                "转账服务 worker，处理用户转账、汇款、打款请求",
                "transfer_flow",
                "转账服务",
                "处理用户转账、汇款、打款、transfer money 请求",
                "amount",
                "转账金额，必须是数字或带货币单位的金额描述。",
                "转账服务完成，记录的转账金额为 {{amount}}。"
        ));
        group.addAgent(INVEST_WORKER_ID, createWorkerAgent(
                INVEST_WORKER_ID,
                "理财服务 worker，处理用户理财、投资和购买产品请求",
                "invest_flow",
                "理财服务",
                "处理理财、投资、购买理财产品、wealth management 请求",
                "product",
                "理财产品名称，例如稳健理财、现金管理类产品。",
                "理财服务完成，选择的理财产品为 {{product}}。"
        ));
        group.addAgent(BALANCE_WORKER_ID, createWorkerAgent(
                BALANCE_WORKER_ID,
                "余额查询 worker，处理用户账户余额相关请求",
                "balance_flow",
                "余额查询",
                "处理账户余额、银行卡余额、balance inquiry 请求",
                "account",
                "需要查询余额的账户号码。",
                "余额查询完成，登记的账户号码为 {{account}}。"
        ));

        controller.subscribe("finance_broadcast", List.of(
                TRANSFER_WORKER_ID,
                INVEST_WORKER_ID,
                BALANCE_WORKER_ID
        ));

        return new DemoGroup(group, controller);
    }

    private static WorkflowAgent createWorkerAgent(
            String agentId,
            String description,
            String workflowId,
            String workflowName,
            String workflowDescription,
            String fieldName,
            String fieldDescription,
            String responseTemplate
    ) {
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id(agentId)
                .description(description)
                .model(createSharedModelConfig())
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "你是 " + workflowName + " worker。只处理当前分配给你的业务，并在信息不足时向用户提问。"
                )))
                .defaultResponse(DefaultResponse.builder().text(DEFAULT_RESPONSE).build())
                .build();

        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(buildQuestionerWorkflow(
                workflowId,
                workflowName,
                workflowDescription,
                fieldName,
                fieldDescription,
                responseTemplate
        )));
        return agent;
    }

    private static Workflow buildQuestionerWorkflow(
            String workflowId,
            String workflowName,
            String workflowDescription,
            String fieldName,
            String fieldDescription,
            String responseTemplate
    ) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(workflowName)
                .version("1.0")
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

    private static void runConsole(ControllerGroup group, AgentGroupSessionApi session, String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PendingInteraction pendingInteraction = null;
        String initialQuery = args.length == 0 ? null : String.join(" ", args);

        printBanner(session.getSessionId());

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

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("conversation_id", session.getSessionId());
            inputs.put(
                    "query",
                    pendingInteraction == null
                            ? userInput
                            : toInteractiveInput(pendingInteraction.nodeId(), userInput)
            );

            try {
                Object result = group.invoke(inputs, session);
                pendingInteraction = consumeResult(result);
            } catch (RuntimeException e) {
                System.out.println("assistant> 示例执行失败: " + extractRootMessage(e));
                return;
            }
        }
    }

    private static PendingInteraction consumeResult(Object result) {
        if (result instanceof List<?> list) {
            return consumeList(list);
        }

        String text = extractDisplayText(result);
        if (text.isBlank()) {
            System.out.println("assistant> [没有返回可显示的输出]");
            return null;
        }

        System.out.println("assistant> " + text);
        return null;
    }

    private static PendingInteraction consumeList(List<?> items) {
        PendingInteraction nextInteraction = null;
        boolean printed = false;

        for (Object item : items) {
            if (item instanceof TraceSchema) {
                continue;
            }

            if (item instanceof OutputSchema outputSchema) {
                String type = outputSchema.getType();
                if (Constant.INTERACTION.equals(type) || "interaction".equals(type)) {
                    InteractionOutput interaction = toInteractionOutput(outputSchema.getPayload());
                    String nodeId = interaction != null && interaction.getId() != null
                            ? interaction.getId()
                            : "questioner";
                    String promptText = interaction != null
                            ? stringify(interaction.getValue())
                            : stringify(outputSchema.getPayload());
                    System.out.println("assistant> " + promptText);
                    nextInteraction = new PendingInteraction(nodeId, promptText);
                    printed = true;
                    continue;
                }

                String text = extractDisplayText(outputSchema.getPayload());
                if (!text.isBlank()) {
                    System.out.println("assistant> " + text);
                    printed = true;
                }
                continue;
            }

            String text = extractDisplayText(item);
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
                Object nestedResponse = outputMap.get("response");
                if (nestedResponse instanceof String text && !text.isBlank()) {
                    return text;
                }
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

    private static String extractRootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
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
        System.out.println("========== Hierarchical Group Java Example ==========");
        System.out.println("会话 ID: " + conversationId);
        System.out.println("Group 结构: leader(main_controller) + 3 个业务 worker");
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

    private record DemoGroup(ControllerGroup group, HierarchicalGroupController controller) {
    }
}