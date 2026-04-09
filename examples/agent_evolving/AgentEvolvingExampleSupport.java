  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package examples.agent_evolving;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.evaluator.DefaultEvaluator;
import com.openjiuwen.agent_evolving.optimizer.llm_call.InstructionOptimizer;
import com.openjiuwen.agent_evolving.trainer.Trainer;
import com.openjiuwen.agent_evolving.updater.SingleDimUpdater;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import examples.utils.SharedExampleApiConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared implementation for the Java agent_evolving example entry point.
 */
final class AgentEvolvingExampleSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AGENT_ID = "react_agent_evolving_java_example";
    private static final String LLM_OPERATOR_ID = "react_llm";
    private static final String DEFAULT_USER_PROMPT = "{{query}}";
    private static final String SYSTEM_PROMPT = "你是一个 helpful 的 AI 助手。"
            + "请直接回答用户的问题，如果需要可以使用工具来辅助回答。";
    private static final int AGENT_MAX_ITERATIONS = 3;
    private static final int TRAINING_EPOCHS = 3;
    private static final double TRAIN_SPLIT_RATIO = 0.6;
    private static final int TRAIN_SPLIT_SEED = 7;
    private static final int TRAINER_PARALLELISM = 2;
    private static final double EARLY_STOP_SCORE = 0.95;
    private static final double MODEL_TEMPERATURE = 0.3;
    private static final double MODEL_TOP_P = 0.9;
    private static final int MODEL_MAX_TOKENS = 1000;
    private static final double MODEL_TIMEOUT_SECONDS = 120.0;

    private static final Path CHECKPOINT_DIR = Path.of("examples", "agent_evolving", ".checkpoints");
    private static final Path CHECKPOINT_FILE = CHECKPOINT_DIR.resolve("latest.json");

    private AgentEvolvingExampleSupport() {
    }

    static void run(String[] args) throws Exception {
        Files.createDirectories(CHECKPOINT_DIR);

        printConfigSummary();

        ReActAgentEvolve agent = createAgent(SYSTEM_PROMPT, AGENT_ID);
        System.out.println("[agent] created: " + agent.getCard().getId());

        CaseLoader[] split = createQaCases().split(TRAIN_SPLIT_RATIO, TRAIN_SPLIT_SEED);
        CaseLoader trainCases = split[0];
        CaseLoader valCases = split[1].isEmpty() ? split[0] : split[1];
        System.out.printf("[data] train=%d, val=%d%n", trainCases.size(), valCases.size());

        if (Files.isRegularFile(CHECKPOINT_FILE)) {
            System.out.println("[checkpoint] resuming from " + CHECKPOINT_FILE.toAbsolutePath());
        } else {
            System.out.println("[checkpoint] will save to " + CHECKPOINT_FILE.toAbsolutePath());
        }

        ModelRequestConfig modelConfig = createModelRequestConfig();
        ModelClientConfig clientConfig = createModelClientConfig();

        Trainer trainer = new Trainer.Builder()
                .updater(new SingleDimUpdater(new InstructionOptimizer(modelConfig, clientConfig)))
                .evaluator(new DefaultEvaluator(modelConfig, clientConfig))
                .numParallel(TRAINER_PARALLELISM)
                .earlyStopScore(EARLY_STOP_SCORE)
                .checkpointDir(CHECKPOINT_DIR.toString())
                .resumeFrom(CHECKPOINT_FILE.toString())
                .checkpointEveryNEpochs(1)
                .checkpointOnImprove(true)
                .build();

        System.out.println("[train] starting instruction optimization...");
            try {
                trainer.train(agent, trainCases, valCases, TRAINING_EPOCHS, Map.of(
                    "targets", List.of("system_prompt")
                ));
            } catch (RuntimeException exception) {
                throw new IllegalStateException(buildTrainingFailureMessage(exception), exception);
            }
        System.out.println("[train] finished.");

        normalizeOptimizedPrompt(agent);
        printOptimizedPrompt(agent);
        runInference(agent, resolveDemoQueries(args));
    }

    private static ReActAgentEvolve createAgent(String systemPrompt, String agentId) {
        AgentCard agentCard = AgentCard.builder()
                .id(agentId)
                .name("ReAct Agent Evolving Example")
                .description("Java example for self-evolving instruction optimization")
                .build();

        ReActAgentEvolve agent = new ReActAgentEvolve(agentCard);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(AGENT_MAX_ITERATIONS)
                .build()
                .configureModelClient(
                        SharedExampleApiConfigLoader.getModelProvider(),
                        SharedExampleApiConfigLoader.getApiKey(),
                        SharedExampleApiConfigLoader.getApiBase(),
                        SharedExampleApiConfigLoader.getModelName(),
                        SharedExampleApiConfigLoader.getSslVerify()
                )
                .configurePromptTemplate(createRuntimePromptTemplate(systemPrompt));

        if (config.getModelConfigObj() != null) {
            config.getModelConfigObj().setTemperature(MODEL_TEMPERATURE);
            config.getModelConfigObj().setTopP(MODEL_TOP_P);
            config.getModelConfigObj().setMaxTokens(MODEL_MAX_TOKENS);
        }

        agent.configure(config);
        return agent;
    }

    private static CaseLoader createQaCases() {
        return new CaseLoader(List.of(
                new Case(
                        Map.of("query", "什么是机器学习？"),
                        Map.of("answer", "机器学习是人工智能的一个分支，通过算法从数据中学习规律。"),
                        "qa_case_1"
                ),
                new Case(
                        Map.of("query", "Python 如何读取文件？"),
                        Map.of("answer", "使用 open() 函数，例如：with open('file.txt', 'r') as f: content = f.read()"),
                        "qa_case_2"
                ),
                new Case(
                        Map.of("query", "水的化学式是什么？"),
                        Map.of("answer", "水的化学式是 H2O，由两个氢原子和一个氧原子组成。"),
                        "qa_case_3"
                ),
                new Case(
                        Map.of("query", "光速大约是多少？"),
                        Map.of("answer", "光速在真空中约为每秒 30 万公里，即 3x10^8 米/秒。"),
                        "qa_case_4"
                ),
                new Case(
                        Map.of("query", "地球的直径是多少？"),
                        Map.of("answer", "地球的平均直径约为 12,742 公里。"),
                        "qa_case_5"
                )
        ));
    }

    private static ModelRequestConfig createModelRequestConfig() {
        return ModelRequestConfig.builder()
                .modelName(SharedExampleApiConfigLoader.getModelName())
                .temperature(MODEL_TEMPERATURE)
                .topP(MODEL_TOP_P)
                .maxTokens(MODEL_MAX_TOKENS)
                .build();
    }

    private static ModelClientConfig createModelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(SharedExampleApiConfigLoader.getModelProvider())
                .apiKey(SharedExampleApiConfigLoader.getApiKey())
                .apiBase(SharedExampleApiConfigLoader.getApiBase())
                .verifySsl(SharedExampleApiConfigLoader.getSslVerify())
                .timeout(MODEL_TIMEOUT_SECONDS)
                .maxRetries(1)
                .build();
    }

    private static List<String> resolveDemoQueries(String[] args) {
        if (args != null && args.length > 0) {
            return List.of(String.join(" ", args));
        }
        return List.of(
                "请介绍一下机器学习的基本概念。",
                "Python 怎么写文件？"
        );
    }

    @SuppressWarnings("unchecked")
    private static void runInference(ReActAgentEvolve agent, List<String> queries) throws Exception {
        System.out.println("[test] running post-train inference...");
        for (String query : queries) {
            Map<String, Object> result = (Map<String, Object>) agent.invoke(
                    Map.of("query", query),
                    new AgentSession(UUID.randomUUID().toString())
            );
            System.out.println();
            System.out.println("[query] " + query);
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    private static void printConfigSummary() {
        System.out.println("[config] provider=" + SharedExampleApiConfigLoader.getModelProvider());
        System.out.println("[config] model=" + SharedExampleApiConfigLoader.getModelName());
        System.out.println("[config] apiBase=" + SharedExampleApiConfigLoader.getApiBase());
        System.out.println("[config] verifySsl=" + SharedExampleApiConfigLoader.getSslVerify());
    }

    private static void printOptimizedPrompt(ReActAgentEvolve agent) {
        Object operatorObj = agent.getOperators().get(LLM_OPERATOR_ID);
        if (!(operatorObj instanceof LLMCallOperator llmOperator)) {
            return;
        }

        PromptNormalization normalization = normalizePromptState(
                llmOperator.getState().get("system_prompt"),
                llmOperator.getState().get("user_prompt")
        );

        System.out.println("[prompt] optimized system prompt:");
        System.out.println(normalization.systemPrompt());
        System.out.println("[prompt] effective user prompt:");
        System.out.println(normalization.userPrompt());
    }

    private static void normalizeOptimizedPrompt(ReActAgentEvolve agent) {
        Object operatorObj = agent.getOperators().get(LLM_OPERATOR_ID);
        if (!(operatorObj instanceof LLMCallOperator llmOperator)) {
            return;
        }

        Map<String, Object> state = llmOperator.getState();
        PromptNormalization normalization = normalizePromptState(
                state.get("system_prompt"),
                state.get("user_prompt")
        );

        llmOperator.updateSystemPrompt(normalization.systemPrompt());
        llmOperator.updateUserPrompt(normalization.userPrompt());

        Object configObj = agent.getConfig();
        if (configObj instanceof ReActAgentConfig config) {
            config.setPromptTemplate(createRuntimePromptTemplate(normalization.systemPrompt()));
        }
    }

    private static PromptNormalization normalizePromptState(Object rawSystemPrompt, Object rawUserPrompt) {
        String systemPrompt = extractPromptContent(rawSystemPrompt, "system");
        if (systemPrompt.isBlank()) {
            systemPrompt = coercePromptText(rawSystemPrompt, SYSTEM_PROMPT);
        }

        String userPrompt = extractPromptContent(rawUserPrompt, "user");
        if (userPrompt.isBlank()) {
            userPrompt = coercePromptText(rawUserPrompt, "");
        }
        if (userPrompt.isBlank()) {
            userPrompt = extractPromptContent(rawSystemPrompt, "user");
        }
        if (userPrompt.isBlank()) {
            userPrompt = DEFAULT_USER_PROMPT;
        }

        return new PromptNormalization(systemPrompt, userPrompt);
    }

    private static String coercePromptText(Object rawPrompt, String fallback) {
        if (rawPrompt instanceof String text) {
            return text;
        }

        if (rawPrompt instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof SystemMessage systemMessage && systemMessage.getContent() != null) {
                    return String.valueOf(systemMessage.getContent());
                }
            }
            try {
                return MAPPER.writeValueAsString(list);
            } catch (Exception ignored) {
                return fallback;
            }
        }

        return rawPrompt != null ? String.valueOf(rawPrompt) : fallback;
    }

    private static String extractPromptContent(Object rawPrompt, String role) {
        if (rawPrompt instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof SystemMessage systemMessage && "system".equalsIgnoreCase(role)) {
                    return systemMessage.getContent() != null ? String.valueOf(systemMessage.getContent()) : "";
                }
            }
            return "";
        }

        if (!(rawPrompt instanceof String text) || text.isBlank()) {
            return "";
        }

        try {
            List<Map<String, Object>> decoded = MAPPER.readValue(text, new TypeReference<List<Map<String, Object>>>() {
            });
            for (Map<String, Object> message : decoded) {
                if (role.equalsIgnoreCase(String.valueOf(message.get("role")))) {
                    Object content = message.get("content");
                    return content != null ? String.valueOf(content) : "";
                }
            }
        } catch (Exception ignored) {
        }

        Pattern pattern = Pattern.compile(
                "role=" + Pattern.quote(role) + ", content=(.*?)(?=}, \\{role=|}]\\s*$)",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(text.trim());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "";
    }

    private static String buildTrainingFailureMessage(Throwable throwable) {
        String rootMessage = rootMessage(throwable);
        if (rootMessage.contains("balance is insufficient")) {
            return "Remote training failed because the configured model account does not have enough balance for instruction optimization requests. Original error: "
                    + rootMessage;
        }
        return "Remote training failed while calling the configured model service. Original error: " + rootMessage;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null ? message : throwable.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Map<String, String>> createRuntimePromptTemplate(String systemPrompt) {
        return (List) List.of(SystemMessage.builder().content(systemPrompt).build());
    }

    private record PromptNormalization(String systemPrompt, String userPrompt) {
    }
}