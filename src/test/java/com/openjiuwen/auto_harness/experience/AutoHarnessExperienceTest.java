package com.openjiuwen.auto_harness.experience;

import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.tools.ExperienceSearchTool;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoHarnessExperienceTest {

    @Test
    void experienceStoreSupportsRecordGetSearchAndRecentListing() throws Exception {
        Path dir = Files.createTempDirectory("ah-exp");
        ExperienceStore store = new ExperienceStore(dir.toString());

        Experience exp = new Experience();
        exp.setType(ExperienceType.OPTIMIZATION);
        exp.setTopic("fix timeout");
        exp.setSummary("increased limit");
        String expId = store.record(exp);
        assertEquals(exp.getId(), expId);

        Experience got = store.get(expId);
        assertNotNull(got);
        assertEquals("fix timeout", got.getTopic());

        Experience dup = new Experience();
        dup.setType(ExperienceType.OPTIMIZATION);
        dup.setTopic("fix timeout");
        assertEquals("", store.record(dup));

        Experience failure = new Experience();
        failure.setType(ExperienceType.FAILURE);
        failure.setTopic("timeout bug");
        failure.setSummary("task timed out");
        store.record(failure);

        List<Experience> results = store.search("timeout", 5);
        assertFalse(results.isEmpty());
        assertEquals("fix timeout", results.get(0).getTopic());

        List<Experience> recent = store.listRecent(10);
        assertTrue(recent.size() >= 2);
    }

    @Test
    void experienceSearchToolMirrorsSearchToolIntent() throws Exception {
        Path dir = Files.createTempDirectory("ah-exp-tool");
        ExperienceStore store = new ExperienceStore(dir.toString());

        Experience exp = new Experience();
        exp.setType(ExperienceType.OPTIMIZATION);
        exp.setTopic("ruff-fix");
        exp.setSummary("fixed lint errors");
        exp.setOutcome("success");
        store.record(exp);

        ExperienceSearchTool tool = new ExperienceSearchTool(dir.toString());
        ToolOutput result = tool.invoke(Map.of("query", "ruff"));
        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertFalse(data.isEmpty());
        assertEquals("ruff-fix", data.get(0).get("topic"));

        ToolOutput emptyQuery = tool.invoke(Map.of("query", ""));
        assertFalse(emptyQuery.isSuccess());

        ToolOutput noResults = tool.invoke(Map.of("query", "nonexistent"));
        assertTrue(noResults.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> noData = (List<Map<String, Object>>) noResults.getData();
        assertTrue(noData.isEmpty());

        assertEquals(1, tool.stream(Map.of("query", "ruff")).size());
    }
}
