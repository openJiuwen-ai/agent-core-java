/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent_evolving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.checkpointing.EvolutionLog;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.checkpointing.EvolutionStore;
import com.openjiuwen.agentevolving.experience.EvolutionContext;
import com.openjiuwen.agentevolving.optimizer.skill_call.SkillExperienceOptimizer;
import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.agentevolving.signal.SignalDetector;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/system_tests/agent_evolving/test_online_evolution_e2e.py}.
 */
class OnlineEvolutionE2eMissingTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fullPipelineSignalToPersist(@TempDir Path tempDir) throws Exception {
        String skillName = "data-processor";
        String skillContent = "# Data Processor Skill\n\nProcess CSV and JSON files.\n";
        Path skillsRoot = tempDir.resolve("skills");
        prepareSkill(skillsRoot, skillName, skillContent);

        List<Map<String, Object>> messages = buildConversationWithScript();
        SignalDetector detector = new SignalDetector(Set.of(skillName));
        List<EvolutionSignal> signals = detector.detect(messages);

        assertThat(signals).extracting(EvolutionSignal::getSignalType)
                .contains("script_artifact", "execution_failure");
        assertThat(signals.stream()
                .filter(signal -> "script_artifact".equals(signal.getSignalType()))
                .findFirst()
                .orElseThrow()
                .getSkillName()).isEqualTo(skillName);

        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker(mockLlmResponseWithScript())),
                "mock-model",
                "en"
        );
        EvolutionStore store = new EvolutionStore(skillsRoot.toString());
        EvolutionContext ctx = new EvolutionContext(
                skillName,
                signals,
                skillContent,
                messages,
                List.of(),
                List.of(),
                "",
                null,
                List.of(),
                Map.of()
        );

        List<EvolutionRecord> records = optimizer.generateRecords(ctx);

        assertThat(records).hasSize(2);
        assertThat(records.stream().filter(record -> record.getChange().getTarget() != EvolutionTarget.SCRIPT))
                .hasSize(1);
        List<EvolutionRecord> scriptRecords = records.stream()
                .filter(record -> record.getChange().getTarget() == EvolutionTarget.SCRIPT)
                .toList();
        assertThat(scriptRecords).hasSize(1);
        assertThat(scriptRecords.get(0).getChange().getScriptLanguage()).isEqualTo("python");
        assertThat(scriptRecords.get(0).getChange().getScriptFilename()).isEqualTo("generate_bar_chart.py");

        for (EvolutionRecord record : records) {
            join(store.appendRecord(skillName, record));
        }

        EvolutionLog evolutionLog = join(store.loadEvolutionLog(skillName));
        assertThat(evolutionLog.getEntries()).hasSize(2);

        Path scriptsDir = skillsRoot.resolve(skillName).resolve("evolution").resolve("scripts");
        assertThat(scriptsDir).isDirectory();
        List<Path> pythonFiles;
        try (Stream<Path> stream = Files.list(scriptsDir)) {
            pythonFiles = stream.filter(path -> path.getFileName().toString().endsWith(".py")).toList();
        }
        assertThat(pythonFiles).hasSize(1);
        String scriptContent = Files.readString(pythonFiles.get(0), StandardCharsets.UTF_8);
        assertThat(scriptContent).contains("matplotlib", "pandas");

        String scriptIndex = Files.readString(scriptsDir.resolve("_index.md"), StandardCharsets.UTF_8);
        assertThat(scriptIndex).contains("generate_bar_chart.py", "python");

        Path evolutionDir = skillsRoot.resolve(skillName).resolve("evolution");
        String troubleshooting = Files.readString(evolutionDir.resolve("troubleshooting.md"), StandardCharsets.UTF_8);
        assertThat(troubleshooting).contains("Permission Denied");

        String skillMarkdown = Files.readString(skillsRoot.resolve(skillName).resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertThat(skillMarkdown)
                .contains("<!-- evolution-index-start -->")
                .contains("<!-- evolution-index-end -->")
                .contains("Evolution Experiences")
                .contains("**2**");
    }

    @Test
    void dataFetchFalsePositiveSuppressed() {
        List<Map<String, Object>> messages = List.of(
                assistantWithToolCalls("", List.of(toolCall("tc_s", "web_search", Map.of()))),
                toolMessage(
                        "tc_s",
                        "web_search",
                        "Search results:\n"
                                + "1. How to handle Python timeout errors\n"
                                + "2. Common ValueError exceptions and fixes\n"
                                + "3. ConnectionError troubleshooting guide\n"
                ),
                assistantWithToolCalls("", List.of(toolCall("tc_r", "read_file", Map.of()))),
                toolMessage("tc_r", "read_file", "File content: raise ValueError('failed validation')\n")
        );

        List<EvolutionSignal> signals = new SignalDetector().detect(messages);

        assertThat(signals).filteredOn(signal -> "execution_failure".equals(signal.getSignalType())).isEmpty();
    }

    @Test
    void retryOnMalformedLlmOutput(@TempDir Path tempDir) throws Exception {
        String skillName = "retry-skill";
        Path skillsRoot = tempDir.resolve("skills");
        prepareSkill(skillsRoot, skillName, "# Retry Skill\n");
        RecordingInvoker invoker = new RecordingInvoker(
                "This is not JSON at all { broken",
                toJson(List.of(bodyPatch("Troubleshooting", "### Recovered Fix\n- Retry succeeded")))
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "mock", "cn");
        List<EvolutionSignal> signals = new SignalDetector().detect(
                List.of(toolMessage("", "bash", "Error: command timeout"))
        );
        EvolutionContext ctx = new EvolutionContext(
                skillName,
                signals,
                "# Retry Skill\n",
                List.of(message("user", "test")),
                List.of(),
                List.of(),
                "",
                null,
                List.of(),
                Map.of()
        );

        List<EvolutionRecord> records = optimizer.generateRecords(ctx);

        assertThat(invoker.invokeCount()).isEqualTo(2);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getChange().getContent()).contains("Recovered Fix");
    }

    @Test
    void mergeTargetReplacesExistingRecord(@TempDir Path tempDir) throws Exception {
        String skillName = "merge-skill";
        Path skillsRoot = tempDir.resolve("skills");
        prepareSkill(skillsRoot, skillName, "# Merge Skill\n");
        EvolutionStore store = new EvolutionStore(skillsRoot.toString());
        List<EvolutionSignal> signals = new SignalDetector().detect(
                List.of(toolMessage("", "bash", "Error: timeout"))
        );

        SkillExperienceOptimizer firstOptimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker(toJson(List.of(bodyPatch(
                        "Troubleshooting",
                        "### Initial finding\n- v1 content"
                ))))),
                "mock",
                "en"
        );
        EvolutionContext firstCtx = new EvolutionContext(
                skillName,
                signals,
                "# Merge Skill\n",
                List.of(message("user", "hi")),
                List.of(),
                List.of(),
                "",
                null,
                List.of(),
                Map.of()
        );
        List<EvolutionRecord> firstRecords = firstOptimizer.generateRecords(firstCtx);
        for (EvolutionRecord record : firstRecords) {
            join(store.appendRecord(skillName, record));
        }
        String oldId = firstRecords.get(0).getId();

        SkillExperienceOptimizer secondOptimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker(toJson(List.of(bodyPatch(
                        "Troubleshooting",
                        "### Updated finding\n- v2 content with more detail",
                        oldId
                ))))),
                "mock",
                "en"
        );
        List<EvolutionRecord> existingBody = join(store.getPendingRecords(skillName, EvolutionTarget.BODY));
        EvolutionContext secondCtx = new EvolutionContext(
                skillName,
                signals,
                "# Merge Skill\n",
                List.of(message("user", "hi again")),
                List.of(),
                existingBody,
                "",
                null,
                List.of(),
                Map.of()
        );
        List<EvolutionRecord> secondRecords = secondOptimizer.generateRecords(secondCtx);
        for (EvolutionRecord record : secondRecords) {
            join(store.appendRecord(skillName, record));
        }

        EvolutionLog finalLog = join(store.loadEvolutionLog(skillName));
        assertThat(finalLog.getEntries()).hasSize(1);
        assertThat(finalLog.getEntries().get(0).getChange().getContent()).contains("v2 content");
    }

    private static Path prepareSkill(Path root, String name, String content) throws IOException {
        Path skillDir = Files.createDirectories(root.resolve(name));
        Files.writeString(skillDir.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
        return skillDir;
    }

    private static List<Map<String, Object>> buildConversationWithScript() {
        return List.of(
                assistantWithToolCalls(
                        "Let me read the skill first.",
                        List.of(toolCall(
                                "tc_read",
                                "read_file",
                                Map.of("file_path", "/skills/data-processor/SKILL.md")
                        ))
                ),
                toolMessage(
                        "tc_read",
                        "read_file",
                        "# Data Processor Skill\n\nProcess CSV and JSON files."
                ),
                assistantWithToolCalls(
                        "",
                        List.of(toolCall(
                                "tc_code",
                                "code",
                                Map.of("code", """
                                        import pandas as pd
                                        import matplotlib.pyplot as plt

                                        df = pd.read_csv('data.csv')
                                        fig, ax = plt.subplots()
                                        ax.bar(df['category'], df['value'])
                                        ax.set_title('Category Distribution')
                                        plt.savefig('chart.png')
                                        print('Chart saved to chart.png')
                                        """)
                        ))
                ),
                toolMessage("tc_code", "code", "Chart saved to chart.png"),
                assistantWithToolCalls(
                        "",
                        List.of(toolCall("tc_bash", "bash", Map.of("command", "cat /etc/hosts | head")))
                ),
                toolMessage("tc_bash", "bash", "Error: permission denied reading /etc/hosts"),
                message("user", "Actually, the command should use sudo to read system files.")
        );
    }

    private static String mockLlmResponseWithScript() {
        return toJson(List.of(
                bodyPatch(
                        "Troubleshooting",
                        "### Permission Denied on System Files\n"
                                + "- Use sudo when reading system files like /etc/hosts"
                ),
                scriptPatch()
        ));
    }

    private static Map<String, Object> bodyPatch(String section, String content) {
        return bodyPatch(section, content, null);
    }

    private static Map<String, Object> bodyPatch(String section, String content, String mergeTarget) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("action", "append");
        patch.put("target", "body");
        patch.put("section", section);
        patch.put("content", content);
        if (mergeTarget != null) {
            patch.put("merge_target", mergeTarget);
        }
        return patch;
    }

    private static Map<String, Object> scriptPatch() {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("action", "append");
        patch.put("target", "script");
        patch.put("section", "Scripts");
        patch.put("content", """
                import pandas as pd
                import matplotlib.pyplot as plt

                df = pd.read_csv('data.csv')
                fig, ax = plt.subplots()
                ax.bar(df['category'], df['value'])
                ax.set_title('Category Distribution')
                plt.savefig('chart.png')
                """);
        patch.put("script_filename", "generate_bar_chart.py");
        patch.put("script_language", "python");
        patch.put("script_purpose", "Generate bar chart from CSV data");
        return patch;
    }

    private static Map<String, Object> message(String role, Object content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static Map<String, Object> assistantWithToolCalls(
            String content,
            List<Map<String, Object>> toolCalls
    ) {
        Map<String, Object> message = message("assistant", content);
        message.put("tool_calls", toolCalls);
        return message;
    }

    private static Map<String, Object> toolMessage(String toolCallId, String name, String content) {
        Map<String, Object> message = message("tool", content);
        message.put("tool_call_id", toolCallId);
        message.put("name", name);
        return message;
    }

    private static Map<String, Object> toolCall(String id, String name, Map<String, Object> arguments) {
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", id);
        toolCall.put("name", name);
        toolCall.put("arguments", toJson(arguments));
        return toolCall;
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to serialize test JSON", exception);
        }
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    /**
     * Deterministic model invoker for the E2E pipeline.
     */
    private static final class RecordingInvoker implements Model.ModelInvoker {
        private final Queue<Object> outcomes = new ArrayDeque<>();
        private int invokeCount;

        private RecordingInvoker(Object... outcomes) {
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                ModelInvokeOptions options
        ) {
            invokeCount++;
            Object outcome = outcomes.isEmpty() ? "" : outcomes.remove();
            if (outcome instanceof Throwable throwable) {
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(throwable);
                return failed;
            }
            return CompletableFuture.completedFuture(new AssistantMessage(String.valueOf(outcome)));
        }

        private int invokeCount() {
            return invokeCount;
        }
    }
}
