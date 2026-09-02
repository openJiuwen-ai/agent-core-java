/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * SkillEvaluator - Use LLM plus bundled evaluation skills to assess another skill.
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.skill_evaluator.SkillEvaluator} while staying
 * aligned with the current Java 17 stack.
 * 
 * @since 0.1.7
 */
public class SkillEvaluator {
    static final String DEFAULT_OUTPUT_DIR = "outputs/evaluations";
    private static final String DEFAULT_CONVERSATION_ID = "skill_eval_001";
    private static final String DEFAULT_SUBAGENT_CONVERSATION_ID = "skill_eval_subagent";

    private ReActAgent agent;
    private ReActAgentConfig config;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Object> tools = new ArrayList<>();
    private Path skillsDir;
    private Path targetSkillsRoot;
    private Path outputDir;

    /**
     * Create and configure the evaluation agent from environment variables.
     * 
     * @return async completion signal
     * @since 0.1.7
     */
    public CompletableFuture<Void> createAgent() {
        return OpenJiuwenExecutors.runBackgroundAsync(() -> {
            Path resolvedSkillsDir = resolveDefaultSkillsDir();
            Path resolvedTargetSkillsRoot = resolveTargetSkillsRoot();
            Path resolvedOutputDir = resolvePathConfig("OUTPUT_DIR", Path.of(DEFAULT_OUTPUT_DIR));

            this.skillsDir = resolvedSkillsDir;
            this.targetSkillsRoot = resolvedTargetSkillsRoot;
            this.outputDir = resolvedOutputDir;
            this.agent = new ReActAgent(
                    AgentCard.builder().name("skill_evaluator_agent").description("Skill Evaluator Agent").build());

            Path filesBaseDir = Paths.get("").toAbsolutePath().normalize();
            String systemPrompt = String.join("\n", "You are an intelligent assistant.",
                    "All user-provided files are located at '" + filesBaseDir + "'",
                    "Put all generated files into '" + resolvedOutputDir.toAbsolutePath().normalize() + "'.",
                    "You may use tools when necessary.");
            int maxIterations = parseIntConfig("MAX_ITERATIONS", 25);

            SysOperationCard sysopCard = SysOperationCard.builder().mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder().workDir(null).build()).build();
            Runner.resourceMgr().addSysOperation(sysopCard, null);

            this.config = ReActAgentConfig.builder().build()
                    .configureModelClient(resolveStringConfig("MODEL_PROVIDER", ""), resolveStringConfig("API_KEY", ""),
                            resolveStringConfig("API_BASE", ""), resolveStringConfig("MODEL_NAME", ""),
                            Boolean.parseBoolean(resolveStringConfig("LLM_SSL_VERIFY", "false")))
                    .configurePromptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                    .configureMaxIterations(maxIterations).configureContextEngine(null, null, false);
            this.config.setSysOperationId(sysopCard.getId());
            this.agent.configure(this.config);

            tools.clear();
            addSysOpTool(sysopCard.getId(), "fs", "readFile");
            addSysOpTool(sysopCard.getId(), "fs", "writeFile");
            addSysOpTool(sysopCard.getId(), "fs", "listFiles");
            addSysOpTool(sysopCard.getId(), "fs", "listDirectories");
            addSysOpTool(sysopCard.getId(), "fs", "searchFiles");
            addSysOpTool(sysopCard.getId(), "shell", "executeCmd");
            addSysOpTool(sysopCard.getId(), "code", "executeCode");

            this.agent.getAbilityManager().add(createSubagentTool());
            this.agent.registerSkill(resolvedSkillsDir.toString(), Path.of("").toAbsolutePath().normalize());
        });
    }

    /**
     * Evaluate a skill and write reports into the requested output directory.
     * 
     * @param skillPath relative skill path within {@code SKILLS_DIR}
     * @param requirement extra requirement text
     * @param outputPath output directory
     * @return async evaluation result object
     * @since 0.1.7
     */
    public CompletableFuture<Object> evaluate(String skillPath, String requirement, String outputPath) {
        return OpenJiuwenExecutors.supplyBackgroundAsync(() -> {
            ensureAgentReady();
            Path resolvedSkillPath = resolveTargetSkillPath(skillPath, targetSkillsRoot);
            Path reportDir = resolveReportDir(outputPath, outputDir);
            String query = buildEvaluationQuery(resolvedSkillPath, reportDir, requirement);

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", query);
            inputs.put("conversation_id", DEFAULT_CONVERSATION_ID);
            return Runner.runAgent(agent, inputs, null, null);
        });
    }

    /**
     * evaluate.
     * 
     * @param skillPath relative skill path within {@code SKILLS_DIR}
     * @param requirement requirement
     * @param outputPath outputPath
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Object> evaluate(Path skillPath, String requirement, Path outputPath) {
        return evaluate(skillPath.toString(), requirement,
                outputPath != null ? outputPath.toAbsolutePath().normalize().toString() : null);
    }

    /**
     * evaluate.
     * 
     * @param skillPath skillPath
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Object> evaluate(Path skillPath) {
        return evaluate(skillPath, "", null);
    }

    /**
     * getAgent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ReActAgent getAgent() {
        return agent;
    }

    Path getSkillsDir() {
        return skillsDir;
    }

    Path getOutputDir() {
        return outputDir;
    }

    static String buildEvaluationQuery(Path skillPath, Path reportDir, String requirement) {
        StringBuilder query = new StringBuilder().append("Help me evaluate the skill in '")
                .append(skillPath.toAbsolutePath().normalize()).append("'.\n").append("Save the evaluation report to '")
                .append(reportDir.toAbsolutePath().normalize()).append("' folder.");
        if (requirement != null && !requirement.isBlank()) {
            query.append("\n").append(requirement.trim());
        }
        return query.toString();
    }

    static Path resolveReportDir(String outputPath, Path fallbackOutputDir) {
        if (outputPath != null && !outputPath.isBlank()) {
            return Paths.get(outputPath).toAbsolutePath().normalize();
        }
        return fallbackOutputDir.toAbsolutePath().normalize();
    }

    static Path resolveDefaultSkillsDir() {
        List<Path> candidates = List.of(Path.of("openjiuwen", "dev_tools", "skill_evaluator", "skills"),
                Path.of("src", "main", "resources", "openjiuwen", "dev_tools", "skill_evaluator", "skills"));
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException("Could not resolve SkillEvaluator skills directory. Tried: "
                + candidates.stream().map(path -> path.toAbsolutePath().normalize().toString()).toList());
    }

    static ReActAgentConfig copyConfig(ReActAgentConfig source) {
        List<Map<String, String>> promptTemplate = new ArrayList<>();
        if (source.getPromptTemplate() != null) {
            for (Map<String, String> item : source.getPromptTemplate()) {
                promptTemplate.add(item != null ? new LinkedHashMap<>(item) : null);
            }
        }
        Map<String, String> headers =
            source.getCustomHeaders() != null ? new LinkedHashMap<>(source.getCustomHeaders()) : null;
        List<Object> contextProcessors =
            source.getContextProcessors() != null ? new ArrayList<>(source.getContextProcessors()) : null;

        return ReActAgentConfig.builder().memScopeId(source.getMemScopeId()).modelName(source.getModelName())
                .modelProvider(source.getModelProvider()).apiKey(source.getApiKey()).apiBase(source.getApiBase())
                .promptTemplateName(source.getPromptTemplateName()).promptTemplate(promptTemplate)
                .customHeaders(headers).maxIterations(source.getMaxIterations())
                .modelClientConfig(source.getModelClientConfig()).modelConfigObj(source.getModelConfigObj())
                .sysOperationId(source.getSysOperationId()).contextEngineConfig(source.getContextEngineConfig())
                .contextProcessors(contextProcessors).build();
    }

    /**
     * ensureAgentReady.
     * 
     * @since 0.1.7
     */
    private void ensureAgentReady() {
        if (agent == null || config == null || skillsDir == null || targetSkillsRoot == null || outputDir == null) {
            throw new IllegalStateException("Agent not initialized. Call createAgent() first.");
        }
    }

    private Path resolveTargetSkillsRoot() {
        String configured = resolveStringConfig("SKILLS_DIR", "");
        Path path = configured == null || configured.isBlank()
                ? Path.of("")
                : Paths.get(configured);
        try {
            Path realPath = path.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(realPath)) {
                throw new IllegalStateException("Configured SKILLS_DIR is not a directory: " + realPath);
            }
            return realPath;
        } catch (IOException e) {
            throw new IllegalStateException("Configured SKILLS_DIR does not exist: " + path, e);
        }
    }

    static Path resolveTargetSkillPath(String skillPath, Path skillsRoot) {
        if (skillPath == null || skillPath.isBlank()) {
            throw new IllegalArgumentException("Skill path must not be blank.");
        }
        Path requestedPath = Path.of(skillPath);
        if (requestedPath.isAbsolute()) {
            throw new SecurityException("Skill path must be relative to SKILLS_DIR.");
        }
        for (Path segment : requestedPath) {
            if ("..".equals(segment.toString())) {
                throw new SecurityException("Skill path must not contain '..'.");
            }
        }

        try {
            Path realSkillsRoot = skillsRoot.toRealPath();
            Path targetPath = realSkillsRoot.resolve(requestedPath).normalize();
            if (!targetPath.startsWith(realSkillsRoot)) {
                throw new SecurityException("Skill path is outside SKILLS_DIR.");
            }
            Path realTargetPath = targetPath.toRealPath();
            if (!realTargetPath.startsWith(realSkillsRoot)) {
                throw new SecurityException("Skill path is outside SKILLS_DIR.");
            }
            if (!Files.isDirectory(realTargetPath)) {
                throw new IllegalArgumentException("Skill path must identify a directory.");
            }
            if (!Files.isRegularFile(realTargetPath.resolve("SKILL.md"))
                    && !Files.isRegularFile(realTargetPath.resolve("Skill.md"))) {
                throw new IllegalArgumentException("Skill directory does not contain SKILL.md.");
            }
            return realTargetPath;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve skill path within SKILLS_DIR.", e);
        }
    }

    /**
     * resolvePathConfig.
     * 
     * @param key key
     * @param defaultPath defaultPath
     * @return the result
     * @since 0.1.7
     */
    private static Path resolvePathConfig(String key, Path defaultPath) {
        String configured = resolveStringConfig(key, "");
        Path path = configured.isBlank() ? defaultPath : Path.of(configured);
        return path.toAbsolutePath().normalize();
    }

    /**
     * resolveStringConfig.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private static String resolveStringConfig(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return defaultValue;
    }

    /**
     * parseIntConfig.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private static int parseIntConfig(String key, int defaultValue) {
        String configured = resolveStringConfig(key, "");
        if (configured.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(configured);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * addSysOpTool.
     * 
     * @param sysOperationId sysOperationId
     * @param operationName operationName
     * @param toolName toolName
     * @since 0.1.7
     */
    private void addSysOpTool(String sysOperationId, String operationName, String toolName) {
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOperationId, operationName, toolName);
        if (toolCard != null) {
            tools.add(toolCard);
            agent.getAbilityManager().add(toolCard);
        }
    }

    /**
     * createSubagentTool.
     * 
     * @return the result
     * @since 0.1.7
     */
    private LocalFunction createSubagentTool() {
        ToolCard card = ToolCard.builder().id("create_subagent").name("create_subagent").description(
                "Create a subagent that loads evaluation skills and executes a focused evaluation" + " subtask.")
                .inputParams(buildSubagentInputSchema()).build();
        return new LocalFunction(card, inputs -> {
            String userPrompt = String.valueOf(inputs.getOrDefault("user_prompt", ""));
            String requestedSkillsDir = String.valueOf(inputs.getOrDefault("skills_dir", "default"));
            Path subagentSkillsDir = "default".equals(requestedSkillsDir)
                    ? skillsDir
                    : Paths.get(requestedSkillsDir).toAbsolutePath().normalize();
            if (!Files.isDirectory(subagentSkillsDir)) {
                throw new IllegalStateException("Subagent skills directory does not exist: " + subagentSkillsDir);
            }

            ReActAgent subAgent = new ReActAgent(AgentCard.builder().name("skill_evaluator_subagent")
                    .description("Skill Evaluator Subagent").build());
            SysOperationCard sysopCard = SysOperationCard.builder().mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder().workDir(null).build()).build();
            Runner.resourceMgr().addSysOperation(sysopCard, null);

            ReActAgentConfig subConfig = copyConfig(config);
            subConfig.setSysOperationId(sysopCard.getId());
            subAgent.configure(subConfig);
            for (Object tool : tools) {
                subAgent.getAbilityManager().add(tool);
            }
            subAgent.registerSkill(subagentSkillsDir.toString(), Path.of("").toAbsolutePath().normalize());

            Map<String, Object> runInputs = new LinkedHashMap<>();
            runInputs.put("query", userPrompt);
            runInputs.put("conversation_id", DEFAULT_SUBAGENT_CONVERSATION_ID);
            Object result = Runner.runAgent(subAgent, runInputs, null, null);
            return extractOutput(result);
        });
    }

    /**
     * buildSubagentInputSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> buildSubagentInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("user_prompt",
                Map.of("type", "string", "description", "Instruction or task description for the subagent."));
        properties.put("skills_dir", Map.of("type", "string", "description",
                "Directory from which the subagent loads skills. Use 'default' to reuse the" + " evaluator skills."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("user_prompt"));
        return schema;
    }

    @SuppressWarnings("unchecked")
    /**
     * extractOutput.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private static String extractOutput(Object result) {
        if (result instanceof Map<?, ?> map) {
            Object output = map.get("output");
            if (output == null) {
                return String.valueOf(result);
            }
            if (output instanceof Map<?, ?> outputMap) {
                Object response = outputMap.get("response");
                if (response != null) {
                    return String.valueOf(response);
                }
            }
            return String.valueOf(output);
        }
        return Objects.toString(result, "");
    }
}
