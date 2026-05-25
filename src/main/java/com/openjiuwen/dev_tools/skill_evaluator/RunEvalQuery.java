/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Run a skill agent with a given prompt and skill path.
 * 
 * <p>Mirrors Python's openjiuwen.dev_tools.skill_evaluator.skills.skill_tester.scripts.run_eval_query.py.</p>
 */
public class RunEvalQuery {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final ZoneId ZONE_UTC8 = ZoneId.of("UTC+8");

    private String prompt;
    private Path skillPath;
    private Path outputPath;
    private Path filesBaseDir;
    private int maxIterations = 40;

    public RunEvalQuery() {
    }

    /**
     * Set the prompt/query to send to the agent.
     */
    public RunEvalQuery setPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * Set the path to the skill directory or file to be tested.
     */
    public RunEvalQuery setSkillPath(Path skillPath) {
        this.skillPath = skillPath;
        return this;
    }

    /**
     * Set the output path for results.
     */
    public RunEvalQuery setOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
    }

    /**
     * Set the base directory for user-provided files.
     */
    public RunEvalQuery setFilesBaseDir(Path filesBaseDir) {
        this.filesBaseDir = filesBaseDir;
        return this;
    }

    /**
     * Set the maximum agent iterations.
     */
    public RunEvalQuery setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    /**
     * Resolve the output file path.
     */
    private Path resolveOutputFile() {
        String timestamp = LocalDateTime.now(ZONE_UTC8).format(TIMESTAMP_FORMAT);
        String defaultFilename = "skill_test_result_" + timestamp + ".txt";

        if (outputPath == null) {
            return Path.of(System.getProperty("user.dir"), defaultFilename);
        }

        // Treat as a directory if: it already is one, has no file extension,
        // or the original string ends with a separator
        boolean looksLikeDir = Files.isDirectory(outputPath)
                || outputPath.getFileName().toString().indexOf('.') == -1
                || outputPath.toString().endsWith("/") 
                || outputPath.toString().endsWith("\\");

        try {
            if (looksLikeDir) {
                Files.createDirectories(outputPath);
                return outputPath.resolve(defaultFilename);
            } else {
                Files.createDirectories(outputPath.getParent());
                return outputPath;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + e.getMessage(), e);
        }
    }

    /**
     * Run the skill evaluation query.
     */
    public String run() throws Exception {
        if (prompt == null || prompt.isEmpty()) {
            throw new IllegalArgumentException("Prompt is required");
        }
        if (skillPath == null || !Files.exists(skillPath)) {
            throw new IllegalArgumentException("Skill path does not exist: " + skillPath);
        }

        Path resolvedFilesBaseDir = filesBaseDir != null ? filesBaseDir : Path.of(System.getProperty("user.dir"));
        Path outputFile = resolveOutputFile();
        String outputDir = outputFile.getParent().toString();

        // Build agent
        AgentCard card = new AgentCard();
        card.setName("skill_agent");
        card.setDescription("Skill Agent");
        ReActAgent agent = new ReActAgent(card);

        String systemPrompt = String.format(
                "You are an intelligent assistant.\n" +
                "All user-provided files are located at '%s'\n" +
                "Put all generated files into %s folder\n" +
                "You may use tools when necessary.\n",
                resolvedFilesBaseDir, outputDir
        );

        // Create sys operation
        SysOperationCard sysopCard = new SysOperationCard();
        sysopCard.setMode(OperationMode.LOCAL);
        sysopCard.setWorkConfig(new LocalWorkConfig());
        Runner.resourceMgr().addSysOperation(sysopCard, null);

        // Configure agent
        ReActAgentConfig config = new ReActAgentConfig()
                .configureMaxIterations(maxIterations);
        config.setSysOperationId(sysopCard.getId());
        
        agent.configure(config);

        // Register tools
        String[][] toolConfigs = {
                {"fs", "read_file"},
                {"code", "execute_code"},
                {"shell", "execute_cmd"},
                {"fs", "write_file"}
        };
        for (String[] toolConfig : toolConfigs) {
            try {
                var toolCard = Runner.resourceMgr().getSysOpToolCards(
                        sysopCard.getId(), toolConfig[0], toolConfig[1]);
                agent.getAbilityManager().add(toolCard);
            } catch (Exception e) {
                Loggers.AGENT.warning("Could not register tool: " + toolConfig[1] + " - " + e.getMessage());
            }
        }

        // Register skill
        agent.registerSkill(skillPath.toString());
        Loggers.AGENT.info("Skill loaded from: " + skillPath);

        // Run agent
        Loggers.AGENT.info("Running agent with prompt: " + prompt);
        String timestamp = LocalDateTime.now(ZONE_UTC8).format(TIMESTAMP_FORMAT);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", prompt);
        inputs.put("conversation_id", "cli_run_" + timestamp);

        Map<String, Object> result = (Map<String, Object>) Runner.runAgent(agent, inputs, null, null);
        String outputText = result.containsKey("output") 
                ? result.get("output").toString() 
                : result.toString();

        Loggers.AGENT.info(outputText);

        // Save result
        Files.writeString(outputFile, outputText);
        Loggers.AGENT.info("Result saved to: " + outputFile);

        return outputText;
    }

    /**
     * Main entry point for CLI usage.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: RunEvalQuery --prompt <prompt> --skill-path <path> --output-path <path> [--files-base-dir <dir>] [--max-iterations <n>]");
            System.exit(1);
        }

        RunEvalQuery runner = new RunEvalQuery();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--prompt", "-p" -> {
                    if (i + 1 < args.length) {
                        runner.setPrompt(args[++i]);
                    }
                }
                case "--skill-path", "-s" -> {
                    if (i + 1 < args.length) {
                        runner.setSkillPath(Path.of(args[++i]));
                    }
                }
                case "--output-path", "-o" -> {
                    if (i + 1 < args.length) {
                        runner.setOutputPath(Path.of(args[++i]));
                    }
                }
                case "--files-base-dir", "-f" -> {
                    if (i + 1 < args.length) {
                        runner.setFilesBaseDir(Path.of(args[++i]));
                    }
                }
                case "--max-iterations", "-m" -> {
                    if (i + 1 < args.length) {
                        runner.setMaxIterations(Integer.parseInt(args[++i]));
                    }
                }
            }
        }

        runner.run();
    }
}
