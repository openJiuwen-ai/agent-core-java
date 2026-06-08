/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors focused pure-format rendering coverage from Python's external format tests.
 */
class ExternalFormatTest {

    private static final long NOW_MS = 1_700_000_000_000L;
    private static final long THREE_MINUTES_MS = 3L * 60L * 1000L;
    private static final long TWELVE_MINUTES_MS = 12L * 60L * 1000L;
    private final String previousLanguage = AgentTeamI18n.getLanguage();

    @AfterEach
    void restoreLanguage() {
        AgentTeamI18n.setLanguage(previousLanguage);
    }

    @Test
    void testRenderMessageDirect() {
        AgentTeamI18n.setLanguage("en");
        String out = ExternalFormat.renderMessage(
                new TestMessage("m1", "leader", "hello", false, NOW_MS - THREE_MINUTES_MS),
                NOW_MS
        );
        assertTrue(out.contains("m1"));
        assertTrue(out.contains("leader"));
        assertTrue(out.contains("hello"));
        assertTrue(out.contains("direct message"));
        assertTrue(out.contains("3m ago"));
    }

    @Test
    void testRenderMessageBroadcast() {
        AgentTeamI18n.setLanguage("en");
        String out = ExternalFormat.renderMessage(
                new TestMessage("m2", "leader", "all hands", true, NOW_MS - THREE_MINUTES_MS),
                NOW_MS
        );
        assertTrue(out.contains("broadcast"));
    }

    @Test
    void testRenderMessagesJoinsBatch() {
        AgentTeamI18n.setLanguage("en");
        String out = ExternalFormat.renderMessages(List.of(
                new TestMessage("m1", "leader", "first", false, NOW_MS - THREE_MINUTES_MS),
                new TestMessage("m2", "dev-2", "second", false, NOW_MS - THREE_MINUTES_MS)
        ), NOW_MS);
        assertTrue(out.contains("first"));
        assertTrue(out.contains("second"));
    }

    @Test
    void testRenderTaskLineCarriesTimeAndAssignee() {
        AgentTeamI18n.setLanguage("en");
        String out = ExternalFormat.renderTaskLine(
                new TestTask("t1", "title-t1", "content-t1", "claimed", "dev-1", NOW_MS - TWELVE_MINUTES_MS),
                NOW_MS
        );
        assertTrue(out.contains("t1"));
        assertTrue(out.contains("claimed"));
        assertTrue(out.contains("→ dev-1"));
        assertTrue(out.contains("12m ago"));
    }

    @Test
    void testRenderTaskBoardFiltersTerminalAndMarksAssignment() {
        AgentTeamI18n.setLanguage("en");
        String out = ExternalFormat.renderTaskBoard(List.of(
                new TestTask("t1", "title-t1", "content-t1", "pending", null, NOW_MS - TWELVE_MINUTES_MS),
                new TestTask("t2", "title-t2", "content-t2", "completed", "dev-1", NOW_MS - TWELVE_MINUTES_MS),
                new TestTask("t3", "title-t3", "content-t3", "claimed", "dev-1", NOW_MS - TWELVE_MINUTES_MS),
                new TestTask("t4", "title-t4", "content-t4", "cancelled", null, NOW_MS - TWELVE_MINUTES_MS)
        ), false, NOW_MS);
        assertTrue(out.contains("t1"));
        assertTrue(out.contains("t3"));
        assertFalse(out.contains("t2"));
        assertFalse(out.contains("t4"));
        assertTrue(out.contains("→ dev-1"));
    }

    @Test
    void testRenderTaskBoardEmptyWhenAllTerminal() {
        AgentTeamI18n.setLanguage("en");
        String out = ExternalFormat.renderTaskBoard(List.of(
                new TestTask("t1", "title-t1", "content-t1", "completed", null, NOW_MS - TWELVE_MINUTES_MS),
                new TestTask("t2", "title-t2", "content-t2", "cancelled", null, NOW_MS - TWELVE_MINUTES_MS)
        ), false, NOW_MS);
        assertEquals("", out);
    }

    private record TestMessage(
            String messageId,
            String fromMemberName,
            String content,
            boolean broadcast,
            long timestamp
    ) implements ExternalFormat.MessageLike {
    }

    private record TestTask(
            String taskId,
            String title,
            String content,
            String status,
            String assignee,
            Long updatedAt
    ) implements ExternalFormat.TaskLike {
    }
}
