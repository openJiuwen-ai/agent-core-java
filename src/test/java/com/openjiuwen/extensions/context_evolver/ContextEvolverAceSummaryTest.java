/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
import com.openjiuwen.extensions.context_evolver.schema.RetrieveResponse;
import com.openjiuwen.extensions.context_evolver.schema.SummarizeResponse;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.summary.task.ace.ApplyDeltaOp;
import com.openjiuwen.extensions.context_evolver.summary.task.ace.Playbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEvolverAceSummaryTest {

    private Map<String, Object> configSnapshot;

    @BeforeEach
    void captureState() {
        configSnapshot = Config.snapshot();
        ServiceContext.getInstance().clear();
    }

    @AfterEach
    void restoreState() {
        Config.restore(configSnapshot);
        ServiceContext.getInstance().clear();
    }

    @Test
    void aceSummarizePersistsStructuredPlaybookMemories() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ACE",
            "ACE"
        );

        SummarizeResponse summary = service.summarizeResponse(
            "user-ace",
            "none",
            "How do I get the top Spotify songs?",
            List.of(sampleTrajectory())
        ).join();

        assertEquals("success", summary.getStatus());
        assertFalse(summary.getMemory().isEmpty());

        ACEMemory first = (ACEMemory) summary.getMemory().get(0);
        assertEquals("user-ace", first.getWorkspaceId());

        RetrieveResponse retrieve = service.retrieveResponse("user-ace", "How do I get the top Spotify songs?").join();
        assertEquals("success", retrieve.getStatus());
        assertTrue(retrieve.getMemoryString().contains("spotify.search_songs"));
        assertTrue(retrieve.getMemoryString().contains("Section: apis_to_use_for_specific_information"));

        Map<String, Object> playbook = service.getPlaybook("user-ace").join();
        assertTrue(((Number) playbook.get("memory_count")).intValue() >= 2);
    }

    @Test
    void aceSummarizeReusesExistingBulletsAndTagsThemOnRepeat() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ACE",
            "ACE"
        );

        service.summarizeResponse("user-repeat", "none", "How do I get the top Spotify songs?", List.of(sampleTrajectory())).join();
        int initialCount = ((Number) service.getPlaybook("user-repeat").join().get("memory_count")).intValue();

        service.summarizeResponse("user-repeat", "none", "How do I get the top Spotify songs?", List.of(sampleTrajectory())).join();
        Map<String, Object> updatedPlaybook = service.getPlaybook("user-repeat").join();

        assertEquals(initialCount, ((Number) updatedPlaybook.get("memory_count")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memories = (List<Map<String, Object>>) updatedPlaybook.get("memories");
        Map<String, Object> actionMemory = memories.stream()
            .filter(memory -> String.valueOf(memory.get("content")).contains("spotify.search_songs"))
            .findFirst()
            .orElseThrow();
        assertEquals(1, ((Number) actionMemory.get("helpful")).intValue());
    }

    @Test
    void applyDeltaEvictsLowestScoringBulletWhenPlaybookIsFull() {
        Playbook playbook = new Playbook();
        playbook.addBullet(
            "strategies_and_hard_rules",
            "Low-value guidance",
            "strategies_and_hard_rules-00001",
            Map.of("harmful", 2)
        );
        playbook.addBullet(
            "strategies_and_hard_rules",
            "Keep this proven guidance",
            "strategies_and_hard_rules-00002",
            Map.of("helpful", 3)
        );
        playbook.setNextId(2);

        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-limit");
        context.set("playbook", playbook);
        context.set(
            "delta",
            new Playbook.DeltaBatch(
                "Add a better bullet",
                List.of(new Playbook.DeltaOperation(
                    "ADD",
                    "strategies_and_hard_rules",
                    "Use the API action trace before forming the answer.",
                    null,
                    Map.of()
                ))
            )
        );

        new ApplyDeltaOp(2).execute(context).join();

        assertNull(playbook.getBullet("strategies_and_hard_rules-00001"));
        assertNotNull(playbook.getBullet("strategies_and_hard_rules-00002"));
        assertEquals(2, playbook.bullets().size());

        List<?> memories = context.getList("memories");
        assertEquals(1, memories.size());
        ACEMemory memory = (ACEMemory) memories.get(0);
        assertEquals("Use the API action trace before forming the answer.", memory.getContent());
    }

    private static String sampleTrajectory() {
        return """
USER: Give me a comma-separated list of the top Spotify songs.
ASSISTANT: I'll gather the relevant songs from Spotify.
ACTION: spotify.search_songs(genre='R&B', sort_by='-play_count', page_limit=4)
OBSERVATION: [{"title":"Crimson Skies","play_count":995},{"title":"Silent Sea","play_count":990}]
ASSISTANT: Crimson Skies, Silent Sea
""";
    }
}
