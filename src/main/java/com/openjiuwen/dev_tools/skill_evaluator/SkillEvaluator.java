/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.ToolDecorator;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * Skill evaluator that builds a ReAct agent for evaluating skill packages.
 *
 * <p>Mirrors Python's {@code SkillEvaluator} in
 * {@code openjiuwen/dev_tools/skill_evaluator/skill_evaluator.py}.</p>
 */
public class SkillEvaluator {
    private static final String DEFAULT_SKILLS_DIR = "openjiuwen/dev_tools/skill_evaluator/skills";
    private static final String DEFAULT_FILES_BASE_DIR = "openjiuwen/dev_tools/skill_evaluator";
    private static final int DEFAULT_MAX_ITERATIONS = 25;

    private final Map<String, String> environment;
    private ReActAgent agent;
    private List<ToolCard> tools = new ArrayList<>();

    public SkillEvaluator() {
        this(System.getenv());
    }

    public SkillEvaluator(Map<String, String> environment) {
        this.environment = Collections.unmodifiableMap(loadDotEnv(environment));
    }

    public CompletionStage<Void> createAgent() {
        Path skillsDir = expandUser(env("SKILLS_DIR", DEFAULT_SKILLS_DIR)).toAbsolutePath().normalize();
        String filesBaseDir = env("FILES_BASE_DIR", expandUser(DEFAULT_FILES_BASE_DIR)
                .toAbsolutePath()
                .normalize()
                .toString());
        int maxIterations = parseInt(env("MAX_ITERATIONS", String.valueOf(DEFAULT_MAX_ITERATIONS)),
                DEFAULT_MAX_ITERATIONS);
        String outputDir = env("OUTPUT_DIR", "");

        String apiBase = env("API_BASE", "");
        String apiKey = env("API_KEY", "");
        String modelName = env("MODEL_NAME", "");
        String modelProvider = env("MODEL_PROVIDER", "");
        boolean verifySsl = parseBoolean(env("LLM_SSL_VERIFY", "False"));

        AgentCard card = new AgentCard();
        card.setName("skill_evaluator_agent");
        card.setDescription("Skill Evaluator Agent");
        this.agent = new ReActAgent(card);

        String systemPrompt = "You are an intelligent assistant.\n"
                + "All user-provided files are located at '" + filesBaseDir + "'\n"
                + "Put all generated files into " + outputDir + "\n"
                + "You may use tools when necessary.\n";

        SysOperationCard sysopCard = new SysOperationCard();
        sysopCard.setMode(OperationMode.LOCAL);
        sysopCard.setWorkConfig(new LocalWorkConfig());
        Runner.resourceMgr().addSysOperation(sysopCard);

        ReActAgentConfig config = new ReActAgentConfig()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, verifySsl)
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .configureMaxIterations(maxIterations)
                .configureContextEngine(null, null, false, false);
        config.setSysOperationId(sysopCard.getId());
        this.agent.configure(config);

        this.tools = new ArrayList<>();
        addSysOpTool(sysopCard.getId(), "fs", "read_file");
        addSysOpTool(sysopCard.getId(), "code", "execute_code");
        addSysOpTool(sysopCard.getId(), "shell", "execute_cmd");
        addSysOpTool(sysopCard.getId(), "fs", "write_file");
        this.agent.getAbilityManager().add(this.tools);

        Tool subagentTool = createSubagentTool(config, this.tools, skillsDir.toString());
        Runner.resourceMgr().addTool(subagentTool);
        this.agent.getAbilityManager().add(subagentTool.getCard());

        if (!Files.exists(skillsDir)) {
            return CompletableFuture.failedFuture(
                    new NoSuchFileException("Skills directory '" + skillsDir + "' does not exist."));
        }
        return this.agent.registerSkill(skillsDir.toString()).thenApply(ignored -> null);
    }

    public CompletionStage<Map<String, Object>> evaluate(Path skillPath) {
        return evaluate(skillPath, "", null);
    }

    public CompletionStage<Map<String, Object>> evaluate(Path skillPath, String requirement) {
        return evaluate(skillPath, requirement, null);
    }

    public CompletionStage<Map<String, Object>> evaluate(Path skillPath, String requirement, Path outputPath) {
        Objects.requireNonNull(skillPath, "skillPath");
        Path reportDir = outputPath != null ? outputPath : missingOutputDir();
        if (agent == null) {
            throw new IllegalStateException("SkillEvaluator.agent is not initialized. Call createAgent() first.");
        }
        String query = buildEvaluationQuery(skillPath, requirement, reportDir);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        inputs.put("conversation_id", "skill_eval_001");
        return Runner.runAgent(agent, inputs).thenApply(SkillEvaluator::asResultMap);
    }

    public ReActAgent getAgent() {
        return agent;
    }

    public void setAgent(ReActAgent agent) {
        this.agent = agent;
    }

    public List<ToolCard> getTools() {
        return List.copyOf(tools);
    }

    String buildEvaluationQuery(Path skillPath, String requirement, Path reportDir) {
        String suffix = requirement == null ? "" : requirement;
        return "Help me evaluate the skill in the '" + skillPath + "'.\n"
                + "Save evaluation report to '" + reportDir + "' foler." + suffix;
    }

    private void addSysOpTool(String sysOperationId, String operationName, String toolName) {
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(
                sysOperationId,
                List.of(operationName),
                List.of(toolName)
        );
        if (toolCard instanceof ToolCard card) {
            tools.add(card);
        }
    }

    private Tool createSubagentTool(ReActAgentConfig config, List<ToolCard> toolCards, String defaultSkillsDir) {
        return ToolDecorator.tool(
                inputs -> invokeSubagent(config, toolCards, defaultSkillsDir, inputs),
                ToolDecorator.Options.builder()
                        .name("create_subagent")
                        .description("Create and invoke a subagent to complete a specified task. "
                                + "The subagent loads skills from the provided skills directory and executes it.")
                        .build()
        );
    }

    private String invokeSubagent(ReActAgentConfig config,
                                  List<ToolCard> toolCards,
                                  String defaultSkillsDir,
                                  Map<String, Object> inputs) {
        String userPrompt = stringValue(inputs, "user_prompt", "");
        String skillsDir = stringValue(inputs, "skills_dir", "default");
        Path resolvedDir = expandUser("default".equals(skillsDir) ? defaultSkillsDir : skillsDir);

        AgentCard card = new AgentCard();
        card.setName("skill_evaluator_subagent");
        card.setDescription("Subagent");
        ReActAgent subAgent = new ReActAgent(card);

        SysOperationCard sysopCard = new SysOperationCard();
        sysopCard.setMode(OperationMode.LOCAL);
        sysopCard.setWorkConfig(new LocalWorkConfig());
        Runner.resourceMgr().addSysOperation(sysopCard);

        ReActAgentConfig subConfig = copyConfig(config);
        subConfig.setSysOperationId(sysopCard.getId());
        subAgent.configure(subConfig);
        subAgent.getAbilityManager().add(toolCards);

        if (Files.exists(resolvedDir)) {
            await(subAgent.registerSkill(resolvedDir.toString()));
        }
        Object result = await(subAgent.invoke(userPrompt, (AgentSessionApi) null));
        return result == null ? null : String.valueOf(result);
    }

    private static Path missingOutputDir() {
        throw new IllegalStateException("SkillEvaluator has no attribute '_output_dir'");
    }

    private static ReActAgentConfig copyConfig(ReActAgentConfig source) {
        ReActAgentConfig copy = new ReActAgentConfig();
        copy.setMemScopeId(source.getMemScopeId());
        copy.setModelName(source.getModelName());
        copy.setModelProvider(source.getModelProvider());
        copy.setApiKey(source.getApiKey());
        copy.setApiBase(source.getApiBase());
        copy.setCustomHeaders(copyMap(source.getCustomHeaders()));
        copy.setPromptTemplateName(source.getPromptTemplateName());
        copy.setPromptTemplate(copyPromptTemplate(source.getPromptTemplate()));
        copy.setMaxIterations(source.getMaxIterations());
        copy.setLlmReturnTokenIds(source.isLlmReturnTokenIds());
        copy.setLlmLogprobs(source.isLlmLogprobs());
        copy.setLlmTopLogprobs(source.getLlmTopLogprobs());
        copy.setModelClientConfig(source.getModelClientConfig());
        copy.setModelConfigObj(source.getModelConfigObj());
        copy.setSysOperationId(source.getSysOperationId());
        copy.setContextEngineConfig(source.getContextEngineConfig());
        copy.setContextProcessors(source.getContextProcessors());
        copy.setWorkspace(source.getWorkspace());
        return copy;
    }

    private static List<Map<String, Object>> copyPromptTemplate(List<Map<String, Object>> source) {
        if (source == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            result.add(copyMap(item));
        }
        return result;
    }

    private static Map<String, Object> copyMap(Map<String, ?> source) {
        if (source == null) {
            return null;
        }
        return new LinkedHashMap<>(source);
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new CompletionException(cause == null ? executionException : cause);
        }
    }

    private static Map<String, Object> asResultMap(Object result) {
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> output = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                output.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return output;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("output", result);
        return output;
    }

    private static Path expandUser(String rawPath) {
        String value = rawPath == null ? "" : rawPath;
        if (value.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (value.startsWith("~/") || value.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home")).resolve(value.substring(2));
        }
        return Path.of(value);
    }

    private static String stringValue(Map<String, Object> inputs, String key, String defaultValue) {
        if (inputs == null || !inputs.containsKey(key)) {
            return defaultValue;
        }
        Object value = inputs.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String env(String key, String defaultValue) {
        String value = environment.get(key);
        return value == null ? defaultValue : value;
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)
                || "y".equals(normalized) || "on".equals(normalized);
    }

    private static Map<String, String> loadDotEnv(Map<String, String> baseEnvironment) {
        Map<String, String> result = new LinkedHashMap<>();
        if (baseEnvironment != null) {
            result.putAll(baseEnvironment);
        }
        Path dotEnv = Path.of(".env");
        if (!Files.isRegularFile(dotEnv)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(dotEnv, StandardCharsets.UTF_8)) {
                parseDotEnvLine(line, result);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return result;
    }

    private static void parseDotEnvLine(String line, Map<String, String> target) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).trim();
        }
        int separator = trimmed.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String key = trimmed.substring(0, separator).trim();
        String value = trimmed.substring(separator + 1).trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        target.putIfAbsent(key, value);
    }
}
