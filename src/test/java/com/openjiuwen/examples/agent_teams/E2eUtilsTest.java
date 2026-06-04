/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class E2eUtilsTest {
    @Test
    void expandEnvVarsRecursivelyPreservesMissingVariables() {
        Map<String, Object> raw = Map.of(
                "literal", "${__OPENJIUWEN_E2E_UTILS_MISSING__}",
                "nested", Map.of("path", "prefix-${PATH}"),
                "items", List.of("a", "${__OPENJIUWEN_E2E_UTILS_MISSING__}")
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> expanded = (Map<String, Object>) E2eUtils.expandEnvVars(raw);

        assertThat(expanded.get("literal")).isEqualTo("${__OPENJIUWEN_E2E_UTILS_MISSING__}");
        assertThat((String) ((Map<?, ?>) expanded.get("nested")).get("path")).startsWith("prefix-");
        assertThat(((List<?>) expanded.get("items")).stream().map(String::valueOf).toList())
                .containsExactly("a", "${__OPENJIUWEN_E2E_UTILS_MISSING__}");
    }

    @Test
    void loadTeamConfigParsesYamlAndExpandsValues() throws Exception {
        Path config = Files.createTempFile("team-config", ".yaml");
        Files.writeString(config, """
                team_name: demo
                runtime:
                  session_id: ${__OPENJIUWEN_E2E_UTILS_MISSING__}
                members:
                  - name: leader
                """, StandardCharsets.UTF_8);

        Map<String, Object> loaded = E2eUtils.loadTeamConfig(config);

        assertThat(loaded.get("team_name")).isEqualTo("demo");
        assertThat(loaded).containsKey("runtime");
        assertThat((List<?>) loaded.get("members")).hasSize(1);
    }

    @Test
    void extractContentMatchesPythonFallbackOrder() {
        assertThat(E2eUtils.extractContent(Map.of("content", "body", "output", "ignored"))).isEqualTo("body");
        assertThat(E2eUtils.extractContent(Map.of("content", "", "output", "fallback"))).isEqualTo("fallback");
        assertThat(E2eUtils.extractContent("plain")).isEqualTo("plain");
        assertThat(E2eUtils.extractContent(null)).isEqualTo("None");
    }

    @Test
    void consumeStreamFlushesChunkGroupsAndSkipsDuplicateAnswerAfterLlmOutput() {
        Iterator<OutputSchema> chunks = List.of(
                new OutputSchema(E2eUtils.CHUNK_LLM_REASONING, 0, Map.of("content", "think")),
                new OutputSchema(E2eUtils.CHUNK_LLM_OUTPUT, 1, Map.of("content", "hello ")),
                new OutputSchema(E2eUtils.CHUNK_LLM_OUTPUT, 2, Map.of("content", "world")),
                new OutputSchema(E2eUtils.CHUNK_ANSWER, 3, Map.of("content", "duplicate")),
                new OutputSchema(E2eUtils.CHUNK_TOOL_CALL, 4, Map.of("tool_name", "search", "tool_args", "q")),
                new OutputSchema(E2eUtils.CHUNK_TOOL_RESULT, 5, Map.of("tool_result", "ok")),
                new OutputSchema(E2eUtils.CHUNK_MESSAGE, 6, Map.of("content", "done")),
                new OutputSchema(E2eUtils.CHUNK_INTERACTION, 7, "need input")
        ).iterator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        E2eUtils.consumeStream(chunks, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[Reasoning] think");
        assertThat(output).contains("[Output] " + E2eUtils.COLOR_RESET + "hello world");
        assertThat(output).doesNotContain("duplicate");
        assertThat(output).contains("[Tool] search");
        assertThat(output).contains("[Result] ok");
        assertThat(output).contains("[Message] done");
        assertThat(output).contains("[Interaction] need input");
    }
}
