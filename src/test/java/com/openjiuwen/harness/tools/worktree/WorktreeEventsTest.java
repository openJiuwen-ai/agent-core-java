/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorktreeEventsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void createdEventKeepsOwnerTagAndDefaultExistedFlag() throws Exception {
        WorktreeCreatedEvent event = new WorktreeCreatedEvent();
        event.setWorktreeName("wt-happy");
        event.setWorktreePath("/tmp/ws/.worktrees/wt-happy");
        event.setOwnerId("alice");
        event.setTag("team-a");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(event), Map.class);
        assertEquals("wt-happy", payload.get("worktree_name"));
        assertEquals("/tmp/ws/.worktrees/wt-happy", payload.get("worktree_path"));
        assertEquals("alice", payload.get("owner_id"));
        assertEquals("team-a", payload.get("tag"));
        assertEquals(Boolean.FALSE, payload.get("existed"));
    }

    @Test
    void removedEventRoundTripsWithSnakeCaseFields() throws Exception {
        WorktreeRemovedEvent event = new WorktreeRemovedEvent(
                "wt-bye",
                "/tmp/ws/.worktrees/wt-bye",
                "alice",
                "team-a"
        );

        WorktreeRemovedEvent restored = MAPPER.readValue(MAPPER.writeValueAsBytes(event), WorktreeRemovedEvent.class);
        assertEquals(event, restored);
        assertEquals("alice", restored.getOwnerId());
        assertEquals("team-a", restored.getTag());
    }

    @Test
    void handlerContractAcceptsGenericEventsAsynchronously() {
        AtomicReference<WorktreeEvent> seen = new AtomicReference<>();
        WorktreeEventHandler handler = event -> {
            seen.set(event);
            return CompletableFuture.completedFuture(null);
        };

        WorktreeCreatedEvent created = new WorktreeCreatedEvent(
                "wt-happy",
                "/tmp/ws/.worktrees/wt-happy",
                "alice",
                "team-a",
                true
        );
        handler.handle(created).join();

        assertInstanceOf(WorktreeCreatedEvent.class, seen.get());
        assertTrue(((WorktreeCreatedEvent) seen.get()).isExisted());
        assertFalse(seen.get() instanceof WorktreeRemovedEvent);
    }
}
