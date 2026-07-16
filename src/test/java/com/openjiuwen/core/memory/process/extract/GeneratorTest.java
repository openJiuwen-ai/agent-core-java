
package com.openjiuwen.core.memory.process.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class GeneratorTest {
    @AfterEach
    void clearPromptCache() {
        com.openjiuwen.core.memory.prompt.PromptApplier.getInstance().clearCache();
    }

    @Test
    void genAllMemoryFiltersFragmentTypesByIndependentSwitches() throws Exception {
        Model model = mock(Model.class);
        doReturn(new AssistantMessage("""
                ```json
                {
                  \"has_key_information\": true,
                  \"variables\": [],
                  \"summary\": \"summary\"
                }
                ```
                """), new AssistantMessage("""
                ```json
                {
                  \"user_profile\": [\"profile\"],
                  \"semantic_memory\": [\"semantic\"],
                  \"episodic_memory\": [\"episodic\"]
                }
                ```
                """)).when(model).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        AgentMemoryConfig config = AgentMemoryConfig.builder().enableUserProfile(true).enableSemanticMemory(false)
                .enableEpisodicMemory(true).build();

        Map<String, List<BaseMemoryUnit>> memories =
            new Generator(new DataIdManager()).genAllMemory(Map.of("scope_id", "scope-1", "user_id", "user-1",
                    "messages", List.of(new BaseMessage("user", "I like Java")), "history_messages", List.of(),
                    "config", config, "base_chat_model", Map.entry("test-model", model), "message_mem_id", "msg-1",
                    "timestamp", "2026-05-11 00:00:00", "summary_max_token", 128, "forbidden_variables", ""));

        assertTrue(memories.containsKey(MemoryType.USER_PROFILE.getValue()));
        assertFalse(memories.containsKey(MemoryType.SEMANTIC_MEMORY.getValue()));
        assertTrue(memories.containsKey(MemoryType.EPISODIC_MEMORY.getValue()));
    }

    @Test
    void genAllMemoryAcceptsPythonFragmentItemShapes() throws Exception {
        Model model = mock(Model.class);
        doReturn(new AssistantMessage("""
                ```json
                {
                  \"has_key_information\": true,
                  \"variables\": [],
                  \"summary\": \"\"
                }
                ```
                """), new AssistantMessage("""
                ```json
                {
                  \"user_profile\": [
                    \"plain profile\",
                    {\"content\": \"content profile\"},
                    {\"other\": \"fallback profile\"}
                  ]
                }
                ```
                """)).when(model).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        Map<String, List<BaseMemoryUnit>> memories = new Generator(new DataIdManager()).genAllMemory(Map.of("scope_id",
                "scope-1", "user_id", "user-1", "messages", List.of(new BaseMessage("user", "I like Java")),
                "history_messages", List.of(), "config", AgentMemoryConfig.builder().enableSummaryMemory(false).build(),
                "base_chat_model", Map.entry("test-model", model), "message_mem_id", "msg-1", "timestamp",
                "2026-05-11 00:00:00", "summary_max_token", 128, "forbidden_variables", ""));

        List<String> contents = memories.get(MemoryType.USER_PROFILE.getValue()).stream()
                .map(unit -> ((com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit) unit).getContent())
                .toList();

        assertEquals(List.of("plain profile", "content profile", "{other=fallback profile}"), contents);
    }
}
