/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.BroadcastEvent;
import com.openjiuwen.agent_teams.schema.MemberCanceledEvent;
import com.openjiuwen.agent_teams.schema.MemberExecutionChangedEvent;
import com.openjiuwen.agent_teams.schema.MemberRestartedEvent;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.MemberSpawnedEvent;
import com.openjiuwen.agent_teams.schema.MemberStatusChangedEvent;
import com.openjiuwen.agent_teams.schema.MessageEvent;
import com.openjiuwen.agent_teams.schema.PlanApprovalEvent;
import com.openjiuwen.agent_teams.schema.TaskCancelledEvent;
import com.openjiuwen.agent_teams.schema.TaskClaimedEvent;
import com.openjiuwen.agent_teams.schema.TaskCompletedEvent;
import com.openjiuwen.agent_teams.schema.TaskCreatedEvent;
import com.openjiuwen.agent_teams.schema.TaskListDrainedEvent;
import com.openjiuwen.agent_teams.schema.TaskPlanRequestEvent;
import com.openjiuwen.agent_teams.schema.TaskPlanResponseEvent;
import com.openjiuwen.agent_teams.schema.TaskUnblockedEvent;
import com.openjiuwen.agent_teams.schema.TaskUpdatedEvent;
import com.openjiuwen.agent_teams.schema.TeamCleanedEvent;
import com.openjiuwen.agent_teams.schema.TeamCompletedEvent;
import com.openjiuwen.agent_teams.schema.TeamCreatedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamStandbyEvent;
import com.openjiuwen.agent_teams.schema.ToolApprovalResultEvent;
import com.openjiuwen.agent_teams.schema.WorkspaceArtifactEvent;
import com.openjiuwen.agent_teams.schema.WorkspaceConflictEvent;
import com.openjiuwen.agent_teams.schema.WorkspaceLockRequestEvent;
import com.openjiuwen.agent_teams.schema.WorkspaceLockResponseEvent;
import com.openjiuwen.agent_teams.schema.WorktreeCreatedEvent;
import com.openjiuwen.agent_teams.schema.WorktreeRemovedEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event envelope that pairs an event type with its payload.
 * <p>
 * Mirrors Python's {@code EventMessage} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, Class<? extends BaseEventMessage>> EVENT_TYPE_MAP = new LinkedHashMap<>();
    private static final Map<Class<? extends BaseEventMessage>, String> EVENT_CLASS_MAP = new LinkedHashMap<>();

    static {
        register(TeamEvent.CREATED, TeamCreatedEvent.class);
        register(TeamEvent.CLEANED, TeamCleanedEvent.class);
        register(TeamEvent.STANDBY, TeamStandbyEvent.class);
        register(TeamEvent.TEAM_COMPLETED, TeamCompletedEvent.class);
        register(TeamEvent.MEMBER_SPAWNED, MemberSpawnedEvent.class);
        register(TeamEvent.MEMBER_RESTARTED, MemberRestartedEvent.class);
        register(TeamEvent.MEMBER_STATUS_CHANGED, MemberStatusChangedEvent.class);
        register(TeamEvent.MEMBER_EXECUTION_CHANGED, MemberExecutionChangedEvent.class);
        register(TeamEvent.MEMBER_SHUTDOWN, MemberShutdownEvent.class);
        register(TeamEvent.MEMBER_CANCELED, MemberCanceledEvent.class);
        register(TeamEvent.PLAN_APPROVAL, PlanApprovalEvent.class);
        register(TeamEvent.TOOL_APPROVAL_RESULT, ToolApprovalResultEvent.class);
        register(TeamEvent.MESSAGE, MessageEvent.class);
        register(TeamEvent.BROADCAST, BroadcastEvent.class);
        register(TeamEvent.TASK_CREATED, TaskCreatedEvent.class);
        register(TeamEvent.TASK_PLAN_REQUEST, TaskPlanRequestEvent.class);
        register(TeamEvent.TASK_PLAN_RESPONSE, TaskPlanResponseEvent.class);
        register(TeamEvent.TASK_UPDATED, TaskUpdatedEvent.class);
        register(TeamEvent.TASK_CLAIMED, TaskClaimedEvent.class);
        register(TeamEvent.TASK_COMPLETED, TaskCompletedEvent.class);
        register(TeamEvent.TASK_CANCELLED, TaskCancelledEvent.class);
        register(TeamEvent.TASK_UNBLOCKED, TaskUnblockedEvent.class);
        register(TeamEvent.TASK_LIST_DRAINED, TaskListDrainedEvent.class);
        register(TeamEvent.WORKTREE_CREATED, WorktreeCreatedEvent.class);
        register(TeamEvent.WORKTREE_REMOVED, WorktreeRemovedEvent.class);
        register(TeamEvent.WORKSPACE_ARTIFACT_UPDATED, WorkspaceArtifactEvent.class);
        register(TeamEvent.WORKSPACE_CONFLICT, WorkspaceConflictEvent.class);
        register(TeamEvent.WORKSPACE_LOCK_REQUEST, WorkspaceLockRequestEvent.class);
        register(TeamEvent.WORKSPACE_LOCK_RESPONSE, WorkspaceLockResponseEvent.class);
    }

    private String eventType;
    @JsonProperty("payload")
    private Map<String, Object> payloadData = new LinkedHashMap<>();
    private String senderId = "";

    public static EventMessage fromEvent(BaseEventMessage event) {
        String eventType = EVENT_CLASS_MAP.get(event.getClass());
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown event class: " + event.getClass().getName());
        }
        Map<String, Object> payload = OBJECT_MAPPER.convertValue(event, new TypeReference<>() {
        });
        return new EventMessage(eventType, payload, "");
    }

    @JsonIgnore
    public BaseEventMessage getPayload() {
        Class<? extends BaseEventMessage> payloadClass = EVENT_TYPE_MAP.get(eventType);
        if (payloadClass == null) {
            throw new IllegalArgumentException("Unknown event_type: " + eventType);
        }
        return OBJECT_MAPPER.convertValue(payloadData, payloadClass);
    }

    public byte[] serialize() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this).getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize EventMessage", exception);
        }
    }

    public static EventMessage deserialize(byte[] data) {
        try {
            return OBJECT_MAPPER.readValue(new String(data, StandardCharsets.UTF_8), EventMessage.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize EventMessage", exception);
        }
    }

    private static void register(String eventType, Class<? extends BaseEventMessage> eventClass) {
        EVENT_TYPE_MAP.put(eventType, eventClass);
        EVENT_CLASS_MAP.put(eventClass, eventType);
    }
}
