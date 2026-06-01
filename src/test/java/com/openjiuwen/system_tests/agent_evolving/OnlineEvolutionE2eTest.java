/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent_evolving;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionContext;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.optimizer.skill_call.SkillExperienceOptimizer;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.signal.SignalDetector;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.harness.rails.skills.TeamSkillRail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * End-to-end system tests for the online skill evolution pipeline.
 *
 * <p>Mirrors Python's {@code test_online_evolution_e2e.py} in
 * {@code tests/system_tests/agent_evolving}.
 */
class OnlineEvolutionE2eTest {

    @Test
    void testFullPipelineSignalToPersist(@TempDir Path tmpDir) throws Exception {
        String skillName = "data-processor";
        String skillContent = "# Data Processor Skill\n\nProcess CSV and JSON files.\n";
        prepareSkill(tmpDir.resolve("skills"), skillName, skillContent);

        List<Map<String, Object>> messages = buildConversationWithScript();
        List<EvolutionSignal> signals = new SignalDetector(Set.of(skillName)).detect(messages);

        assertTrue(signals.size() >= 2, "Expected at least two signals");
        assertTrue(signals.stream().anyMatch(signal -> "script_artifact".equals(signal.getSignalType())));
        assertTrue(signals.stream().anyMatch(signal ->
                "execution_failure".equals(signal.getSignalType())
                        || "user_correction".equals(signal.getSignalType())));
        assertEquals(skillName, signals.stream()
                .filter(signal -> "script_artifact".equals(signal.getSignalType()))
                .findFirst()
                .orElseThrow()
                .getSkillName());

        Model llm = llmWith(List.of(mockLlmResponseWithScript()));
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "mock-model", "en");
        TeamSkillRail.FileEvolutionStore store = new TeamSkillRail.FileEvolutionStore(tmpDir.resolve("skills"));
        EvolutionContext ctx = new EvolutionContext(
                skillName,
                signals,
                skillContent,
                messages,
                List.of(),
                List.of());

        List<EvolutionRecord> records = optimizer.generateRecords(ctx);

        assertEquals(2, records.size());
        List<EvolutionRecord> scriptRecords = records.stream()
                .filter(record -> record.getChange().getTarget() == EvolutionTarget.SCRIPT)
                .toList();
        assertEquals(1, scriptRecords.size());
        assertEquals("python", scriptRecords.getFirst().getChange().getScriptLanguage());
        assertEquals("generate_bar_chart.py", scriptRecords.getFirst().getChange().getScriptFilename());

        for (EvolutionRecord record : records) {
            store.appendRecord(skillName, record);
        }

        EvolutionLog log = store.loadFullEvolutionLog(skillName);
        assertEquals(2, log.getEntries().size());
        Path scriptsDir = tmpDir.resolve("skills").resolve(skillName).resolve("evolution").resolve("scripts");
        assertTrue(Files.exists(scriptsDir.resolve("generate_bar_chart.py")));
        assertTrue(Files.readString(scriptsDir.resolve("generate_bar_chart.py")).contains("matplotlib"));
        assertTrue(Files.readString(scriptsDir.resolve("_index.md")).contains("generate_bar_chart.py"));
        assertTrue(Files.readString(tmpDir.resolve("skills").resolve(skillName).resolve("evolution").resolve("troubleshooting.md"))
                .contains("Permission Denied"));
        String skillMd = Files.readString(tmpDir.resolve("skills").resolve(skillName).resolve("SKILL.md"));
        assertTrue(skillMd.contains("<!-- evolution-index-start -->"));
        assertTrue(skillMd.contains("Evolution Experiences"));
        assertTrue(skillMd.contains("**2**"));
    }

    @Test
    void testDataFetchFalsePositiveSuppressed() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "assistant", "content", "", "tool_calls",
                        List.of(Map.of("id", "tc_s", "name", "web_search", "arguments", "{}"))),
                Map.of("role", "tool", "tool_call_id", "tc_s", "name", "web_search",
                        "content", "Search results:\n1. Python timeout errors\n2. ValueError exceptions"),
                Map.of("role", "assistant", "content", "", "tool_calls",
                        List.of(Map.of("id", "tc_r", "name", "read_file", "arguments", "{}"))),
                Map.of("role", "tool", "tool_call_id", "tc_r", "name", "read_file",
                        "content", "File content: raise ValueError('failed validation')\n")
        );

        List<EvolutionSignal> signals = new SignalDetector().detect(messages);

        assertEquals(0, signals.stream().filter(signal -> "execution_failure".equals(signal.getSignalType())).count());
    }

    @Test
    void testRetryOnMalformedLlmOutput() throws Exception {
        Model llm = llmWith(List.of(
                "This is not JSON at all { broken",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"### Recovered Fix\\n- Retry succeeded\"}]"
        ));
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "mock", "cn");
        EvolutionContext ctx = new EvolutionContext(
                "retry-skill",
                List.of(signal("execution_failure", "Error: command timeout")),
                "# Retry Skill\n",
                List.of(Map.of("role", "user", "content", "test")),
                List.of(),
                List.of());

        List<EvolutionRecord> records = optimizer.generateRecords(ctx);

        assertEquals(1, records.size());
        assertTrue(records.getFirst().getChange().getContent().contains("Recovered Fix"));
    }

    @Test
    void testMergeTargetReplacesExistingRecord(@TempDir Path tmpDir) throws Exception {
        String skillName = "merge-skill";
        prepareSkill(tmpDir.resolve("skills"), skillName, "# Merge Skill\n");
        TeamSkillRail.FileEvolutionStore store = new TeamSkillRail.FileEvolutionStore(tmpDir.resolve("skills"));
        EvolutionRecord oldRecord = record("ev_old", patch("### Initial finding\n- v1 content", null));
        EvolutionRecord mergeRecord = record("ev_new", patch("### Updated finding\n- v2 content with more detail", oldRecord.getId()));

        store.appendRecord(skillName, oldRecord);
        store.appendRecord(skillName, mergeRecord);

        EvolutionLog finalLog = store.loadEvolutionLog(skillName);
        assertEquals(1, finalLog.getEntries().size());
        assertTrue(finalLog.getEntries().getFirst().getChange().getContent().contains("v2 content"));
    }

    private static void prepareSkill(Path root, String name, String content) throws Exception {
        Path skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), content);
    }

    private static List<Map<String, Object>> buildConversationWithScript() {
        String code = "import pandas as pd\n"
                + "import matplotlib.pyplot as plt\n"
                + "df = pd.read_csv('data.csv')\n"
                + "fig, ax = plt.subplots()\n"
                + "ax.bar(df['category'], df['value'])\n"
                + "ax.set_title('Category Distribution')\n"
                + "plt.savefig('chart.png')\n";
        return List.of(
                Map.of("role", "assistant", "content", "Let me read the skill first.", "tool_calls",
                        List.of(Map.of("id", "tc_read", "name", "read_file",
                                "arguments", "{\"file_path\":\"/skills/data-processor/SKILL.md\"}"))),
                Map.of("role", "tool", "tool_call_id", "tc_read", "name", "read_file",
                        "content", "# Data Processor Skill\n\nProcess CSV and JSON files."),
                Map.of("role", "assistant", "content", "", "tool_calls",
                        List.of(Map.of("id", "tc_code", "name", "code",
                                "arguments", "{\"code\":" + quoteJson(code) + "}"))),
                Map.of("role", "tool", "tool_call_id", "tc_code", "name", "code",
                        "content", "Chart saved to chart.png"),
                Map.of("role", "assistant", "content", "", "tool_calls",
                        List.of(Map.of("id", "tc_bash", "name", "bash",
                                "arguments", "{\"command\":\"cat /etc/hosts | head\"}"))),
                Map.of("role", "tool", "tool_call_id", "tc_bash", "name", "bash",
                        "content", "Error: permission denied reading /etc/hosts"),
                Map.of("role", "user", "content", "That is wrong, should use sudo when reading system files")
        );
    }

    private static String mockLlmResponseWithScript() {
        return """
                [
                  {"action":"append","target":"body","section":"Troubleshooting",
                   "content":"### Permission Denied on System Files\\n- Use sudo when reading system files like /etc/hosts",
                   "merge_target":null},
                  {"action":"append","target":"script","section":"Scripts",
                   "content":"import pandas as pd\\nimport matplotlib.pyplot as plt\\n\\ndf = pd.read_csv('data.csv')\\nfig, ax = plt.subplots()\\nax.bar(df['category'], df['value'])\\nax.set_title('Category Distribution')\\nplt.savefig('chart.png')\\n",
                   "merge_target":null,
                   "script_filename":"generate_bar_chart.py",
                   "script_language":"python",
                   "script_purpose":"Generate bar chart from CSV data"}
                ]
                """;
    }

    private static EvolutionSignal signal(String signalType, String excerpt) {
        return EvolutionSignal.builder()
                .signalType(signalType)
                .evolutionType(com.openjiuwen.agent_evolving.signal.EvolutionCategory.SKILL_EXPERIENCE)
                .section("Troubleshooting")
                .excerpt(excerpt)
                .toolName("bash")
                .skillName("retry-skill")
                .build();
    }

    private static EvolutionRecord record(String id, EvolutionPatch patch) {
        return EvolutionRecord.builder()
                .id(id)
                .source("execution_failure")
                .timestamp(Instant.now().toString())
                .context("ctx")
                .change(patch)
                .applied(false)
                .build();
    }

    private static EvolutionPatch patch(String content, String mergeTarget) {
        return EvolutionPatch.builder()
                .section("Troubleshooting")
                .action("append")
                .content(content)
                .target(EvolutionTarget.BODY)
                .mergeTarget(mergeTarget)
                .build();
    }

    private static Model llmWith(List<String> results) throws Exception {
        Model llm = mock(Model.class);
        final int[] index = {0};
        Answer<AssistantMessage> answer = invocation -> {
            String result = results.get(Math.min(index[0], results.size() - 1));
            index[0]++;
            return new AssistantMessage(result);
        };
        doAnswer(answer).when(llm).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        return llm;
    }

    private static String quoteJson(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
