package com.openjiuwen.agent_teams.team_workspace;

import com.openjiuwen.agent_teams.schema.TeamEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Mirrors Python's {@code TeamWorkspaceRail} in
 * {@code openjiuwen.agent_teams.team_workspace.rails}.
 */
class WorkspaceRailsTest {

    @Test
    void afterToolCallPublishesEventWhenVersionControlDisabledAndResolvesHubPath() throws Exception {
        AtomicReference<String> eventType = new AtomicReference<>();
        AtomicReference<Object> eventPayload = new AtomicReference<>();
        WorkspaceRails rails = new WorkspaceRails(
            manager(false, eventType, eventPayload),
            "member-a"
        );

        rails.afterToolCall("write_file", ".team/team-alpha/artifacts/report.md").join();

        assertEquals(TeamEvent.WORKSPACE_ARTIFACT_UPDATED, eventType.get());
        Map<?, ?> payload = assertInstanceOf(Map.class, eventPayload.get());
        assertEquals("team-alpha", payload.get("team_name"));
        assertEquals("member-a", payload.get("member_name"));
        assertEquals("artifacts/report.md", payload.get("artifact_path"));
    }

    @Test
    void afterToolCallResolvesLegacyTeamPath() throws Exception {
        AtomicReference<Object> eventPayload = new AtomicReference<>();
        WorkspaceRails rails = new WorkspaceRails(
            manager(false, new AtomicReference<>(), eventPayload),
            "member-a"
        );

        rails.afterToolCall("edit_file", ".team/artifacts/report.md").join();

        Map<?, ?> payload = assertInstanceOf(Map.class, eventPayload.get());
        assertEquals("artifacts/report.md", payload.get("artifact_path"));
    }

    private static WorkspaceManager manager(
            boolean versionControl,
            AtomicReference<String> eventType,
            AtomicReference<Object> eventPayload) throws Exception {
        WorkspaceManager.TeamWorkspaceConfig config = new WorkspaceManager.TeamWorkspaceConfig();
        config.setVersionControl(versionControl);
        Path workspace = Files.createTempDirectory("team-workspace-rails");
        return new WorkspaceManager(
            config,
            workspace.toString(),
            "team-alpha",
            WorkspaceManager.WorkspaceMode.LOCAL,
            null,
            "leader",
            "leader",
            (type, event) -> {
                eventType.set(type);
                eventPayload.set(event);
            }
        );
    }
}
