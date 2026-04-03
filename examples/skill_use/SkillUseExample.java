import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Java version of the Python examples/skill_use examples.
 */
public final class SkillUseExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "skill_use_java_example";
    private static final String DEFAULT_CONVERSATION_ID = "492";
    private static final String DEFAULT_REMOTE_OWNER = "dreamofapsychiccat";
    private static final String DEFAULT_REMOTE_REPO = "remote-skills-test";
    private static final String DEFAULT_REMOTE_REF = "HEAD";
    private static final String DEFAULT_REMOTE_DIR = "skills/image_resizer";

    private SkillUseExample() {
    }

    public static void main(String[] args) throws Exception {
        Path filesBaseDir = resolvePathConfig("FILES_BASE_DIR", Path.of("examples", "skill_use", "data"));
        Path outputDir = resolvePathConfig("OUTPUT_DIR", Path.of("examples", "skill_use", "output"));
        Path skillsDir = resolvePathConfig("SKILLS_DIR", Path.of("examples", "skill_use", "skills"));
        int maxIterations = Integer.parseInt(resolveStringConfig("MAX_ITERATIONS", "40"));

        Files.createDirectories(filesBaseDir);
        Files.createDirectories(outputDir);
        Files.createDirectories(skillsDir);

        ReActAgent agent = createAgent(filesBaseDir, outputDir, maxIterations);
        ReActAgentConfig agentConfig = (ReActAgentConfig) agent.getConfig();
        String sysOperationId = agentConfig.getSysOperationId();
        addSysOpTools(agent, sysOperationId);
        registerSkills(agent, skillsDir);

        String query = args.length == 0
                ? "Downscale the provided image inside the " + filesBaseDir + " directory by 2x."
                : String.join(" ", args);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                    agent,
                    Map.of(
                            "query", query,
                            "conversation_id", DEFAULT_CONVERSATION_ID
                    ),
                    null,
                    null
            );

            System.out.println("Skill use result:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } finally {
            Runner.release(DEFAULT_CONVERSATION_ID);
            Runner.stop();
        }
    }

    private static ReActAgent createAgent(Path filesBaseDir, Path outputDir, int maxIterations) {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("Skill Agent")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);
        SysOperationCard sysOpCard = SysOperationCard.builder()
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
            + "Complete the task as soon as the requested output file exists in the output directory, then respond with the output path and stop.\n"
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
        return agent;
    }

    private static void addSysOpTools(ReActAgent agent, String sysOperationId) {
        addSysOpTool(agent, sysOperationId, "fs", "readFile");
        addSysOpTool(agent, sysOperationId, "code", "executeCode");
        addSysOpTool(agent, sysOperationId, "shell", "executeCmd");
    }

    private static void addSysOpTool(ReActAgent agent, String sysOperationId, String operationName, String toolName) {
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOperationId, operationName, toolName);
        if (toolCard != null) {
            agent.getAbilityManager().add(toolCard);
        }
    }

    private static void registerSkills(ReActAgent agent, Path skillsDir) {
        if (!Boolean.parseBoolean(resolveStringConfig("SKILL_USE_SKIP_REMOTE", "false"))) {
            try {
                agent.registerRemoteSkills(
                        skillsDir.toString(),
                        new GitHubTree(
                                DEFAULT_REMOTE_OWNER,
                                DEFAULT_REMOTE_REPO,
                                DEFAULT_REMOTE_REF,
                                DEFAULT_REMOTE_DIR
                        ),
                        resolveStringConfig("GITHUB_TOKEN", "")
                );
            } catch (RuntimeException ex) {
                System.out.println("Remote skill registration skipped: " + ex.getMessage());
            }
        }

        agent.registerSkill(skillsDir.toString());
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