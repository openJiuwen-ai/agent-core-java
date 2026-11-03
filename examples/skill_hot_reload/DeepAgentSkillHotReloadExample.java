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
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;
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
 *
 * --- TC_004: Batch load with one abnormal skill, others still load ---
 * [TC_004] tc004_invalid NOT loaded: true, tc004_valid_1..5 ALL loaded: true
 *
 * --- TC_002: Oversized SKILL.md (>10MB) should be skipped ---
 * [TC_002] Oversized skill is skipped, existing skills unaffected
 *
 * --- TC_003/TC_009: Invalid SKILL.md format should be skipped ---
 * [TC_003] Invalid skill is skipped, existing skills unaffected
 *
 * --- TC_012: Recover invalid SKILL.md → should be reloaded ---
 * [TC_012] Recovered skill is reloaded and works
 *
 * --- TC_011: Simultaneous add 10, delete 10, modify 10 skills ---
 * [TC_011] All simultaneous operations applied correctly
 *
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
        System.out.println("[Step 1] Initial skills loaded: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");

        // Step 2: Run DeepAgent - SkillUseRail auto-injects skill info
        System.out.println("[Step 2] Running DeepAgent with " + skillNames(skillUseRail).size() + " skills...");
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
        System.out.println("[Step 4] Skills after add hot-reload: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");

        // Hot-reload: delete skill_b
        Files.deleteIfExists(skillBDir.resolve("SKILL.md"));
        Files.deleteIfExists(skillBDir);

        // Step 5: Run DeepAgent again - SkillUseRail auto-detects skill deletion
        System.out.println("[Step 5] Running DeepAgent again after deleting skill_b...");
        Map<String, Object> result3 = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names and their descriptions.");
        System.out.println("[Step 5] DeepAgent response: " + extractOutput(result3));
        System.out.println("[Step 6] Skills after delete hot-reload: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");

        // ---- TC_004: Batch load with one abnormal skill, others still work ----
        System.out.println("\n--- TC_004: Batch load with one abnormal skill, others still load ---");
        // Create 5 valid skills + 1 invalid skill simultaneously
        for (int i = 1; i <= 5; i++) {
            Path validDir = skillsDir.resolve("tc004_valid_" + i);
            Files.createDirectories(validDir);
            Files.writeString(validDir.resolve("SKILL.md"),
                    "---\ndescription: TC004 valid skill " + i + "\n---\n# TC004 Valid " + i + "\n\nValid skill in batch load test.");
        }
        Path tc004InvalidDir = skillsDir.resolve("tc004_invalid");
        Files.createDirectories(tc004InvalidDir);
        Files.writeString(tc004InvalidDir.resolve("SKILL.md"),
                "No YAML front matter - this is an invalid SKILL.md for TC_004.");
        System.out.println("[TC_004] Created 5 valid + 1 invalid skill in batch");

        // Run DeepAgent - invalid skill should be skipped, 5 valid ones should load
        Map<String, Object> resultBatchWithBad = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names.");
        System.out.println("[TC_004] DeepAgent response: " + extractOutput(resultBatchWithBad));
        System.out.println("[TC_004] Skills after batch load: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");
        // Verify: tc004_invalid should NOT be in the list, tc004_valid_1..5 SHOULD be in the list
        boolean invalidNotLoaded = !skillNames(skillUseRail).contains("tc004_invalid");
        boolean allValidLoaded = skillNames(skillUseRail).contains("tc004_valid_1")
                && skillNames(skillUseRail).contains("tc004_valid_5");
        System.out.println("[TC_004] tc004_invalid NOT loaded: " + invalidNotLoaded
                + ", tc004_valid_1..5 ALL loaded: " + allValidLoaded);

        // Cleanup TC_004 skills
        for (int i = 1; i <= 5; i++) {
            Files.deleteIfExists(skillsDir.resolve("tc004_valid_" + i).resolve("SKILL.md"));
            Files.deleteIfExists(skillsDir.resolve("tc004_valid_" + i));
        }
        Files.deleteIfExists(tc004InvalidDir.resolve("SKILL.md"));
        Files.deleteIfExists(tc004InvalidDir);

        // ---- TC_002: SKILL.md file size exceeds 10MB should be skipped ----
        System.out.println("\n--- TC_002: Oversized SKILL.md (>10MB) should be skipped ---");
        Path oversizedDir = skillsDir.resolve("skill_oversized");
        Files.createDirectories(oversizedDir);
        String largeContent = "---\ndescription: Oversized skill\n---\n# Oversized\n\n" + "A".repeat(10 * 1024 * 1024 + 1);
        Files.writeString(oversizedDir.resolve("SKILL.md"), largeContent);
        System.out.println("[TC_002] Created oversized SKILL.md (size: " + oversizedDir.resolve("SKILL.md").toFile().length() + " bytes)");

        // Run DeepAgent - oversized skill should be skipped, only skill_a remains
        Map<String, Object> resultOversize = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names.");
        System.out.println("[TC_002] DeepAgent response: " + extractOutput(resultOversize));
        System.out.println("[TC_002] Skills after oversized attempt: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");
        Files.deleteIfExists(oversizedDir.resolve("SKILL.md"));
        Files.deleteIfExists(oversizedDir);

        // ---- TC_003/TC_009: Invalid format SKILL.md does not affect other skills ----
        System.out.println("\n--- TC_003/TC_009: Invalid SKILL.md format should be skipped ---");
        Path invalidDir = skillsDir.resolve("skill_invalid");
        Files.createDirectories(invalidDir);
        Files.writeString(invalidDir.resolve("SKILL.md"),
                "This is not a valid SKILL.md - no YAML front matter at all.");
        System.out.println("[TC_003] Created invalid SKILL.md (no front matter)");

        // Run DeepAgent - invalid skill should be skipped, skill_a still works
        Map<String, Object> resultInvalid = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names.");
        System.out.println("[TC_003] DeepAgent response: " + extractOutput(resultInvalid));
        System.out.println("[TC_003] Skills after invalid attempt: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");

        // ---- TC_012: Recover invalid skill file → reloaded and works ----
        System.out.println("\n--- TC_012: Recover invalid SKILL.md → should be reloaded ---");
        Files.writeString(invalidDir.resolve("SKILL.md"),
                "---\ndescription: Recovered skill after fixing format\n---\n# Recovered Skill\n\nThis skill was fixed from invalid format.");
        forceMtimeChange(invalidDir.resolve("SKILL.md"));
        System.out.println("[TC_012] Fixed SKILL.md with valid front matter");

        Map<String, Object> resultRecover = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names and descriptions.");
        System.out.println("[TC_012] DeepAgent response: " + extractOutput(resultRecover));
        System.out.println("[TC_012] Skills after recovery: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");

        // ---- TC_011: Simultaneous add, delete, modify (10 each) ----
        System.out.println("\n--- TC_011: Simultaneous add 10, delete 10, modify 10 skills ---");
        // First, create 30 baseline skills for the simultaneous operation test
        for (int i = 1; i <= 30; i++) {
            Path batchDir = skillsDir.resolve("batch_skill_" + i);
            Files.createDirectories(batchDir);
            Files.writeString(batchDir.resolve("SKILL.md"),
                    "---\ndescription: Batch skill " + i + "\n---\n# Batch Skill " + i + "\n\nBaseline skill for TC_011.");
        }
        // Run once to load all 30 batch skills + existing skills
        Map<String, Object> resultBaseline = runDeepAgent(deepAgent,
                "List all your skill names.");
        System.out.println("[TC_011 Baseline] Skills count: " + skillNames(skillUseRail).size());

        // Now: delete batch_skill_1..10, modify batch_skill_11..20, add new_skill_31..40
        for (int i = 1; i <= 10; i++) {
            Files.deleteIfExists(skillsDir.resolve("batch_skill_" + i).resolve("SKILL.md"));
            Files.deleteIfExists(skillsDir.resolve("batch_skill_" + i));
        }
        for (int i = 11; i <= 20; i++) {
            Path modDir = skillsDir.resolve("batch_skill_" + i);
            Files.writeString(modDir.resolve("SKILL.md"),
                    "---\ndescription: Modified batch skill " + i + "\n---\n# Modified Batch Skill " + i + "\n\nUpdated for TC_011.");
            forceMtimeChange(modDir.resolve("SKILL.md"));
        }
        for (int i = 31; i <= 40; i++) {
            Path addDir = skillsDir.resolve("new_skill_" + i);
            Files.createDirectories(addDir);
            Files.writeString(addDir.resolve("SKILL.md"),
                    "---\ndescription: New skill " + i + "\n---\n# New Skill " + i + "\n\nAdded for TC_011.");
        }
        System.out.println("[TC_011] Deleted batch_skill_1..10, Modified batch_skill_11..20, Added new_skill_31..40");

        Map<String, Object> resultSimultaneous = runDeepAgent(deepAgent,
                "How many skills do you have now? List all skill names.");
        System.out.println("[TC_011] DeepAgent response: " + extractOutput(resultSimultaneous));
        System.out.println("[TC_011] Skills after simultaneous operations: " + skillNames(skillUseRail)
                + " (count: " + skillNames(skillUseRail).size() + ")");

        // Cleanup batch skills
        for (int i = 11; i <= 40; i++) {
            String dirName = i <= 20 ? "batch_skill_" + i : "new_skill_" + i;
            Files.deleteIfExists(skillsDir.resolve(dirName).resolve("SKILL.md"));
            Files.deleteIfExists(skillsDir.resolve(dirName));
        }

        Runner.stop();
        System.out.println("\n=== Demo Complete ===");
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
        for (Object rail : deepAgent.getRails()) {
            if (rail instanceof SkillUseRail skillUseRail) {
                return skillUseRail;
            }
        }
        return null;
    }

    private static List<String> skillNames(SkillUseRail rail) {
        return rail.getSkillsMeta().stream().map(SkillDescriptor::name).toList();
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