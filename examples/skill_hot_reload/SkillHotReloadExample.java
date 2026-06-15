/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.skill_hot_reload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import examples.utils.SharedExampleApiConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demonstrates Skill hot-reload: skills are automatically refreshed
 * when the agent detects file changes during request processing,
 * without any manual refresh calls or service restart.
 *
 * <p>Key mechanism: a {@code BEFORE_MODEL_CALL} callback on the agent
 * automatically checks mtime signatures of skill directories before each
 * model call. If the signature has changed (skill added, modified, or
 * deleted), the SkillManager is refreshed incrementally. The ReActAgent's
 * built-in {@code updateSkillPromptBuilderSection()} then automatically
 * picks up the latest SkillManager state and injects updated skill
 * information into the system prompt for the next model call.</p>
 *
 * <p>No manual {@code skillManager.refreshIncrementally()} calls are needed.
 * The agent automatically picks up skill changes from disk during its
 * execution loop, matching the production pattern used by SkillUseRail.</p>
 *
 * Expected output:</p>
 * <pre>
 * === Skill Hot-Reload Demo ===
 * [Step 1] Initial skills on disk: [skill_a]
 * [Step 2] Running agent - agent sees skill_a (auto-injected by agent)
 * [Hot-Reload] Skill signature changed, refreshing incrementally...
 * [Hot-Reload] Updated skills: [skill_a, skill_b]
 * [Step 3] Running agent again - agent sees skill_a + skill_b (auto-refreshed)
 * [Hot-Reload] Skill signature changed, refreshing incrementally...
 * [Hot-Reload] Updated skills: [skill_a]
 * [Step 5] Running agent again - agent only sees skill_a (skill_b auto-removed)
 * === Demo Complete ===
 * </pre>
 */
public final class SkillHotReloadExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "skill_hot_reload_java_example";
    private static final String CONVERSATION_ID_1 = "hot_reload_1";
    private static final String CONVERSATION_ID_2 = "hot_reload_2";
    private static final String CONVERSATION_ID_3 = "hot_reload_3";
    private static final String SYS_OP_ID = "skill_hot_reload_local_sysop";

    private SkillHotReloadExample() {
    }

    public static void main(String[] args) throws Exception {
        // Resolve paths relative to the skill-hot-reload module directory,
        // so they work correctly regardless of the current working directory
        // (IDE runs from project root, mvn exec:java runs from module directory)
        Path moduleDir = resolveModuleDir();
        Path skillsDir = resolvePathConfig("SKILLS_DIR",
                moduleDir.resolve("examples").resolve("skill_hot_reload").resolve("skills"));
        Path filesBaseDir = resolvePathConfig("FILES_BASE_DIR",
                moduleDir.resolve("examples").resolve("skill_hot_reload").resolve("data"));
        Path outputDir = resolvePathConfig("OUTPUT_DIR",
                moduleDir.resolve("examples").resolve("skill_hot_reload").resolve("output"));
        int maxIterations = Integer.parseInt(resolveStringConfig("MAX_ITERATIONS", "10"));

        Files.createDirectories(skillsDir);
        Files.createDirectories(filesBaseDir);
        Files.createDirectories(outputDir);

        System.out.println("=== Skill Hot-Reload Demo ===");

        // Create agent with automatic hot-reload callback.
        // registerSkill() scans skillsDir for all sub-directories with SKILL.md
        // and loads them into SkillManager. If skillsDir already has 30 skills,
        // they will all be registered.
        ReActAgent agent = createAgent(filesBaseDir, outputDir, maxIterations, skillsDir);

        SkillManager skillManager = agent.getSkillUtil().getSkillManager();
        System.out.println("[Step 1] Initial skills loaded: " + skillManager.getNames()
                + " (count: " + skillManager.count() + ")");

        // Step 2: Run agent - skill info auto-injected by updateSkillPromptBuilderSection()
        System.out.println("[Step 2] Running agent with " + skillManager.count() + " skills...");
        @SuppressWarnings("unchecked")
        Map<String, Object> result1 = (Map<String, Object>) Runner.runAgent(
                agent,
                Map.of(
                        "query", "How many skills do you currently have? List the first 5 skill names and their descriptions.",
                        "conversation_id", CONVERSATION_ID_1
                ),
                null, null
        );
        System.out.println("[Step 2] Agent response: " + extractOutput(result1));

        // Hot-reload: add a new skill (skill_b) and modify skill_a on disk
        // The callback will detect signature change on the next run and refresh SkillManager
        Path skillBDir = skillsDir.resolve("skill_b");
        Files.createDirectories(skillBDir);
        Files.writeString(skillBDir.resolve("SKILL.md"),
                "---\ndescription: B skill for hot-reload demo\n---\n# Skill B\n\nThis is skill B.");

        Path skillADir = skillsDir.resolve("skill_a");
        if (Files.exists(skillADir)) {
            Files.writeString(skillADir.resolve("SKILL.md"),
                    "---\ndescription: Modified A skill\n---\n# Skill A (Modified)\n\nThis is the modified version of skill A.");
            forceMtimeChange(skillADir.resolve("SKILL.md"));
        }

        // Step 3: Run agent again - callback auto-detects signature change
        System.out.println("[Step 3] Running agent again after adding skill_b and modifying skill_a...");
        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) Runner.runAgent(
                agent,
                Map.of(
                        "query", "How many skills do you have now? List the first 5 skill names and their descriptions.",
                        "conversation_id", CONVERSATION_ID_2
                ),
                null, null
        );
        System.out.println("[Step 3] Agent response: " + extractOutput(result2));
        System.out.println("[Step 4] SkillManager after add hot-reload: " + skillManager.getNames()
                + " (count: " + skillManager.count() + ")");

        // Hot-reload: delete skill_b from disk
        Files.deleteIfExists(skillBDir.resolve("SKILL.md"));
        Files.deleteIfExists(skillBDir);

        // Step 5: Run agent again - callback auto-detects skill deletion
        System.out.println("[Step 5] Running agent again after deleting skill_b...");
        @SuppressWarnings("unchecked")
        Map<String, Object> result3 = (Map<String, Object>) Runner.runAgent(
                agent,
                Map.of(
                        "query", "How many skills do you have now? List the first 5 skill names and their descriptions.",
                        "conversation_id", CONVERSATION_ID_3
                ),
                null, null
        );
        System.out.println("[Step 5] Agent response: " + extractOutput(result3));
        System.out.println("[Step 6] SkillManager after delete hot-reload: " + skillManager.getNames()
                + " (count: " + skillManager.count() + ")");

        // Clean up
        Runner.release(CONVERSATION_ID_1);
        Runner.release(CONVERSATION_ID_2);
        Runner.release(CONVERSATION_ID_3);
        Runner.stop();

        System.out.println("=== Demo Complete ===");
    }

    /**
     * Create a ReActAgent with automatic skill hot-reload capability.
     *
     * <p>The BEFORE_MODEL_CALL callback only needs to:
     * <ol>
     *   <li>Build current mtime signature of skill directories</li>
     *   <li>Compare with last known signature</li>
     *   <li>If changed: refresh SkillManager incrementally</li>
     * </ol>
     * The ReActAgent's built-in {@code updateSkillPromptBuilderSection()} method
     * automatically picks up the latest SkillManager state and injects it
     * into the system prompt on each iteration - no manual SystemMessage
     * injection is needed.</p>
     */
    private static ReActAgent createAgent(Path filesBaseDir, Path outputDir,
                                          int maxIterations, Path skillsDir) {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("Skill Hot-Reload Agent")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);

        // Register SysOperation
        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id(SYS_OP_ID)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(null).build())
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);

        String systemPrompt = "You are an intelligent assistant.\n"
                + "All user-provided files are located at '" + filesBaseDir + "'\n"
                + "Put all generated files into " + outputDir + "\n"
                + "You are running on Windows.\n"
                + "Use readFile with encoding UTF-8 when reading text files.\n"
                + "When using executeCode with Python, use single-quoted string literals and include a positive timeout.\n"
                + "You may use tools when necessary.\n";

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .maxIterations(maxIterations)
                .build()
                .configureModelClient(
                        SharedExampleApiConfigLoader.getModelProvider(),
                        SharedExampleApiConfigLoader.getApiKey(),
                        SharedExampleApiConfigLoader.getApiBase(),
                        SharedExampleApiConfigLoader.getModelName(),
                        SharedExampleApiConfigLoader.getSslVerify()
                )
                .configureContextEngine(null, null, false);

        config.setSysOperationId(sysOpCard.getId());
        agent.configure(config);

        // Add SysOp tools
        addSysOpTools(agent, sysOpCard.getId());

        // Register skill directory on agent - scans all sub-directories with SKILL.md
        agent.registerSkill(skillsDir.toString());

        // === Automatic hot-reload via BEFORE_MODEL_CALL callback ===
        // This callback fires before every model call during agent execution.
        // It checks if skill files have changed on disk (mtime signature comparison).
        // If the signature has changed, it refreshes SkillManager incrementally.
        // The ReActAgent's updateSkillPromptBuilderSection() then automatically
        // uses the refreshed SkillManager state to inject updated skill info
        // into the system prompt - no manual prompt update needed.
        SkillManager skillManager = agent.getSkillUtil().getSkillManager();
        AtomicReference<List<Map.Entry<String, Long>>> lastSignatureRef =
                new AtomicReference<>(skillManager.buildSnapshotSignature(List.of(skillsDir)));

        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx -> {
            List<Map.Entry<String, Long>> currentSignature = skillManager.buildSnapshotSignature(List.of(skillsDir));
            boolean signatureChanged = !signaturesEqual(currentSignature, lastSignatureRef.get());

            if (signatureChanged) {
                System.out.println("[Hot-Reload] Skill signature changed, refreshing incrementally...");
                skillManager.refreshIncrementally(List.of(skillsDir));
                lastSignatureRef.set(currentSignature);
                System.out.println("[Hot-Reload] Updated skills: " + skillManager.getNames());
            }
        }, 100);

        return agent;
    }

    private static void addSysOpTools(ReActAgent agent, String sysOperationId) {
        addSysOpTool(agent, sysOperationId, "fs", "readFile");
        addSysOpTool(agent, sysOperationId, "code", "executeCode");
        addSysOpTool(agent, sysOperationId, "shell", "executeCmd");
    }

    private static void addSysOpTool(ReActAgent agent, String sysOperationId,
                                     String operationName, String toolName) {
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOperationId, operationName, toolName);
        if (toolCard != null) {
            agent.getAbilityManager().add(toolCard);
        }
    }

    private static boolean signaturesEqual(List<Map.Entry<String, Long>> a, List<Map.Entry<String, Long>> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getKey().equals(b.get(i).getKey())
                    || a.get(i).getValue() != b.get(i).getValue()) {
                return false;
            }
        }
        return true;
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
        // Search upward from CWD for pom.xml to find the project root.
        // This works regardless of whether CWD is the project root, the example
        // directory, or any other subdirectory.
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        // Fallback: assume CWD is the module directory
        return Path.of("").toAbsolutePath();
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