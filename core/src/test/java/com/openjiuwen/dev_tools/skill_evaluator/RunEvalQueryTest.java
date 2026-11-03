/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code run_eval_query} module in
 * {@code openjiuwen/dev_tools/skill_evaluator/skills/skill_tester/scripts/run_eval_query.py}.
 */
class RunEvalQueryTest {
    @Test
    void resolveOutputFileCreatesTimestampedFileInsideDirectoryLikePath(@TempDir Path tempDir) {
        RunEvalQuery query = new RunEvalQuery(Map.of());
        Path outputDir = tempDir.resolve("reports");

        Path result = query.resolveOutputFile(outputDir);

        assertEquals(outputDir, result.getParent());
        assertTrue(Files.isDirectory(outputDir));
        assertTrue(result.getFileName().toString().startsWith("skill_test_result_"));
        assertTrue(result.getFileName().toString().endsWith(".txt"));
    }

    @Test
    void resolveOutputFileUsesExplicitFilePathAndCreatesParent(@TempDir Path tempDir) {
        RunEvalQuery query = new RunEvalQuery(Map.of());
        Path outputFile = tempDir.resolve("nested").resolve("result.txt");

        Path result = query.resolveOutputFile(outputFile);

        assertEquals(outputFile, result);
        assertTrue(Files.isDirectory(outputFile.getParent()));
    }

    @Test
    void buildSystemPromptMatchesPythonTemplate(@TempDir Path tempDir) {
        RunEvalQuery query = new RunEvalQuery(Map.of());
        Path filesBaseDir = tempDir.resolve("files");

        String prompt = query.buildSystemPrompt(filesBaseDir, tempDir.resolve("out").toString());

        assertEquals("You are an intelligent assistant.\n"
                + "All user-provided files are located at '" + filesBaseDir + "'\n"
                + "Put all generated files into " + tempDir.resolve("out") + " folder\n"
                + "You may use tools when necessary.\n", prompt);
    }

    @Test
    void parseArgsRequiresAndPreservesCliOptions() {
        RunEvalQuery.ParsedArgs args = RunEvalQuery.parseArgs(new String[]{
                "--prompt", "Evaluate it",
                "--skill-path", "skills/sample",
                "--output-path", "reports",
                "--files-base-dir", "files",
                "--max-iterations", "7"
        });

        assertEquals("Evaluate it", args.prompt());
        assertEquals(Path.of("skills/sample"), args.skillPath());
        assertEquals(Path.of("reports"), args.outputPath());
        assertEquals(Path.of("files"), args.filesBaseDir());
        assertEquals(7, args.maxIterations());
    }

    @Test
    void runRejectsMissingSkillPathBeforeRunnerUse(@TempDir Path tempDir) {
        RunEvalQuery query = new RunEvalQuery(Map.of())
                .setPrompt("Evaluate it")
                .setSkillPath(tempDir.resolve("missing-skill"))
                .setOutputPath(tempDir.resolve("out"));

        assertThrows(NoSuchFileException.class, query::run);
    }
}
