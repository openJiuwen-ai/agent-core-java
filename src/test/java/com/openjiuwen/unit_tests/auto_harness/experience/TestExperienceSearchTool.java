/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.experience;

import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.tools.ExperienceSearchTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for experience search tool.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.experience.test_experience_search_tool}.
 */
@ExtendWith(MockitoExtension.class)
class TestExperienceSearchTool {

    @Nested
    class TestExperienceSearchToolInvoke {

        @Test
        @Tag("level0")
        void testSearchReturnsResults(@TempDir Path tempDir) throws Exception {
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            Experience ruffFix = new Experience();
            ruffFix.setType(ExperienceType.OPTIMIZATION);
            ruffFix.setTopic("ruff-fix");
            ruffFix.setSummary("fixed lint errors");
            ruffFix.setOutcome("success");
            store.record(ruffFix);

            Experience timeoutBug = new Experience();
            timeoutBug.setType(ExperienceType.FAILURE);
            timeoutBug.setTopic("timeout-bug");
            timeoutBug.setSummary("task timed out");
            timeoutBug.setOutcome("timeout");
            store.record(timeoutBug);

            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            ToolOutput result = tool.invoke(Map.of("query", "ruff"));

            assertTrue(result.isSuccess());
            List<Map<String, Object>> data = data(result);
            assertTrue(data.size() >= 1);
            assertEquals("ruff-fix", data.get(0).get("topic"));
        }

        @Test
        @Tag("level0")
        void testSearchEmptyQuery(@TempDir Path tempDir) throws Exception {
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            ToolOutput result = tool.invoke(Map.of("query", ""));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("\u7a7a"));
        }

        @Test
        @Tag("level0")
        void testSearchNoResults(@TempDir Path tempDir) throws Exception {
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            ToolOutput result = tool.invoke(Map.of("query", "nonexistent"));

            assertTrue(result.isSuccess());
            assertEquals(List.of(), data(result));
        }

        @Test
        @Tag("level0")
        void testCardHasCorrectName(@TempDir Path tempDir) {
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());

            assertEquals("experience_search", tool.getCard().getName());
            assertTrue(tool.getCard().getId().contains("ExperienceSearchTool"));
        }

        @Test
        @Tag("level1")
        void testStreamYieldsInvokeResult(@TempDir Path tempDir) throws Exception {
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            List<ToolOutput> chunks = new ArrayList<>();
            Iterator<Object> stream = tool.stream(Map.of("query", "test"));
            while (stream.hasNext()) {
                chunks.add((ToolOutput) stream.next());
            }

            assertEquals(1, chunks.size());
            assertTrue(chunks.get(0).isSuccess());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> data(ToolOutput result) {
        return (List<Map<String, Object>>) result.getData();
    }
}
