/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.harness.tools.ToolOutput;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests readonly experience-search tool behavior.
 *
 * <p>Mirrors Python's {@code TestExperienceSearchTool} in
 * {@code tests/unit_tests/auto_harness/experience/test_experience_search_tool.py}.</p>
 *
 * <p>Also exercises Java's {@link ExperienceSearchTool}, which mirrors Python's implementation in
 * {@code openjiuwen/auto_harness/tools/experience_search_tool.py}.</p>
 */
class ExperienceSearchToolTest {

    @TempDir
    private Path tempDir;

    @Test
    void searchReturnsResults() throws Exception {
        ExperienceStore store = new ExperienceStore(tempDir);
        store.record(Experience.builder()
                .type(ExperienceType.OPTIMIZATION)
                .topic("ruff-fix")
                .summary("fixed lint errors")
                .outcome("success")
                .build()).join();
        store.record(Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic("timeout-bug")
                .summary("task timed out")
                .outcome("timeout")
                .build()).join();

        ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
        ToolOutput result = (ToolOutput) tool.invoke(Map.of("query", "ruff"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(searchRows(result)).isNotEmpty();
        assertThat(searchRows(result).get(0).get("topic")).isEqualTo("ruff-fix");
    }

    @Test
    void searchEmptyQueryReturnsFailure() throws Exception {
        ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("query", ""));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("空");
    }

    @Test
    void searchNoResultsReturnsEmptySuccess() throws Exception {
        ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("query", "nonexistent"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(searchRows(result)).isEmpty();
    }

    @Test
    void cardHasCorrectName() {
        ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());

        assertThat(tool.getCard().getName()).isEqualTo("experience_search");
        assertThat(tool.getCard().getId()).contains("ExperienceSearchTool");
    }

    @Test
    void streamYieldsInvokeResult() throws Exception {
        ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());

        Iterator<Object> stream = tool.stream(Map.of("query", "test"));

        assertThat(stream.hasNext()).isTrue();
        ToolOutput first = (ToolOutput) stream.next();
        assertThat(stream.hasNext()).isFalse();
        assertThat(first.isSuccess()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> searchRows(ToolOutput output) {
        return (List<Map<String, Object>>) output.getData();
    }
}
