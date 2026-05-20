/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
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
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.skill_evaluator.SkillEvaluator} while staying
 * aligned with the current Java 17 stack.
 */
public class SkillEvaluator {
  static final String DEFAULT_OUTPUT_DIR = "outputs/evaluations";
  private static final String DEFAULT_CONVERSATION_ID = "skill_eval_001";
  private static final String DEFAULT_SUBAGENT_CONVERSATION_ID = "skill_eval_subagent";

  private ReActAgent agent;
  private ReActAgentConfig config;
  private final List<Object> tools = new ArrayList<>();
  private Path skillsDir;
  private Path outputDir;

  /**
   * Create and configure the evaluation agent from environment variables.
   *
   * @return async completion signal
   */
  public CompletableFuture<Void> createAgent() {
    return CompletableFuture.runAsync(
        () -> {
          Path resolvedSkillsDir = resolveSkillsDir();
          Path resolvedOutputDir = resolvePathConfig("OUTPUT_DIR", Path.of(DEFAULT_OUTPUT_DIR));

          this.skillsDir = resolvedSkillsDir;
          this.outputDir = resolvedOutputDir;
          this.agent =
              new ReActAgent(
                  AgentCard.builder()
                      .name("skill_evaluator_agent")
                      .description("Skill Evaluator Agent")
                      .build());

          Path filesBaseDir = Paths.get("").toAbsolutePath().normalize();
          String systemPrompt =
              String.join(
                  "\n",
                  "You are an intelligent assistant.",
                  "All user-provided files are located at '" + filesBaseDir + "'",
                  "Put all generated files into '"
                      + resolvedOutputDir.toAbsolutePath().normalize()
                      + "'.",
                  "You may use tools when necessary.");
          int maxIterations = parseIntConfig("MAX_ITERATIONS", 25);

          SysOperationCard sysopCard =
              SysOperationCard.builder()
                  .mode(OperationMode.LOCAL)
                  .workConfig(LocalWorkConfig.builder().workDir(null).build())
                  .build();
          Runner.resourceMgr().addSysOperation(sysopCard, null);

          this.config =
              ReActAgentConfig.builder()
                  .build()
                  .configureModelClient(
                      resolveStringConfig("MODEL_PROVIDER", ""),
                      resolveStringConfig("API_KEY", ""),
                      resolveStringConfig("API_BASE", ""),
                      resolveStringConfig("MODEL_NAME", ""),
                      Boolean.parseBoolean(resolveStringConfig("LLM_SSL_VERIFY", "false")))
                  .configurePromptTemplate(
                      List.of(Map.of("role", "system", "content", systemPrompt)))
                  .configureMaxIterations(maxIterations)
                  .configureContextEngine(null, null, false);
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
          this.agent.registerSkill(resolvedSkillsDir.toString());
        });
  }

  /**
   * Evaluate a skill and write reports into the requested output directory.
   *
   * @param skillPath skill path
   * @param requirement extra requirement text
   * @param outputPath output directory
   * @return async evaluation result object
   */
  public CompletableFuture<Object> evaluate(
      String skillPath, String requirement, String outputPath) {
    return CompletableFuture.supplyAsync(
        () -> {
          ensureAgentReady();
          Path resolvedSkillPath = Paths.get(skillPath).toAbsolutePath().normalize();
          Path reportDir = resolveReportDir(outputPath, outputDir);
          String query = buildEvaluationQuery(resolvedSkillPath, reportDir, requirement);

          Map<String, Object> inputs = new LinkedHashMap<>();
          inputs.put("query", query);
          inputs.put("conversation_id", DEFAULT_CONVERSATION_ID);
          return Runner.runAgent(agent, inputs, null, null);
        });
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Object> evaluate(Path skillPath, String requirement, Path outputPath) {
    return evaluate(
        skillPath.toAbsolutePath().normalize().toString(),
        requirement,
        outputPath != null ? outputPath.toAbsolutePath().normalize().toString() : null);
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Object> evaluate(Path skillPath) {
    return evaluate(skillPath, "", null);
  }

  /** Auto-generated for codecheck compliance. */
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
    StringBuilder query =
        new StringBuilder()
            .append("Help me evaluate the skill in '")
            .append(skillPath.toAbsolutePath().normalize())
            .append("'.\n")
            .append("Save the evaluation report to '")
            .append(reportDir.toAbsolutePath().normalize())
            .append("' folder.");
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
    List<Path> candidates =
        List.of(
            Path.of("openjiuwen", "dev_tools", "skill_evaluator", "skills"),
            Path.of(
                "src",
                "main",
                "resources",
                "openjiuwen",
                "dev_tools",
                "skill_evaluator",
                "skills"));
    for (Path candidate : candidates) {
      Path normalized = candidate.toAbsolutePath().normalize();
      if (Files.isDirectory(normalized)) {
        return normalized;
      }
    }
    throw new IllegalStateException(
        "Could not resolve SkillEvaluator skills directory. Tried: "
            + candidates.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .toList());
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
        source.getContextProcessors() != null
            ? new ArrayList<>(source.getContextProcessors())
            : null;

    return ReActAgentConfig.builder()
        .memScopeId(source.getMemScopeId())
        .modelName(source.getModelName())
        .modelProvider(source.getModelProvider())
        .apiKey(source.getApiKey())
        .apiBase(source.getApiBase())
        .promptTemplateName(source.getPromptTemplateName())
        .promptTemplate(promptTemplate)
        .customHeaders(headers)
        .maxIterations(source.getMaxIterations())
        .modelClientConfig(source.getModelClientConfig())
        .modelConfigObj(source.getModelConfigObj())
        .sysOperationId(source.getSysOperationId())
        .contextEngineConfig(source.getContextEngineConfig())
        .contextProcessors(contextProcessors)
        .build();
  }

  private void ensureAgentReady() {
    if (agent == null || config == null || skillsDir == null || outputDir == null) {
      throw new IllegalStateException("Agent not initialized. Call createAgent() first.");
    }
  }

  private Path resolveSkillsDir() {
    String configured = resolveStringConfig("SKILLS_DIR", "");
    if (configured != null && !configured.isBlank()) {
      Path path = Paths.get(configured).toAbsolutePath().normalize();
      if (!Files.isDirectory(path)) {
        throw new IllegalStateException("Configured SKILLS_DIR does not exist: " + path);
      }
      return path;
    }
    return resolveDefaultSkillsDir();
  }

  private static Path resolvePathConfig(String key, Path defaultPath) {
    String configured = resolveStringConfig(key, "");
    Path path = configured.isBlank() ? defaultPath : Path.of(configured);
    return path.toAbsolutePath().normalize();
  }

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

  private void addSysOpTool(String sysOperationId, String operationName, String toolName) {
    Object toolCard =
        Runner.resourceMgr().getSysOpToolCards(sysOperationId, operationName, toolName);
    if (toolCard != null) {
      tools.add(toolCard);
      agent.getAbilityManager().add(toolCard);
    }
  }

  private LocalFunction createSubagentTool() {
    ToolCard card =
        ToolCard.builder()
            .id("create_subagent")
            .name("create_subagent")
            .description(
                "Create a subagent that loads evaluation skills and executes a focused evaluation"
                    + " subtask.")
            .inputParams(buildSubagentInputSchema())
            .build();
    return new LocalFunction(
        card,
        inputs -> {
          String userPrompt = String.valueOf(inputs.getOrDefault("user_prompt", ""));
          String requestedSkillsDir = String.valueOf(inputs.getOrDefault("skills_dir", "default"));
          Path subagentSkillsDir =
              "default".equals(requestedSkillsDir)
                  ? skillsDir
                  : Paths.get(requestedSkillsDir).toAbsolutePath().normalize();
          if (!Files.isDirectory(subagentSkillsDir)) {
            throw new IllegalStateException(
                "Subagent skills directory does not exist: " + subagentSkillsDir);
          }

          ReActAgent subAgent =
              new ReActAgent(
                  AgentCard.builder()
                      .name("skill_evaluator_subagent")
                      .description("Skill Evaluator Subagent")
                      .build());
          SysOperationCard sysopCard =
              SysOperationCard.builder()
                  .mode(OperationMode.LOCAL)
                  .workConfig(LocalWorkConfig.builder().workDir(null).build())
                  .build();
          Runner.resourceMgr().addSysOperation(sysopCard, null);

          ReActAgentConfig subConfig = copyConfig(config);
          subConfig.setSysOperationId(sysopCard.getId());
          subAgent.configure(subConfig);
          for (Object tool : tools) {
            subAgent.getAbilityManager().add(tool);
          }
          subAgent.registerSkill(subagentSkillsDir.toString());

          Map<String, Object> runInputs = new LinkedHashMap<>();
          runInputs.put("query", userPrompt);
          runInputs.put("conversation_id", DEFAULT_SUBAGENT_CONVERSATION_ID);
          Object result = Runner.runAgent(subAgent, runInputs, null, null);
          return extractOutput(result);
        });
  }

  private static Map<String, Object> buildSubagentInputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put(
        "user_prompt",
        Map.of(
            "type", "string",
            "description", "Instruction or task description for the subagent."));
    properties.put(
        "skills_dir",
        Map.of(
            "type", "string",
            "description",
                "Directory from which the subagent loads skills. Use 'default' to reuse the"
                    + " evaluator skills."));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.of("user_prompt"));
    return schema;
  }

  @SuppressWarnings("unchecked")
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
