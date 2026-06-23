/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * CLI helper that runs a skill agent with a prompt and skill path.
 *
 * <p>Mirrors Python's {@code run_eval_query} module in
 * {@code openjiuwen/dev_tools/skill_evaluator/skills/skill_tester/scripts/run_eval_query.py}.</p>
 */
public class RunEvalQuery {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final ZoneId ZONE_UTC8 = ZoneId.of("UTC+8");
    private static final String DEFAULT_SCRIPT_DIR =
            "openjiuwen/dev_tools/skill_evaluator/skills/skill_tester/scripts";

    private final Map<String, String> environment;
    private String prompt;
    private Path skillPath;
    private Path outputPath;
    private Path filesBaseDir;
    private Integer maxIterations;

    public RunEvalQuery() {
        this(System.getenv());
    }

    public RunEvalQuery(Map<String, String> environment) {
        this.environment = loadDotEnv(environment);
    }

    public RunEvalQuery setPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public RunEvalQuery setSkillPath(Path skillPath) {
        this.skillPath = skillPath;
        return this;
    }

    public RunEvalQuery setOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
    }

    public RunEvalQuery setFilesBaseDir(Path filesBaseDir) {
        this.filesBaseDir = filesBaseDir;
        return this;
    }

    public RunEvalQuery setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public String run() throws Exception {
        if (prompt == null || prompt.isEmpty()) {
            throw new IllegalArgumentException("Prompt is required");
        }
        if (skillPath == null) {
            throw new IllegalArgumentException("Skill path does not exist: null");
        }
        Path resolvedSkillPath = expandUser(skillPath.toString()).toAbsolutePath().normalize();
        if (!Files.exists(resolvedSkillPath)) {
            throw new NoSuchFileException("Skill path does not exist: " + resolvedSkillPath);
        }

        Path resolvedFilesBaseDir = filesBaseDir == null
                ? expandUser(env("FILES_BASE_DIR", DEFAULT_SCRIPT_DIR))
                : expandUser(filesBaseDir.toString());
        resolvedFilesBaseDir = resolvedFilesBaseDir.toAbsolutePath().normalize();
        Path outputFile = resolveOutputFile(outputPath == null ? null : expandUser(outputPath.toString())
                .toAbsolutePath()
                .normalize());
        int iterations = maxIterations != null && maxIterations != 0
                ? maxIterations
                : parseInt(env("MAX_ITERATIONS", "40"), 40);

        String apiBase = env("API_BASE", "");
        String apiKey = env("API_KEY", "");
        String modelName = env("MODEL_NAME", "");
        String modelProvider = env("MODEL_PROVIDER", "");
        boolean verifySsl = parseBoolean(env("LLM_SSL_VERIFY", "False"));
        String outputDir = outputFile.getParent().toString();

        AgentCard card = new AgentCard();
        card.setName("skill_agent");
        card.setDescription("Skill Agent");
        ReActAgent agent = new ReActAgent(card);

        String systemPrompt = buildSystemPrompt(resolvedFilesBaseDir, outputDir);
        SysOperationCard sysopCard = new SysOperationCard();
        sysopCard.setMode(OperationMode.LOCAL);
        sysopCard.setWorkConfig(new LocalWorkConfig());
        Runner.resourceMgr().addSysOperation(sysopCard);

        ReActAgentConfig config = new ReActAgentConfig()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, verifySsl)
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .configureMaxIterations(iterations)
                .configureContextEngine(null, null, false, false);
        config.setSysOperationId(sysopCard.getId());
        agent.configure(config);

        for (String[] toolConfig : List.of(
                new String[]{"fs", "read_file"},
                new String[]{"code", "execute_code"},
                new String[]{"shell", "execute_cmd"},
                new String[]{"fs", "write_file"})) {
            ToolCard toolCard = getSysOpToolCard(sysopCard.getId(), toolConfig[0], toolConfig[1]);
            if (toolCard != null) {
                agent.getAbilityManager().add(toolCard);
            }
        }

        await(agent.registerSkill(resolvedSkillPath.toString()));
        Loggers.AGENT.info("Skill loaded from: {}", resolvedSkillPath);

        Loggers.AGENT.info("Running agent with prompt: {}", prompt);
        String timestamp = LocalDateTime.now(ZONE_UTC8).format(TIMESTAMP_FORMAT);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", prompt);
        inputs.put("conversation_id", "cli_run_" + timestamp);
        Object result = await(Runner.runAgent(agent, inputs));
        String outputText = outputText(result);
        Loggers.AGENT.info(outputText);

        Files.writeString(outputFile, outputText, StandardCharsets.UTF_8);
        Loggers.AGENT.info("Result saved to: {}", outputFile);
        Loggers.AGENT.info("\nResult saved to: {}", outputFile);
        return outputText;
    }

    Path resolveOutputFile(Path outputPath) {
        String timestamp = LocalDateTime.now(ZONE_UTC8).format(TIMESTAMP_FORMAT);
        String defaultFilename = "skill_test_result_" + timestamp + ".txt";
        if (outputPath == null) {
            return Path.of(System.getProperty("user.dir")).resolve(defaultFilename);
        }

        boolean looksLikeDir = Files.isDirectory(outputPath)
                || outputPath.getFileName().toString().indexOf('.') == -1
                || outputPath.toString().endsWith("/")
                || outputPath.toString().endsWith("\\");
        try {
            if (looksLikeDir) {
                Files.createDirectories(outputPath);
                return outputPath.resolve(defaultFilename);
            }
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return outputPath;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create output directory: " + exception.getMessage(), exception);
        }
    }

    String buildSystemPrompt(Path filesBaseDir, String outputDir) {
        return "You are an intelligent assistant.\n"
                + "All user-provided files are located at '" + filesBaseDir + "'\n"
                + "Put all generated files into " + outputDir + " folder\n"
                + "You may use tools when necessary.\n";
    }

    static ParsedArgs parseArgs(String[] args) {
        String prompt = null;
        Path skillPath = null;
        Path outputPath = null;
        Path filesBaseDir = null;
        Integer maxIterations = null;
        for (int index = 0; index < args.length; index += 1) {
            String arg = args[index];
            switch (arg) {
                case "--prompt", "-p" -> prompt = requireValue(args, ++index, arg);
                case "--skill-path", "-s" -> skillPath = Path.of(requireValue(args, ++index, arg));
                case "--output-path", "-o" -> outputPath = Path.of(requireValue(args, ++index, arg));
                case "--files-base-dir", "-f" -> filesBaseDir = Path.of(requireValue(args, ++index, arg));
                case "--max-iterations", "-m" -> maxIterations = Integer.parseInt(requireValue(args, ++index, arg));
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (prompt == null || skillPath == null || outputPath == null) {
            throw new IllegalArgumentException(usage());
        }
        return new ParsedArgs(prompt, skillPath, outputPath, filesBaseDir, maxIterations);
    }

    public static void main(String[] args) throws Exception {
        try {
            ParsedArgs parsed = parseArgs(args);
            RunEvalQuery runner = new RunEvalQuery()
                    .setPrompt(parsed.prompt())
                    .setSkillPath(parsed.skillPath())
                    .setOutputPath(parsed.outputPath());
            if (parsed.filesBaseDir() != null) {
                runner.setFilesBaseDir(parsed.filesBaseDir());
            }
            if (parsed.maxIterations() != null) {
                runner.setMaxIterations(parsed.maxIterations());
            }
            runner.run();
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static String usage() {
        return "Usage: RunEvalQuery --prompt <prompt> --skill-path <path> --output-path <path> "
                + "[--files-base-dir <dir>] [--max-iterations <n>]";
    }

    private ToolCard getSysOpToolCard(String sysOperationId, String operationName, String toolName) {
        Object result = Runner.resourceMgr().getSysOpToolCards(sysOperationId,
                List.of(operationName),
                List.of(toolName));
        return result instanceof ToolCard toolCard ? toolCard : null;
    }

    private String env(String key, String defaultValue) {
        String value = environment.get(key);
        return value == null ? defaultValue : value;
    }

    private static String outputText(Object result) {
        if (result instanceof Map<?, ?> map && map.containsKey("output")) {
            Object output = map.get("output");
            return output == null ? "null" : String.valueOf(output);
        }
        return String.valueOf(result);
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

    /**
     * Mirrors Python argparse values for
     * {@code openjiuwen/dev_tools/skill_evaluator/skills/skill_tester/scripts/run_eval_query.py}.
     */
    record ParsedArgs(String prompt, Path skillPath, Path outputPath, Path filesBaseDir, Integer maxIterations) {
        ParsedArgs {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(skillPath, "skillPath");
            Objects.requireNonNull(outputPath, "outputPath");
        }
    }
}
