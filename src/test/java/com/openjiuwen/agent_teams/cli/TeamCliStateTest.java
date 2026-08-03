/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code StreamHandle}, {@code WatchBinding}, and {@code TeamCliState} in
 * {@code openjiuwen/agent_teams/cli/state.py}.
 */
class TeamCliStateTest {

    @Test
    void streamHandleDefaultsCancelledFalseAndKeepsFutures() {
        CompletableFuture<Map<String, Object>> runtimeReady = new CompletableFuture<>();
        CompletableFuture<Void> task = new CompletableFuture<>();

        StreamHandle handle = new StreamHandle("team-a", "session-a", runtimeReady, task);

        assertEquals("team-a", handle.getTeamName());
        assertEquals("session-a", handle.getSessionId());
        assertSame(runtimeReady, handle.getRuntimeReady());
        assertSame(task, handle.getTask());
        assertFalse(handle.isCancelled());
        handle.setCancelled(true);
        assertTrue(handle.isCancelled());
    }

    @Test
    void watchBindingKeyMirrorsPythonTupleKey() {
        WatchBinding binding = new WatchBinding("team", "session", "human");
        WatchBindingKey key = WatchBindingKey.from(binding);

        assertEquals(new WatchBindingKey("team", "session", "human"), key);
    }

    @Test
    void rememberSessionDeduplicatesAndReturnsSortedHistory() {
        TeamCliState state = new TeamCliState(new SpecRegistry(), "console");

        state.rememberSession("team-a", "session-2");
        state.rememberSession("team-a", "session-1");
        state.rememberSession("team-a", "session-2");

        assertEquals(List.of("session-1", "session-2"), state.knownSessions("team-a"));
        assertEquals(List.of(), state.knownSessions("missing"));
    }

    @Test
    void setActiveUpdatesRoutingTargetAndClearsPending() {
        TeamCliState state = new TeamCliState(new SpecRegistry(), "console");
        state.setPending("old-team", "old-session");

        state.setActive("team-a", "session-a");

        assertEquals("team-a", state.getActiveTeamName());
        assertEquals("session-a", state.getActiveSessionId());
        assertNull(state.getPendingTeamName());
        assertNull(state.getPendingSessionId());
    }

    @Test
    void setPendingDoesNotChangeActiveTarget() {
        TeamCliState state = new TeamCliState(new SpecRegistry(), "console");
        state.setActive("team-a", "session-a");

        state.setPending("team-b", "session-b");

        assertEquals("team-a", state.getActiveTeamName());
        assertEquals("session-a", state.getActiveSessionId());
        assertEquals("team-b", state.getPendingTeamName());
        assertEquals("session-b", state.getPendingSessionId());
    }

    @Test
    void mapsRemainMutableLikeDataclassDefaultFactories() {
        TeamCliState state = new TeamCliState(new SpecRegistry(), "console");
        StreamHandle handle = new StreamHandle("team-a", "session-a", new CompletableFuture<>(),
                new CompletableFuture<>());
        WatchBinding binding = new WatchBinding("team-a", "session-a", "human");

        state.getStreamHandles().put("team-a", handle);
        state.getWatchBindings().put(WatchBindingKey.from(binding), binding);

        assertSame(handle, state.getStreamHandles().get("team-a"));
        assertEquals(binding, state.getWatchBindings().get(new WatchBindingKey("team-a", "session-a", "human")));
    }
}
