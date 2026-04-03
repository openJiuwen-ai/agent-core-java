// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.skill_creator;

import com.openjiuwen.core.common.logging.Loggers;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SkillCreator - Use LLM to intelligently generate Skills.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.skill_creator.SkillCreator}.
 *
 * <p>Usage example:
 * <pre>
 *     SkillCreator creator = new SkillCreator();
 *     creator.createAgent().thenCompose(v -> 
 *         creator.generate("Create a calculator skill", "/output/path")
 *     ).thenAccept(result -> {
 *         System.out.println("Result: " + result);
 *     });
 * </pre>
 */
public class SkillCreator {

    private ReActAgent agent;

    /**
     * Default constructor.
     */
    public SkillCreator() {
        // Empty constructor, agent is initialized in createAgent()
    }

    /**
     * Create and configure the ReActAgent with skills.
     *
     * <p>This method reads environment variables for configuration:
     * <ul>
     *   <li>SKILLS_DIR - Directory containing skill files (default: "openjiuwen/dev_tools/skill_creator/skills")</li>
     *   <li>FILES_BASE_DIR - Base directory for files (default: current directory)</li>
     *   <li>MAX_ITERATIONS - Maximum agent iterations (default: 25)</li>
     *   <li>API_BASE - API base URL</li>
     *   <li>API_KEY - API key</li>
     *   <li>MODEL_NAME - Model name</li>
     *   <li>MODEL_PROVIDER - Model provider</li>
     *   <li>LLM_SSL_VERIFY - SSL verification flag (default: false)</li>
     * </ul>
     *
     * @return a CompletableFuture that completes when the agent is configured
     */
    public CompletableFuture<Void> createAgent() {
        return CompletableFuture.runAsync(() -> {
            // Get skills directory path
            String skillsDirEnv = getEnvOrDefault("SKILLS_DIR", "");
            Path skillsDir;
            if (skillsDirEnv != null && !skillsDirEnv.isEmpty()) {
                skillsDir = Paths.get(skillsDirEnv).toAbsolutePath();
            } else {
                skillsDir = resolveDefaultSkillsDir();
            }

            // Get files base directory
            String filesBaseDirEnv = getEnvOrDefault("FILES_BASE_DIR", "");
            String filesBaseDir;
            if (filesBaseDirEnv != null && !filesBaseDirEnv.isEmpty()) {
                filesBaseDir = filesBaseDirEnv;
            } else {
                filesBaseDir = Paths.get("").toAbsolutePath().toString();
            }

            // Get max iterations
            int maxIterations = 25;
            String maxIterationsEnv = getEnvOrDefault("MAX_ITERATIONS", "");
            if (maxIterationsEnv != null && !maxIterationsEnv.isEmpty()) {
                try {
                    maxIterations = Integer.parseInt(maxIterationsEnv);
                } catch (NumberFormatException e) {
                    Loggers.AGENT.warning("Invalid MAX_ITERATIONS value, using default: 25");
                }
            }

            // Get API configuration from environment
            String apiBase = getEnvOrDefault("API_BASE", "");
            String apiKey = getEnvOrDefault("API_KEY", "");
            String modelName = getEnvOrDefault("MODEL_NAME", "");
            String modelProvider = getEnvOrDefault("MODEL_PROVIDER", "");
            boolean verifySsl = Boolean.parseBoolean(getEnvOrDefault("LLM_SSL_VERIFY", "false"));

            // Construct agent instance
            this.agent = new ReActAgent(
                    AgentCard.builder()
                            .name("skill_creator_agent")
                            .description("Skill Creator Agent")
                            .build()
            );

            // Create system prompt
            String systemPrompt = "You are an intelligent assistant.\n"
                    + "All user-provided files are located at '" + filesBaseDir + "'\n";
                systemPrompt += "You are running on Windows. Any shell command must be Windows-compatible.\n";
                systemPrompt += "Prefer file tools over shell commands whenever possible. Use writeFile to create or update files.\n";
                systemPrompt += "When creating a new text file with writeFile, pass createIfNotExist=true and provide content as an object like {value: \"...\"}.\n";
                systemPrompt += "Use readFile with encoding UTF-8 for text files. Do not read binary files such as PDF files as text.\n";
                systemPrompt += "Do not duplicate binary assets with writeFile. For PDF or other binary inputs, reference the original absolute path in SKILL.md instead of copying the binary file.\n";
                systemPrompt += "Create only the files needed for the generated skill and place them under the requested output directory.\n";

            // Create and register SysOperation
            SysOperationCard sysopCard = SysOperationCard.builder()
                    .mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder().workDir(null).build())
                    .build();
            Runner.resourceMgr().addSysOperation(sysopCard, null);

            // Configure the agent
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .build()
                    .configureModelClient(
                            modelProvider,
                            apiKey,
                            apiBase,
                            modelName,
                            verifySsl
                    )
                    .configurePromptTemplate(List.of(
                            Map.of("role", "system", "content", systemPrompt)
                    ))
                    .configureMaxIterations(maxIterations)
                    .configureContextEngine(null, null, false);

            config.setSysOperationId(sysopCard.getId());
            this.agent.configure(config);
            addSysOpTool(sysopCard.getId(), "fs", "readFile");
            addSysOpTool(sysopCard.getId(), "fs", "writeFile");
            addSysOpTool(sysopCard.getId(), "fs", "listFiles");
            addSysOpTool(sysopCard.getId(), "fs", "listDirectories");
            addSysOpTool(sysopCard.getId(), "fs", "searchFiles");

            // Register skills if directory exists
            if (Files.exists(skillsDir)) {
                this.agent.registerSkill(skillsDir.toString());
            } else {
                throw new RuntimeException("Directory " + skillsDir + " does not exist.");
            }
        });
    }

    /**
     * Generate a skill based on the given requirement.
     *
     * @param requirement the skill requirement description
     * @param outputPath the output path for generated files
     * @return a CompletableFuture containing the generation result
     */
    public CompletableFuture<Object> generate(String requirement, String outputPath) {
        return CompletableFuture.supplyAsync(() -> {
            if (this.agent == null) {
                throw new IllegalStateException("Agent not initialized. Call createAgent() first.");
            }

            Path outputDir = Paths.get(outputPath);
            String query = requirement + "\nPut all generated files at " + outputDir;

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", query);
            inputs.put("conversation_id", "013");

            return Runner.runAgent(this.agent, inputs, null, null);
        });
    }

    /**
     * Generate a skill based on the given requirement.
     *
     * @param requirement the skill requirement description
     * @param outputPath the output path for generated files (as Path object)
     * @return a CompletableFuture containing the generation result
     */
    public CompletableFuture<Object> generate(String requirement, Path outputPath) {
        return generate(requirement, outputPath.toString());
    }

    /**
     * Get the configured agent.
     *
     * @return the ReActAgent instance, or null if not yet configured
     */
    public ReActAgent getAgent() {
        return agent;
    }

    /**
     * Helper method to get environment variable with default value.
     *
     * @param key the environment variable name
     * @param defaultValue the default value if not set
     * @return the environment variable value or default
     */
    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(key);
        }
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private Path resolveDefaultSkillsDir() {
        List<Path> candidates = List.of(
                Paths.get("openjiuwen", "dev_tools", "skill_creator", "skills"),
                Paths.get("src", "main", "resources", "openjiuwen", "dev_tools", "skill_creator", "skills"),
                Paths.get("agent-core-java-myfork", "src", "main", "resources",
                        "openjiuwen", "dev_tools", "skill_creator", "skills")
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }

        throw new RuntimeException("Could not resolve SkillCreator skills directory. Tried: "
                + candidates.stream().map(path -> path.toAbsolutePath().normalize().toString()).toList());
    }

    private void addSysOpTool(String sysOperationId, String operationName, String toolName) {
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOperationId, operationName, toolName);
        if (toolCard != null) {
            this.agent.getAbilityManager().add(toolCard);
        }
    }
}
