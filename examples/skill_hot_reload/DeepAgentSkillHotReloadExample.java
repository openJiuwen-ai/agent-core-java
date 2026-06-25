/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.skill_hot_reload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import examples.utils.SharedExampleApiConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates Skill hot-reload using DeepAgent with SkillUseRail.
 *
 * <p>Key mechanism: {@link SkillUseRail} automatically checks mtime signatures
 * of skill directories in its {@code beforeModelCall} callback. If the signature
 * has changed (skill added, modified, or deleted), the SkillManager is refreshed
 * incrementally. The updated skill information is then injected into the system
 * prompt for the next model call.</p>
 *
 * <p>Expected output:</p>
 * <pre>
 * === DeepAgent Skill Hot-Reload Demo ===
 * [Step 1] Initial skills loaded: [skill_a] (count: 1)
 * [Step 2] Running DeepAgent - skill info auto-injected by SkillUseRail
 * [Hot-Reload] Skill signature changed, refreshing incrementally...
 * [Hot-Reload] Updated skills: [skill_a, skill_b] (count: 2)
 * [Step 3] Running DeepAgent again - skill_a + skill_b available
 * [Hot-Reload] Skill signature changed, refreshing incrementally...
 * [Hot-Reload] Updated skills: [skill_a] (count: 1)
 * [Step 5] Running DeepAgent again - skill_b auto-removed
 * === Demo Complete ===
 * </pre>
 */
public final class DeepAgentSkillHotReloadExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "deep_agent_skill_hot_reload_example";
    private static final String SYS_OP_ID = "deep_agent_skill_hot_reload_sysop";

    private DeepAgentSkillHotReloadExample() {
    }

    public static void main(String[] args) throws Exception {
        Path moduleDir = resolveModuleDir();
        Path skillsDir = resolvePathConfig("SKILLS_DIR", moduleDir.resolve("skills"));
        Path filesBaseDir = resolvePathConfig("FILES_BASE_DIR", moduleDir.resolve("data"));
        Path outputDir = resolvePathConfig("OUTPUT_DIR", moduleDir.resolve("output"));
        int maxIterations = Integer.parseInt(resolveStringConfig("MAX_ITERATIONS", "5"));

        Files.createDirectories(skillsDir);
        Files.createDirectories(filesBaseDir);
        Files.createDirectories(outputDir);

        System.out.println("=== DeepAgent Skill Hot-Reload Demo ===");

        // Create initial skill_a for testing
        Path skillADir = skillsDir.resolve("skill_a");
        Files.createDirectories(skillADir);
        Files.writeString(skillADir.resolve("SKILL.md"),
                "---\ndescription: Skill A for initial test\n---\n# Skill A\n\nThis is skill A for DeepAgent hot-reload demo.");

        // Create DeepAgent with SkillUseRail
        DeepAgent deepAgent = createDeepAgent(filesBaseDir, outputDir, maxIterations, skillsDir);

        // Ensure DeepAgent is initialized (rails are registered)
        deepAgent.ensureInitialized();

        // Get SkillUseRail to monitor skill changes
        SkillUseRail skillUseRail = findSkillUseRail(deepAgent);
        System.out.println("[Step 1] Initial skills loaded: " + skillUseRail.registeredSkillNames()
                + " (count: " + skillUseRail.registeredSkillNames().size() + ")");

        // Step 2: Run DeepAgent - SkillUseRail auto-injects skill info
        System.out.println("[Step 2] Running DeepAgent with " + skillUseRail.registeredSkillNames().size() + " skills...");
        Map<String, Object> result1 = runDeepAgent(deepAgent,
                "How many skills do you currently have? List all skill names and their descriptions.");
        System.out.println("[Step 2] DeepAgent response: " + extractOutput(result1));

        // Hot-reload: add skill_b and modify skill_a
        Path skillBDir = skillsDir.resolve("skill_b");
        Files.createDirectories(skillBDir);
        Files.writeString(skillBDir.resolve("SKILL.md"),
                "---\ndescription: Skill B added for hot-reload\n---\n# Skill B\n\nThis is skill B, dynamically added.");
        Files.writeString(skillADir.resolve("SKILL.md"),
                "---\ndescription: Modified Skill A\n---\n# Skill A (Modified)\n\nThis is the modified version of skill A.");
        forceMtimeChange(skillADir.resolve("SKILL.md"));

        // Step 3: Run DeepAgent again - SkillUseRail auto-detects signature change
        System.out.println("[Step 3] Running DeepAgent again after adding skill_b and modifying skill_a...");
        Map<String, Object> result2 = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names and their descriptions.");
        System.out.println("[Step 3] DeepAgent response: " + extractOutput(result2));
        System.out.println("[Step 4] Skills after add hot-reload: " + skillUseRail.registeredSkillNames()
                + " (count: " + skillUseRail.registeredSkillNames().size() + ")");

        // Hot-reload: delete skill_b
        Files.deleteIfExists(skillBDir.resolve("SKILL.md"));
        Files.deleteIfExists(skillBDir);

        // Step 5: Run DeepAgent again - SkillUseRail auto-detects skill deletion
        System.out.println("[Step 5] Running DeepAgent again after deleting skill_b...");
        Map<String, Object> result3 = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names and their descriptions.");
        System.out.println("[Step 5] DeepAgent response: " + extractOutput(result3));
        System.out.println("[Step 6] Skills after delete hot-reload: " + skillUseRail.registeredSkillNames()
                + " (count: " + skillUseRail.registeredSkillNames().size() + ")");

        Runner.stop();
        System.out.println("=== Demo Complete ===");
    }

    /**
     * Create a DeepAgent with SkillUseRail for automatic skill hot-reload.
     *
     * <p>The SkillUseRail's {@code beforeModelCall} callback automatically:
     * <ol>
     *   <li>Builds current mtime signature of skill directories</li>
     *   <li>Compares with last known signature</li>
     *   <li>If changed: calls {@code skillManager.refreshIncrementally()}</li>
     *   <li>Injects updated skill info into the system prompt</li>
     * </ol>
     * </p>
     */
    private static DeepAgent createDeepAgent(Path filesBaseDir, Path outputDir,
                                             int maxIterations, Path skillsDir) {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("DeepAgent with Skill Hot-Reload")
                .build();

        Workspace workspace = Workspace.builder()
                .rootPath(filesBaseDir.toString())
                .language("cn")
                .build();

        String systemPrompt = "你是智能助手。\n"
                + "用户提供的文件位于 '" + filesBaseDir + "'\n"
                + "生成的文件放入 " + outputDir + "\n"
                + "当前系统为 Windows。\n"
                + "你可以使用工具完成任务。\n";

        // Create SkillUseRail with skill directory configuration
        SkillUseRail skillUseRail = new SkillUseRail(
                List.of(skillsDir.toString()),
                "all",
                List.of(),
                List.of()
        );

        DeepAgentConfig config = DeepAgentConfig.builder()
                .systemPrompt(systemPrompt)
                .maxIterations(maxIterations)
                .language("cn")
                .workspacePath(filesBaseDir.toString())
                .rails(List.of(skillUseRail))
                .skillDirectories(List.of(skillsDir.toString()))
                .skillMode("all")
                .build();

        // Configure model
        configureModel(config);

        DeepAgent deepAgent = HarnessFactory.createDeepAgent(agentCard, config, workspace);

        // Register SysOperation for tools
        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id(SYS_OP_ID)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(filesBaseDir.toString()).build())
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);

        // Add SysOp tools
        addSysOpTools(deepAgent, sysOpCard.getId());

        return deepAgent;
    }

    private static void configureModel(DeepAgentConfig config) {
        Map<String, Object> modelConfig = Map.of(
                "model", SharedExampleApiConfigLoader.getModelName()
        );
        Map<String, Object> backendConfig = Map.of(
                "client_provider", SharedExampleApiConfigLoader.getModelProvider(),
                "api_key", SharedExampleApiConfigLoader.getApiKey(),
                "api_base", SharedExampleApiConfigLoader.getApiBase(),
                "verify_ssl", SharedExampleApiConfigLoader.getSslVerify()
        );
        config.setModel(modelConfig);
        config.setBackend(backendConfig);
    }

    private static void addSysOpTools(DeepAgent deepAgent, String sysOperationId) {
        addSysOpTool(deepAgent, sysOperationId, "fs", "readFile");
        addSysOpTool(deepAgent, sysOperationId, "code", "executeCode");
        addSysOpTool(deepAgent, sysOperationId, "shell", "executeCmd");
    }

    private static void addSysOpTool(DeepAgent deepAgent, String sysOperationId,
                                     String operationName, String toolName) {
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOperationId, operationName, toolName);
        if (toolCard != null) {
            deepAgent.getAgent().getAbilityManager().add(toolCard);
        }
    }

    /**
     * Find the SkillUseRail from DeepAgent's registered rails.
     */
    private static SkillUseRail findSkillUseRail(DeepAgent deepAgent) {
        for (Object rail : deepAgent.getRegisteredRails()) {
            if (rail instanceof SkillUseRail skillUseRail) {
                return skillUseRail;
            }
        }
        return null;
    }

    private static Map<String, Object> runDeepAgent(DeepAgent deepAgent, String query) {
        Map<String, Object> inputs = Map.of(
                "query", query,
                "conversation_id", AGENT_ID + "_" + System.currentTimeMillis()
        );
        return (Map<String, Object>) deepAgent.run(inputs);
    }

    private static String extractOutput(Map<String, Object> result) {
        if (result == null) return "null";
        Object output = result.get("output");
        if (output != null) return output.toString();
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

    private static void forceMtimeChange(Path file) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        file.toFile().setLastModified(file.toFile().lastModified() + 2000);
    }

    private static Path resolveModuleDir() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("pom.xml"))) {
            return cwd;
        }
        Path moduleDir = cwd.resolve("agent-core-java-examples").resolve("skill-hot-reload");
        if (Files.exists(moduleDir.resolve("pom.xml"))) {
            return moduleDir;
        }
        return cwd;
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
}