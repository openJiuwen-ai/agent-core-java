/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.skill_evaluate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.dev_tools.skill_evaluator.SkillEvaluator;
import examples.utils.SharedExampleApiConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Java example for evaluating a local skill with the bundled SkillEvaluator module.
 */
public final class SkillEvaluateExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_CONVERSATION_ID = "skill_eval_001";

    private SkillEvaluateExample() {
    }

    public static void main(String[] args) throws Exception {
        Path targetSkillPath = resolveTargetSkill(args);
        Path filesBaseDir = Path.of("examples", "skill_use", "data").toAbsolutePath().normalize();
        Path outputDir = Path.of("examples", "skill_evaluate", "output").toAbsolutePath().normalize();

        Files.createDirectories(filesBaseDir);
        Files.createDirectories(outputDir);
        configureExampleProperties(filesBaseDir, outputDir);

        SkillEvaluator evaluator = new SkillEvaluator();
        try {
            evaluator.createAgent().join();
            Object result = evaluator.evaluate(
                    targetSkillPath,
                    "Focus on trigger quality, safety constraints, and whether the skill can be audited by another engineer.",
                    outputDir
            ).join();

            System.out.println("Target skill:");
            System.out.println(targetSkillPath.toAbsolutePath().normalize());
            System.out.println("Evaluation result:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } finally {
            Runner.release(DEFAULT_CONVERSATION_ID);
            Runner.stop();
        }
    }

    private static void configureExampleProperties(Path filesBaseDir, Path outputDir) {
        Map<String, String> config = SharedExampleApiConfigLoader.load();
        System.setProperty("API_BASE", config.getOrDefault("API_BASE", ""));
        System.setProperty("API_KEY", config.getOrDefault("API_KEY", ""));
        System.setProperty("MODEL_PROVIDER", config.getOrDefault("MODEL_PROVIDER", ""));
        System.setProperty("MODEL_NAME", config.getOrDefault("MODEL_NAME", ""));
        System.setProperty("LLM_SSL_VERIFY", config.getOrDefault("LLM_SSL_VERIFY", "false"));
        System.setProperty("FILES_BASE_DIR", filesBaseDir.toString());
        System.setProperty("OUTPUT_DIR", outputDir.toString());
        System.setProperty(
                "SKILLS_DIR",
                Path.of("examples", "skill_use", "skills")
                        .toAbsolutePath()
                        .normalize()
                        .toString()
        );
    }

    private static Path resolveTargetSkill(String[] args) {
        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        return Path.of("image_resizer");
    }
}
