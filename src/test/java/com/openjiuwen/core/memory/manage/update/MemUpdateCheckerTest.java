
package com.openjiuwen.core.memory.manage.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.memory.prompt.PromptApplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MemUpdateCheckerTest {
    @AfterEach
    void clearPromptCache() {
        PromptApplier.getInstance().clearCache("memory_update_check");
    }

    @Test
    void checkWithoutOldMemoriesReturnsAddActions() {
        MemUpdateChecker checker = new MemUpdateChecker();

        List<MemoryActionItem> results =
            checker.check(Map.of("1", "I like reading", "2", "I enjoy books"), Map.of(), null);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(item -> item.getStatus() == MemoryStatus.ADD));
    }

    @Test
    void checkSkipsExactDuplicatesWithoutCallingModel() {
        MemUpdateChecker checker = new MemUpdateChecker();

        List<MemoryActionItem> results =
            checker.check(Map.of("new", "same memory"), Map.of("old", "same memory"), null);

        assertTrue(results.isEmpty());
    }

    @Test
    void checkWithConflictingResultAddsNewAndDeletesOld() throws Exception {
        Model model = mock(Model.class);
        doReturn(AssistantMessage.builder().content(
                "[{\"info_id\":\"1\",\"info_text\":\"I like reading\",\"result\":\"conflicting\",\"related_infos\":{\"2\":\"I hate books\"}}]")
                .build()).when(model).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        MemUpdateChecker checker = new MemUpdateChecker();
        List<MemoryActionItem> results =
            checker.check(Map.of("1", "I like reading"), Map.of("2", "I hate books"), Map.entry("test_model", model));

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(item -> item.getId().equals("1") && item.getStatus() == MemoryStatus.ADD));
        assertTrue(
                results.stream().anyMatch(item -> item.getId().equals("2") && item.getStatus() == MemoryStatus.DELETE));
    }

    @Test
    void checkWithMalformedResponseFallsBackToAdd() throws Exception {
        Model model = mock(Model.class);
        doReturn(AssistantMessage.builder().content("invalid json").build()).when(model).invoke(any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());

        MemUpdateChecker checker = new MemUpdateChecker();
        List<MemoryActionItem> results =
            checker.check(Map.of("1", "I like reading"), Map.of("2", "I enjoy books"), Map.entry("test_model", model));

        assertEquals(1, results.size());
        assertEquals("1", results.get(0).getId());
        assertEquals(MemoryStatus.ADD, results.get(0).getStatus());
    }

    @Test
    void formatInputMatchesPythonBehavior() {
        Map<String, String> newMemories = new LinkedHashMap<>();
        newMemories.put("1", "I like reading");
        newMemories.put("2", "I enjoy books");
        Map<String, String> oldMemories = new LinkedHashMap<>();
        oldMemories.put("3", "I love novels");
        oldMemories.put("4", "I hate sports");

        String[] formatted = MemUpdateChecker.formatInput(newMemories, oldMemories);

        assertArrayEquals(new String[]{"2: I enjoy books\n1: I like reading", "3: I love novels\n4: I hate sports"},
                formatted);
    }

    @Test
    void promptResourceUsesPythonOrderSemantics() {
        String template = PromptApplier.getInstance().getTemplate("memory_update_check").getContent().toString();

        assertTrue(template.contains("按正序遍历未丢弃的新信息"));
        assertTrue(template.contains("暂未遍历到且未丢弃的新信息"));
    }
}
